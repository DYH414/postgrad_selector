package com.ruoyi.postgrad.rag.eval.workbench.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalCaseEntity;
import com.ruoyi.postgrad.rag.eval.workbench.domain.RagEvalDataset;
import java.math.BigDecimal;
import java.util.List;

/**
 * Java → Python Worker 的稳定 HTTP 契约。
 *
 * <p>字段名与 Python Pydantic 模型保持一致，禁止用 Map 在调用点临时拼装。
 */
public record EvaluationDispatchRequest(
    @JsonProperty("run_id") String runId,
    @JsonProperty("dataset_checksum") String datasetChecksum,
    @JsonProperty("config_versions") List<ConfigVersion> configVersions,
    List<DispatchCase> cases
) {
    public static EvaluationDispatchRequest from(
        String runId,
        RagEvalDataset dataset,
        AgentConfigVersion version,
        List<RagEvalCaseEntity> cases
    ) {
        return new EvaluationDispatchRequest(
            runId,
            dataset.getChecksumSha256(),
            List.of(ConfigVersion.from(version)),
            cases.stream().map(DispatchCase::from).toList()
        );
    }

    public record ConfigVersion(
        Long id,
        @JsonProperty("version_name") String versionName,
        @JsonProperty("business_prompt_text") String businessPromptText,
        @JsonProperty("routing_prompt_text") String routingPromptText,
        String model,
        BigDecimal temperature,
        @JsonProperty("tool_catalog_checksum") String toolCatalogChecksum
    ) {
        static ConfigVersion from(AgentConfigVersion version) {
            return new ConfigVersion(
                version.getId(),
                version.getVersionName(),
                version.getBusinessPromptText(),
                version.getRoutingPromptText(),
                version.getModel(),
                version.getTemperature(),
                version.getToolCatalogChecksum()
            );
        }
    }

    public record DispatchCase(
        Long id,
        @JsonProperty("case_key") String caseKey,
        String query,
        @JsonProperty("expected_route") String expectedRoute,
        String category,
        String difficulty,
        AgentCaseSpec agent
    ) {
        static DispatchCase from(RagEvalCaseEntity evalCase) {
            return new DispatchCase(
                evalCase.getId(),
                evalCase.getCaseKey(),
                evalCase.getQueryText(),
                evalCase.getExpectedRoute(),
                evalCase.getCategory(),
                evalCase.getDifficulty(),
                AgentCaseSpec.fromJson(evalCase.getAgentSpecJson())
            );
        }
    }

    public record AgentCaseSpec(
        @JsonProperty("required_tools") List<String> requiredTools,
        @JsonProperty("forbidden_tools") List<String> forbiddenTools,
        @JsonProperty("expected_abstention") boolean expectedAbstention,
        @JsonProperty("expected_claims") List<String> expectedClaims,
        @JsonProperty("forbidden_claims") List<String> forbiddenClaims,
        @JsonProperty("reference_evidence") List<ReferenceEvidence> referenceEvidence
    ) {
        static AgentCaseSpec fromJson(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            JSONObject value = JSON.parseObject(json);
            JSONArray evidence = value.getJSONArray("referenceEvidence");
            List<ReferenceEvidence> references = evidence == null
                ? List.of()
                : evidence.stream()
                    .map(JSONObject.class::cast)
                    .map(item -> new ReferenceEvidence(
                        item.getString("evidenceKey"),
                        stringList(item.getJSONArray("citationTokens"))
                    ))
                    .toList();
            return new AgentCaseSpec(
                stringList(value.getJSONArray("requiredTools")),
                stringList(value.getJSONArray("forbiddenTools")),
                value.getBooleanValue("expectedAbstention"),
                stringList(value.getJSONArray("expectedClaims")),
                stringList(value.getJSONArray("forbiddenClaims")),
                references
            );
        }
    }

    public record ReferenceEvidence(
        @JsonProperty("evidence_key") String evidenceKey,
        @JsonProperty("citation_tokens") List<String> citationTokens
    ) {}

    private static List<String> stringList(JSONArray value) {
        return value == null ? List.of() : value.toList(String.class);
    }
}
