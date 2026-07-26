package com.ruoyi.postgrad.rag.eval.workbench.mapper;

import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentEvalCaseResult;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseResultEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalRun;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RagEvaluationWorkbenchMapper {
    int insertDataset(RagEvalDataset dataset);
    RagEvalDataset selectDatasetById(Long id);
    RagEvalDataset selectDatasetByKeyVersion(@Param("datasetKey") String datasetKey,
                                              @Param("version") String version);
    List<RagEvalDataset> selectDatasets(@Param("datasetType") String datasetType,
                                        @Param("lifecycleState") String lifecycleState);
    int updateDataset(RagEvalDataset dataset);
    int updateDatasetCount(@Param("id") Long id, @Param("caseCount") int caseCount);
    int freezeDataset(@Param("id") Long id, @Param("checksum") String checksum,
                      @Param("caseCount") int caseCount, @Param("operator") String operator,
                      @Param("lockVersion") int lockVersion);

    int insertCase(RagEvalCaseEntity evalCase);
    RagEvalCaseEntity selectCaseById(Long id);
    List<RagEvalCaseEntity> selectCasesByDatasetId(Long datasetId);
    int updateCase(RagEvalCaseEntity evalCase);
    int deleteCase(Long id);

    int insertRun(RagEvalRun run);
    RagEvalRun selectRunById(String runId);
    List<RagEvalRun> selectRuns(@Param("datasetId") Long datasetId, @Param("status") String status,
                                @Param("runIdKeyword") String runIdKeyword,
                                @Param("datasetChecksum") String datasetChecksum);
    RagEvalRun selectActiveEquivalentRun(@Param("datasetId") Long datasetId,
                                         @Param("datasetChecksum") String datasetChecksum,
                                         @Param("parameterSnapshotJson") String parameterSnapshotJson,
                                         @Param("modesJson") String modesJson);
    int markRunStarted(String runId);
    int updateRunProgress(@Param("runId") String runId, @Param("completedCases") int completedCases);
    int completeRun(@Param("runId") String runId, @Param("status") String status,
                    @Param("metricsJson") String metricsJson, @Param("errorCode") String errorCode,
                    @Param("errorSummary") String errorSummary);
    int requestCancel(String runId);
    int markRunningInterrupted();

    int insertCaseResult(RagEvalCaseResultEntity result);
    RagEvalCaseResultEntity selectCaseResultById(Long id);
    List<RagEvalCaseResultEntity> selectCaseResultsByRunId(String runId);
    int reviewCaseResult(@Param("id") Long id, @Param("category") String category,
                         @Param("note") String note, @Param("reviewedBy") String reviewedBy);

    // ── AGENT_E2E: AgentConfigVersion（不可变配置版本）──
    int insertAgentConfigVersion(AgentConfigVersion version);
    AgentConfigVersion selectAgentConfigVersionById(Long id);
    List<AgentConfigVersion> selectAgentConfigVersions(@Param("status") String status);
    int archiveAgentConfigVersion(@Param("id") Long id);

    // ── AGENT_E2E: agent_eval_case_result（Python 回调落库）──
    int insertAgentEvalCaseResult(AgentEvalCaseResult result);
    AgentEvalCaseResult selectAgentEvalCaseResultByKey(@Param("runId") String runId, @Param("caseId") Long caseId);
    List<AgentEvalCaseResult> selectAgentEvalCaseResultsByRunId(String runId);
    /** 按 agent_eval_case_result COUNT(*) 重算 completed_cases；只更新 RUNNING 状态的 run。 */
    int updateAgentEvalProgress(@Param("runId") String runId);
    /** CAS：completed_cases >= total_cases 且非终态时转 SUCCEEDED；返回 1 表示已转态。 */
    int completeAgentEvalRunIfDone(@Param("runId") String runId);
}
