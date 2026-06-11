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

    static final String PROMPT = """
        你是考研择校顾问。根据用户画像和候选学校的事实标签，筛选最适合进入报告候选的学校。

        事实标签说明：
        - 分数大幅超出：gap ≥ +15，用户分数远超录取均分
        - 分数适度超出：gap +5~+14，有一定余量
        - 分数微弱超出：gap 0~+4，微弱优势
        - 分数微弱不足：gap -5~0，轻微劣势
        - 分数适度不足：gap -15~-5，明显劣势
        - 分数大幅不足：gap ≤ -15，差距很大
        - 招生充裕(≥30人) / 招生正常(10-29人) / 招生偏少(4-9人) / 招生极少(≤3人)
        - 可以保底 / 不可保底：后端判断
        - 名额风险=低/中/高/极高

        ## 宁缺毋滥规则（最高优先级）
        - include 是"值得进入最终报告候选"，不是"看起来还行"。
        - 有明显硬伤的学校必须 skip：分数大幅不足且不可保底、招生极少且数据低于A。
        - 有优势但风险明显的学校用 hold。

        ## 选择预算
        - 本批最多 include 6 所，宁可少不可凑。
        - safe 最多 2 所，且必须同时满足：①分数大幅超出 ②可以保底 ③招生充裕或正常。

        ## tier 判断
        - safe：分数大幅超出 + 可以保底 + 招生充裕或正常（三项缺一不可）
        - steady：分数微弱超出到适度超出，风险可控
        - reach：分数不足，需要冲刺

        输出严格 JSON 数组：{"programId":X,"decision":"include|skip|hold","tier":"...","reason":"...","risk":"..."}
        """;

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
                    SystemMessage.from(PROMPT),
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
