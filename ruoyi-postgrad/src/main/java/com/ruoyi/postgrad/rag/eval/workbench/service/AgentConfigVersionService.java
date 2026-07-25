package com.ruoyi.postgrad.rag.eval.workbench.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.postgrad.rag.eval.workbench.domain.AgentConfigVersion;
import com.ruoyi.postgrad.rag.eval.workbench.mapper.RagEvaluationWorkbenchMapper;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolCatalog;
import com.ruoyi.postgrad.recommend.tool.runtime.AgentToolDefinition;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 不可变评测 Agent 配置版本。创建后文本不可改；改 Prompt 须建新版本。
 * ACTIVE 版本的 model/temperature 必须与运行时 AiConfig 全局 Bean 一致
 * （全局单例模型架构下不假装动态切模型）。
 */
@Service
public class AgentConfigVersionService {

    private final RagEvaluationWorkbenchMapper mapper;
    private final String runtimeModel;
    private final BigDecimal runtimeTemperature;
    private final String ragEmbeddingModel;
    private final String rerankModel;

    public AgentConfigVersionService(
        RagEvaluationWorkbenchMapper mapper,
        @Value("${app.ai.deepseek.model-name:unknown}") String runtimeModel,
        @Value("${app.ai.deepseek.temperature:0.7}") double runtimeTemperature,
        @Value("${app.ai.rag.embedding.model-name:BAAI/bge-m3}") String ragEmbeddingModel,
        @Value("${app.ai.rag.rerank.model-name:BAAI/bge-reranker-v2-m3}") String rerankModel) {
        this.mapper = mapper;
        this.runtimeModel = runtimeModel;
        // 与 DECIMAL(4,3) 对齐，避免 0.7 vs 0.700 的比较误判
        this.runtimeTemperature = BigDecimal.valueOf(runtimeTemperature).setScale(3, RoundingMode.HALF_UP);
        this.ragEmbeddingModel = ragEmbeddingModel;
        this.rerankModel = rerankModel;
    }

    public List<AgentConfigVersion> list(String status) {
        return mapper.selectAgentConfigVersions(status);
    }

    public AgentConfigVersion get(Long id) {
        AgentConfigVersion v = mapper.selectAgentConfigVersionById(id);
        if (v == null) throw new ServiceException("Agent config version not found");
        return v;
    }

    public AgentConfigVersion create(AgentConfigVersion req, String operator) {
        if (blank(req.getVersionName())) throw new ServiceException("versionName is required");
        if (blank(req.getBusinessPromptText()) || blank(req.getRoutingPromptText())) {
            throw new ServiceException("businessPromptText and routingPromptText are required");
        }
        String status = req.getStatus() == null ? "ACTIVE" : req.getStatus().trim().toUpperCase();
        if (!List.of("ACTIVE", "ARCHIVED").contains(status)) {
            throw new ServiceException("status must be ACTIVE or ARCHIVED");
        }

        // ACTIVE 版本必须匹配运行时真值（AGENT_CONFIG_RUNTIME_MISMATCH）
        if ("ACTIVE".equals(status)) {
            if (!runtimeModel.equals(req.getModel())) {
                throw new ServiceException("AGENT_CONFIG_RUNTIME_MISMATCH: model '" + req.getModel()
                    + "' != runtime '" + runtimeModel + "'");
            }
            BigDecimal reqTemp = req.getTemperature() == null ? null
                : req.getTemperature().setScale(3, RoundingMode.HALF_UP);
            if (!runtimeTemperature.equals(reqTemp)) {
                throw new ServiceException("AGENT_CONFIG_RUNTIME_MISMATCH: temperature '" + reqTemp
                    + "' != runtime '" + runtimeTemperature + "'");
            }
        }

        // 服务端计算 checksum，请求里的一律忽略
        req.setBusinessPromptChecksum(sha256(req.getBusinessPromptText()));
        req.setRoutingPromptChecksum(sha256(req.getRoutingPromptText()));
        req.setToolCatalogChecksum(toolCatalogChecksum());
        req.setRagConfigSnapshotJson(ragConfigSnapshot());
        req.setStatus(status);
        req.setGitCommit(gitCommit());
        req.setCreatedBy(operator);
        mapper.insertAgentConfigVersion(req);
        return get(req.getId());
    }

    /** 稳定序列化 catalog（按工具名排序），计算 checksum。 */
    private String toolCatalogChecksum() {
        StringBuilder sb = new StringBuilder();
        new AgentToolCatalog().definitions().stream()
            .sorted(Comparator.comparing(AgentToolDefinition::name))
            .forEach(d -> sb.append(d.name()).append('|').append(d.category()).append('|')
                .append(d.budgetGroup()).append('|').append(d.estimatedResultTokens()).append('|')
                .append(d.confirmationRequired()).append('|').append(d.concurrencySafe()).append('\n'));
        return sha256(sb.toString());
    }

    private String ragConfigSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("collection", "postgrad_rag_active");
        snapshot.put("embeddingModel", ragEmbeddingModel);
        snapshot.put("rerankModel", rerankModel);
        return JSON.toJSONString(snapshot);
    }

    private String gitCommit() {
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "HEAD").start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return out.isBlank() ? "unknown" : out;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean blank(String v) { return v == null || v.isBlank(); }

    private String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new ServiceException("SHA-256 unavailable");
        }
    }
}
