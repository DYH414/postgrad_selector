package com.ruoyi.postgrad.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.dto.CandidateProgramDTO;
import com.ruoyi.postgrad.tool.AiRecommendationTools;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 后台 AI 预选 —— 从候选池分层抽样，批量调用 LLM 判断，硬校验后返回 include 列表。
 * <p>
 * 纯计算服务，不操作 Redis/DB。调用方负责从 Redis 读池、写书签、持久化。
 */
@Service
public class PreselectService {

    private static final Logger log = LoggerFactory.getLogger(PreselectService.class);

    @org.springframework.beans.factory.annotation.Value("classpath:prompts/preselect.txt")
    private org.springframework.core.io.Resource preselectPromptResource;

    private String prompt() {
        try {
            return new String(preselectPromptResource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load preselect prompt", e);
        }
    }

    /** 从候选池分层抽样 + AI 批量判断，返回应加入书签的 DTO 列表（仅 include 的） */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<CandidateProgramDTO> preselect(List<CandidateProgramDTO> pool, ChatModel chatModel) {
        if (pool == null || pool.size() < 10) {
            log.warn("[Preselect] Pool too small, size={}", pool == null ? 0 : pool.size());
            return Collections.emptyList();
        }
        log.info("[Preselect] Loaded pool size={}", pool.size());

        // 1. 分层抽样
        List<CandidateProgramDTO> reach = new ArrayList<>(), steady = new ArrayList<>(), safe = new ArrayList<>();
        for (CandidateProgramDTO p : pool) {
            if (p.getGap() < 0) reach.add(p);
            else if (p.getGap() > 14 && p.isCanBeSafe()) safe.add(p);
            else steady.add(p);
        }
        List<CandidateProgramDTO> samples = new ArrayList<>();
        Collections.shuffle(reach); Collections.shuffle(steady); Collections.shuffle(safe);
        samples.addAll(reach.stream().limit(8).toList());
        samples.addAll(steady.stream().limit(8).toList());
        samples.addAll(safe.stream().limit(8).toList());
        if (samples.isEmpty()) return Collections.emptyList();

        // 2. 构建事实卡
        List<String> factCards = samples.stream().map(this::buildFactCard).toList();

        // 3. 分批调用 AI
        List<Map<String, Object>> decisions = new ArrayList<>();
        int batchSize = 12;
        for (int i = 0; i < factCards.size(); i += batchSize) {
            int end = Math.min(i + batchSize, factCards.size());
            String batchStr = String.join("\n", factCards.subList(i, end));
            String userMsg = "候选学校：\n" + batchStr + "\n\n请对上述学校逐一判断，输出 JSON 数组。";

            try {
                String aiResp = chatModel.chat(
                    SystemMessage.from(prompt()),
                    UserMessage.from(userMsg)
                ).aiMessage().text();

                if (aiResp == null || aiResp.isBlank()) {
                    log.warn("[Preselect] Batch {}/{} empty response, skip", i/batchSize+1,
                        (int)Math.ceil(factCards.size()/(double)batchSize));
                    continue;
                }
                log.info("[Preselect] Batch {}/{} raw response (first 200): {}",
                    i/batchSize+1, (int)Math.ceil(factCards.size()/(double)batchSize),
                    aiResp.length() > 200 ? aiResp.substring(0, 200) : aiResp);

                String json = aiResp;
                if (json.contains("```json")) json = json.substring(json.indexOf("```json") + 7);
                if (json.contains("```")) json = json.substring(0, json.lastIndexOf("```")).trim();
                int braceStart = json.indexOf('[');
                int braceEnd = json.lastIndexOf(']');
                if (braceStart >= 0 && braceEnd > braceStart) json = json.substring(braceStart, braceEnd + 1);
                if (json.isBlank()) {
                    log.warn("[Preselect] Batch {}/{} no valid JSON found after extraction", i/batchSize+1,
                        (int)Math.ceil(factCards.size()/(double)batchSize));
                    continue;
                }

                List<Map<String, Object>> batch = (List) JSON.parseArray(json, Map.class);
                if (batch != null) decisions.addAll(batch);
            } catch (Exception e) {
                log.warn("[Preselect] Batch {}/{} failed: {}", i/batchSize+1,
                    (int)Math.ceil(factCards.size()/(double)batchSize), e.getMessage());
            }
        }
        if (decisions.isEmpty()) return Collections.emptyList();

        // 4. 后端硬校验：只返回 include 的 DTO
        return hardValidate(decisions, samples);
    }

    // ── private helpers ──

    private String buildFactCard(CandidateProgramDTO p) {
        int quota = p.getUnifiedExamQuota() != null ? p.getUnifiedExamQuota()
            : (p.getPlanCount() != null ? p.getPlanCount() : 0);
        return String.format("ID=%s | %s | %s | 层次=%s | 城市=%s | %s | %s | %s | %s | 数据=%s",
            p.getProgramId(), p.getSchoolName(), p.getProgramName(),
            p.getSchoolTier(), p.getCity(),
            AiRecommendationTools.gapLabel(p.getGap()),
            AiRecommendationTools.quotaLabel(quota),
            p.isCanBeSafe() ? "可以保底" : "不可保底",
            AiRecommendationTools.quotaRiskLabel(p.getQuotaRisk()),
            p.getDataCompleteness() != null ? p.getDataCompleteness() : "?");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<CandidateProgramDTO> hardValidate(List<Map<String, Object>> decisions,
                                                    List<CandidateProgramDTO> samples) {
        Map<Long, CandidateProgramDTO> sampleMap = new LinkedHashMap<>();
        for (CandidateProgramDTO s : samples) {
            if (s.getProgramId() != null) sampleMap.put(s.getProgramId(), s);
        }

        List<CandidateProgramDTO> result = new ArrayList<>();
        int[] counts = new int[3]; // reach, steady, safe
        for (Map<String, Object> d : decisions) {
            if (!"include".equals(d.get("decision"))) continue;
            Long pid = toLong(d.get("programId"));
            if (pid == null) continue;
            CandidateProgramDTO p = sampleMap.get(pid);
            if (p == null) continue;

            int gap = p.getGap();
            int quota = p.getUnifiedExamQuota() != null ? p.getUnifiedExamQuota()
                : (p.getPlanCount() != null ? p.getPlanCount() : 0);

            String tier = String.valueOf(d.getOrDefault("tier", "steady"));
            if (gap < 0 && "safe".equals(tier)) tier = "reach";
            if (!p.isCanBeSafe() && "safe".equals(tier)) tier = gap >= 5 ? "steady" : "reach";
            if (quota <= 3 && "safe".equals(tier)) tier = "steady";

            int idx = "reach".equals(tier) ? 0 : "steady".equals(tier) ? 1 : 2;
            int max = idx == 0 ? 2 : idx == 1 ? 3 : 2;
            if (counts[idx] >= max) continue;
            counts[idx]++;

            // 更新 DTO 中的 tier（AI 的决策已修正）
            p.setGap(gap); // unchanged but signals we validated
            result.add(p);
        }
        return result;
    }

    private static Long toLong(Object val) {
        if (val instanceof Number n) return n.longValue();
        if (val == null) return null;
        try { return Long.parseLong(String.valueOf(val)); } catch (NumberFormatException e) { return null; }
    }
}
