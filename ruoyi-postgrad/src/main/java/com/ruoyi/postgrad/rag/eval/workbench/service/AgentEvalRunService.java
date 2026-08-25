package com.ruoyi.postgrad.rag.eval.workbench.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationDispatchRequest;
import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationWorkerClient;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentEvalCaseResult;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AGENT_E2E 评测 Run 的结果回调处理。
 *
 * <p>职责边界：接收 Python Worker 的 case 结果回调（§4 端点4）、幂等性保护、
 * 进度重算、CAS 状态转换。不含编排逻辑（编排在 Python 侧）。
 *
 * <p>三条隐患防线（对应 CLAUDE.md § 技术决策）：
 * <ol>
 *   <li>同 resultChecksum → 200 REPLAYED（幂等重放），绝不重复写库</li>
 *   <li>不同 resultChecksum → 409 冲突，不覆盖已有结果，要求 Python 侧排查</li>
 *   <li>completeAgentEvalRunIfDone 使用 CAS SQL（status NOT IN terminal），
 *       并发回调时只有一个线程能完成状态转换</li>
 * </ol>
 */
@Service
public class AgentEvalRunService {

    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "FAILED", "CANCELLED", "INTERRUPTED");

    private final RagEvaluationWorkbenchMapper mapper;
    private final EvaluationWorkerClient workerClient;

    public AgentEvalRunService(RagEvaluationWorkbenchMapper mapper, EvaluationWorkerClient workerClient) {
        this.mapper = mapper;
        this.workerClient = workerClient;
    }

    /**
     * 保存单个 case 的评测结果（Python Worker 回调，端点4）。
     *
     * @return "SAVED" 首次写入；"REPLAYED" 幂等重放（相同 checksum）
     * @throws ServiceException code="EVAL_RUN_NOT_FOUND"   run 不存在
     * @throws ServiceException code="EVAL_RUN_CANCELLED"   run 已取消，拒收结果
     * @throws ServiceException code="EVAL_RUN_TERMINAL"    run 已终止（非 CANCELLED）
     * @throws ServiceException code="EVAL_RESULT_CONFLICT" 同 (runId,caseId) 但 checksum 不同，409 语义
     */
    @Transactional
    public String saveAgentCaseResult(String runId, Long caseId, AgentEvalCaseResult result) {
        // Serialize callbacks for the same run before any result read/write. Without this
        // row lock, two final callbacks can each miss the other's uncommitted insert and
        // both commit while leaving the run permanently RUNNING.
        RagEvalRun run = mapper.selectRunByIdForUpdate(runId);
        if (run == null) throw new ServiceException("EVAL_RUN_NOT_FOUND");
        if ("CANCELLED".equals(run.getStatus())) throw new ServiceException("EVAL_RUN_CANCELLED");

        // The Worker performs one final reconciliation pass. A Run may already be
        // SUCCEEDED by then, but an identical persisted result is still a valid
        // idempotent replay rather than a terminal-state error.
        AgentEvalCaseResult existing = mapper.selectAgentEvalCaseResultByKey(runId, caseId);
        if (existing != null && Objects.equals(existing.getResultChecksum(), result.getResultChecksum())) {
            return "REPLAYED";
        }
        if (TERMINAL_STATUSES.contains(run.getStatus())) throw new ServiceException("EVAL_RUN_TERMINAL");

        RagEvalCaseEntity evalCase = mapper.selectCaseById(caseId);
        if (evalCase == null || !Objects.equals(run.getDatasetId(), evalCase.getDatasetId())) {
            throw new ServiceException("EVAL_CASE_NOT_IN_RUN");
        }

        if (existing != null) {
            throw new ServiceException("EVAL_RESULT_CONFLICT");
        }

        result.setRunId(runId);
        result.setCaseId(caseId);
        if (mapper.insertAgentEvalCaseResult(result) == 0) {
            existing = mapper.selectAgentEvalCaseResultByKey(runId, caseId);
            if (existing != null && Objects.equals(existing.getResultChecksum(), result.getResultChecksum())) {
                return "REPLAYED";
            }
            throw new ServiceException("EVAL_RESULT_CONFLICT");
        }
        // 重算 completed_cases（COUNT(*) from agent_eval_case_result）
        mapper.updateAgentEvalProgress(runId);
        List<AgentEvalCaseResult> persisted = mapper.selectAgentEvalCaseResultsByRunId(runId);
        if (persisted.size() >= run.getTotalCases()) {
            mapper.completeRun(runId, "SUCCEEDED", JSON.toJSONString(aggregateMetrics(persisted)), null, null);
        }
        return "SAVED";
    }

    /** 查询某 run 的所有 case 结果（供管理端展示 / Python Worker 查漏）。 */
    public List<AgentEvalCaseResult> listCaseResults(String runId) {
        RagEvalRun run = mapper.selectRunById(runId);
        if (run == null) throw new ServiceException("EVAL_RUN_NOT_FOUND");
        return mapper.selectAgentEvalCaseResultsByRunId(runId);
    }

    /** Worker-level failures must always terminate a RUNNING run. Idempotent for terminal runs. */
    public void failRun(String runId, String errorCode, String errorSummary) {
        RagEvalRun run = mapper.selectRunById(runId);
        if (run == null) throw new ServiceException("EVAL_RUN_NOT_FOUND");
        if (TERMINAL_STATUSES.contains(run.getStatus()) || "INTERRUPTED".equals(run.getStatus())) return;
        String code = blank(errorCode) ? "EVAL_WORKER_EXECUTION_FAILED" : errorCode.trim();
        String summary = blank(errorSummary) ? code : errorSummary.trim();
        if (summary.length() > 500) summary = summary.substring(0, 500);
        mapper.completeRun(runId, "FAILED", null, code, summary);
    }

    private Map<String, Object> aggregateMetrics(List<AgentEvalCaseResult> results) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        int total = results.size();
        long passed = results.stream().filter(r -> Boolean.TRUE.equals(r.getPassed())).count();
        metrics.put("total", total);
        metrics.put("passed", passed);
        metrics.put("passRate", total == 0 ? 0D : (double) passed / total);
        metrics.put("routeAccuracy", booleanRate(results.stream().map(AgentEvalCaseResult::getRouteAccuracy).toList()));
        metrics.put("requiredToolHitRate", booleanRate(results.stream().map(AgentEvalCaseResult::getRequiredToolHit).toList()));
        metrics.put("judgePassRate", booleanRate(results.stream().map(AgentEvalCaseResult::getJudgePassed).toList()));
        metrics.put("avgFaithfulness", decimalAverage(results.stream().map(AgentEvalCaseResult::getFaithfulness).toList()));
        metrics.put("avgAnswerRelevance", decimalAverage(results.stream().map(AgentEvalCaseResult::getAnswerRelevance).toList()));
        metrics.put("avgLatencyMs", results.stream().filter(r -> r.getLatencyMs() != null)
            .mapToLong(AgentEvalCaseResult::getLatencyMs).average().orElse(0D));
        metrics.put("inputTokens", results.stream().map(AgentEvalCaseResult::getInputTokens)
            .filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        metrics.put("outputTokens", results.stream().map(AgentEvalCaseResult::getOutputTokens)
            .filter(Objects::nonNull).mapToInt(Integer::intValue).sum());
        return metrics;
    }

    private Double booleanRate(List<Boolean> values) {
        List<Boolean> present = values.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        return (double) present.stream().filter(Boolean.TRUE::equals).count() / present.size();
    }

    private Double decimalAverage(List<java.math.BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).mapToDouble(java.math.BigDecimal::doubleValue)
            .average().stream().boxed().findFirst().orElse(null);
    }

    /**
     * 创建 AGENT_E2E 评测 Run（§4 端点1 的 AGENT_E2E 分支）。
     * 校验 dataset 存在 + agentConfigVersion 为 ACTIVE，插入 run 记录，分发给 Python Worker，返回 runId。
     */
    public String createRun(Long datasetId, Long agentConfigVersionId, String operator) {
        RagEvalDataset dataset = mapper.selectDatasetById(datasetId);
        if (dataset == null) throw new ServiceException("评测数据集不存在");
        if (!"AGENT_E2E".equals(dataset.getEvaluationType())) {
            throw new ServiceException("AGENT_EVAL_DATASET_TYPE_REQUIRED");
        }
        if (!"FROZEN".equals(dataset.getLifecycleState())) {
            throw new ServiceException("AGENT_EVAL_DATASET_NOT_FROZEN");
        }

        AgentConfigVersion version = mapper.selectAgentConfigVersionById(agentConfigVersionId);
        if (version == null) throw new ServiceException("Agent 配置版本不存在");
        if (!"ACTIVE".equals(version.getStatus())) throw new ServiceException("AGENT_CONFIG_NOT_ACTIVE");

        List<RagEvalCaseEntity> cases = mapper.selectCasesByDatasetId(datasetId);
        validateDispatchInputs(dataset, version, cases);
        String runId = UUID.randomUUID().toString();
        EvaluationDispatchRequest dispatchRequest =
            EvaluationDispatchRequest.from(runId, dataset, version, cases);

        RagEvalRun run = new RagEvalRun();
        run.setRunId(runId);
        run.setDatasetId(datasetId);
        run.setDatasetChecksum(dataset.getChecksumSha256());
        run.setStatus("PENDING");
        run.setEvaluationType("AGENT_E2E");
        run.setAgentConfigVersionId(agentConfigVersionId);
        run.setAgentConfigSnapshotJson(JSON.toJSONString(version));
        run.setModesJson("[]");
        run.setParameterSnapshotJson("{}");
        run.setTotalCases(cases.size());
        run.setCompletedCases(0);
        run.setCreatedBy(operator);
        mapper.insertRun(run);

        if (mapper.markRunStarted(run.getRunId()) != 1) {
            mapper.completeRun(run.getRunId(), "FAILED", null,
                "EVAL_RUN_START_FAILED", "Unable to transition Agent evaluation run to RUNNING");
            throw new ServiceException("EVAL_RUN_START_FAILED");
        }
        try {
            workerClient.dispatchRun(dispatchRequest);
        } catch (RuntimeException ex) {
            mapper.completeRun(run.getRunId(), "FAILED", null,
                "EVAL_WORKER_DISPATCH_FAILED", safeMessage(ex));
            throw ex;
        }
        return run.getRunId();
    }

    private void validateDispatchInputs(RagEvalDataset dataset, AgentConfigVersion version,
                                        List<RagEvalCaseEntity> cases) {
        if (cases == null || cases.isEmpty()) throw new ServiceException("AGENT_EVAL_DATASET_EMPTY");
        if (blank(dataset.getChecksumSha256())) throw new ServiceException("AGENT_EVAL_DATASET_CHECKSUM_REQUIRED");
        if (version.getId() == null || blank(version.getVersionName())
            || blank(version.getBusinessPromptText()) || blank(version.getModel())
            || version.getTemperature() == null || blank(version.getToolCatalogChecksum())) {
            throw new ServiceException("AGENT_CONFIG_INCOMPLETE");
        }
        boolean invalidCase = cases.stream().anyMatch(c -> c.getId() == null
            || blank(c.getCaseKey()) || blank(c.getQueryText()) || blank(c.getAgentSpecJson()));
        if (invalidCase) throw new ServiceException("AGENT_EVAL_CASE_SPEC_REQUIRED");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
