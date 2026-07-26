package com.ruoyi.postgrad.rag.eval.workbench.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Python评测Worker客户端 — Java创建AGENT_E2E run后分发给Python Worker执行。
 */
@Component
public class EvaluationWorkerClient {

    private static final Logger log = LoggerFactory.getLogger(EvaluationWorkerClient.class);

    private final String workerBaseUrl;
    private final String internalToken;
    private final RestTemplate restTemplate;

    public EvaluationWorkerClient(
        @Value("${app.ai.evaluation.worker-base-url}") String workerBaseUrl,
        @Value("${app.ai.evaluation.internal-token}") String internalToken,
        RestTemplate restTemplate) {
        this.workerBaseUrl = workerBaseUrl;
        this.internalToken = internalToken;
        this.restTemplate = restTemplate;
    }

    /**
     * 分发Run给Python Worker — POST /runs，幂等（Worker侧根据run_id去重）。
     * @param runId 评测Run ID
     * @param datasetChecksum 数据集checksum（用于Worker侧校验）
     * @param configVersions Agent配置版本快照
     * @param cases case列表（含query/agent spec）
     */
    public void dispatchRun(String runId, String datasetChecksum, Map<String, Object> configVersions, List<Map<String, Object>> cases) {
        if (workerBaseUrl == null || workerBaseUrl.isBlank()) {
            log.warn("[EvalWorker] worker-base-url未配置，跳过分发 runId={}", runId);
            return;
        }
        if (internalToken == null || internalToken.isBlank()) {
            log.warn("[EvalWorker] internal-token未配置，跳过分发 runId={}", runId);
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("run_id", runId);
        payload.put("dataset_checksum", datasetChecksum);
        payload.put("config_versions", configVersions);
        payload.put("cases", cases);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Evaluation-Token", internalToken);

        try {
            restTemplate.postForObject(workerBaseUrl + "/runs", new HttpEntity<>(payload, headers), Void.class);
            log.info("[EvalWorker] 分发成功 runId={} cases={}", runId, cases.size());
        } catch (Exception e) {
            log.error("[EvalWorker] 分发失败 runId={}: {}", runId, e.getMessage(), e);
            throw new RuntimeException("Python Worker分发失败: " + e.getMessage(), e);
        }
    }
}
