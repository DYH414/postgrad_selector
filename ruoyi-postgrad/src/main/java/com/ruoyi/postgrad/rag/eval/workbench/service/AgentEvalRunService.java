package com.ruoyi.postgrad.rag.eval.workbench.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.client.EvaluationWorkerClient;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentEvalCaseResult;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

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

    private static final Set<String> TERMINAL_STATUSES = Set.of("SUCCEEDED", "FAILED", "CANCELLED");

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
    public String saveAgentCaseResult(String runId, Long caseId, AgentEvalCaseResult result) {
        RagEvalRun run = mapper.selectRunById(runId);
        if (run == null) throw new ServiceException("EVAL_RUN_NOT_FOUND");
        if ("CANCELLED".equals(run.getStatus())) throw new ServiceException("EVAL_RUN_CANCELLED");
        if (TERMINAL_STATUSES.contains(run.getStatus())) throw new ServiceException("EVAL_RUN_TERMINAL");

        AgentEvalCaseResult existing = mapper.selectAgentEvalCaseResultByKey(runId, caseId);
        if (existing != null) {
            if (Objects.equals(existing.getResultChecksum(), result.getResultChecksum())) {
                return "REPLAYED";
            }
            throw new ServiceException("EVAL_RESULT_CONFLICT");
        }

        result.setRunId(runId);
        result.setCaseId(caseId);
        mapper.insertAgentEvalCaseResult(result);
        // 重算 completed_cases（COUNT(*) from agent_eval_case_result）
        mapper.updateAgentEvalProgress(runId);
        // CAS：全部 case 完成且非终态时转 SUCCEEDED
        mapper.completeAgentEvalRunIfDone(runId);
        return "SAVED";
    }

    /** 查询某 run 的所有 case 结果（供管理端展示 / Python Worker 查漏）。 */
    public List<AgentEvalCaseResult> listCaseResults(String runId) {
        RagEvalRun run = mapper.selectRunById(runId);
        if (run == null) throw new ServiceException("EVAL_RUN_NOT_FOUND");
        return mapper.selectAgentEvalCaseResultsByRunId(runId);
    }

    /**
     * 创建 AGENT_E2E 评测 Run（§4 端点1 的 AGENT_E2E 分支）。
     * 校验 dataset 存在 + agentConfigVersion 为 ACTIVE，插入 run 记录，分发给 Python Worker，返回 runId。
     */
    public String createRun(Long datasetId, Long agentConfigVersionId, String operator) {
        RagEvalDataset dataset = mapper.selectDatasetById(datasetId);
        if (dataset == null) throw new ServiceException("评测数据集不存在");

        AgentConfigVersion version = mapper.selectAgentConfigVersionById(agentConfigVersionId);
        if (version == null) throw new ServiceException("Agent 配置版本不存在");
        if (!"ACTIVE".equals(version.getStatus())) throw new ServiceException("AGENT_CONFIG_NOT_ACTIVE");

        List<RagEvalCaseEntity> cases = mapper.selectCasesByDatasetId(datasetId);

        RagEvalRun run = new RagEvalRun();
        run.setRunId(UUID.randomUUID().toString());
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

        // 分发给 Python Worker
        Map<String, Object> configSnapshot = new LinkedHashMap<>();
        configSnapshot.put("id", version.getId());
        configSnapshot.put("versionName", version.getVersionName());
        configSnapshot.put("businessPromptText", version.getBusinessPromptText());
        configSnapshot.put("routingPromptText", version.getRoutingPromptText());
        configSnapshot.put("model", version.getModel());
        configSnapshot.put("temperature", version.getTemperature());

        List<Map<String, Object>> casesPayload = cases.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("case_id", c.getId());
            m.put("query", c.getQueryText());
            m.put("agent_spec", c.getAgentSpecJson() == null ? null : JSON.parse(c.getAgentSpecJson()));
            return m;
        }).collect(Collectors.toList());

        workerClient.dispatchRun(run.getRunId(), dataset.getChecksumSha256(), configSnapshot, casesPayload);
        return run.getRunId();
    }
}
