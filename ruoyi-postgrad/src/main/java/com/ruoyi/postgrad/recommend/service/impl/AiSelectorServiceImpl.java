package com.ruoyi.postgrad.recommend.service.impl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.AiOpinion;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.BlockedItem;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.SelectedItem;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.service.IAiSelectorService;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * AI 选校服务实现 —— 在给定候选事实卡内由 AI 挑选。
 * <p>每档一次单轮 completion，非对话。AI 只选不给规则判断。</p>
 */
@Service
public class AiSelectorServiceImpl implements IAiSelectorService {

    private static final Logger log = LoggerFactory.getLogger(AiSelectorServiceImpl.class);

    /** 每档最多选几所 */
    private static final int REACH_LIMIT = 3;
    private static final int STEADY_LIMIT = 4;
    private static final int SAFE_LIMIT = 3;

    /** 从 AI 响应中提取 JSON 数组的正则 */
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[[\\s\\S]*?\\]");

    @Value("classpath:prompts/v2/select-reach.txt")
    private org.springframework.core.io.Resource reachPromptResource;

    @Value("classpath:prompts/v2/select-steady.txt")
    private org.springframework.core.io.Resource steadyPromptResource;

    @Value("classpath:prompts/v2/select-safe.txt")
    private org.springframework.core.io.Resource safePromptResource;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private SelectionValidator validator;

    @Override
    public AiSelectionResult select(String tier, List<CandidateCardVO> candidates, int estimatedScore) {
        // 1. 空候选 → 空结果
        if (candidates == null || candidates.isEmpty()) {
            log.info("[AiSelector] tier={} has no candidates, skip", tier);
            return emptyResult(tier);
        }

        // 2. 候选数 ≤ 档位上限 → 跳过 AI，全部选中
        int limit = tierLimit(tier);
        if (candidates.size() <= limit) {
            log.info("[AiSelector] tier={} has {} candidates ≤ limit {}, skip AI", tier, candidates.size(), limit);
            return selectAll(tier, candidates);
        }

        // 3. 构建事实卡文本
        String factsText = buildFactsText(candidates);

        // 4. 加载提示词
        String prompt = loadTierPrompt(tier);
        if (prompt.isEmpty()) {
            log.warn("[AiSelector] tier={} prompt not found, using selectAll fallback", tier);
            return selectAll(tier, candidates);
        }

        // 5. 调用 LLM
        log.info("[AiSelector] tier={} — sending {} candidates to AI (limit={})", tier, candidates.size(), limit);
        String aiRaw;
        try {
            aiRaw = chatModel.chat(
                SystemMessage.from(prompt),
                UserMessage.from(factsText)
            ).aiMessage().text();
        } catch (Exception e) {
            log.error("[AiSelector] tier={} LLM call failed: {}", tier, e.getMessage());
            // 降级：取综合得分最高的前 N 所
            return selectAll(tier, candidates.subList(0, Math.min(limit, candidates.size())));
        }

        log.info("[AiSelector] tier={} AI raw response (first 300): {}",
            tier, aiRaw != null && aiRaw.length() > 300 ? aiRaw.substring(0, 300) : aiRaw);

        if (aiRaw == null || aiRaw.isBlank()) {
            log.warn("[AiSelector] tier={} empty AI response, fallback", tier);
            return selectAll(tier, candidates.subList(0, Math.min(limit, candidates.size())));
        }

        // 6. 解析 JSON
        List<SelectedItem> parsed = parseAiResponse(aiRaw);
        if (parsed.isEmpty()) {
            log.warn("[AiSelector] tier={} failed to parse AI JSON, fallback", tier);
            return selectAll(tier, candidates.subList(0, Math.min(limit, candidates.size())));
        }

        // 7. 校验
        return validator.validate(tier, parsed, candidates);
    }

    // ── private helpers ──

    /**
     * 构建候选事实卡文本（供 LLM 选择）。
     * <p>格式：ID:123 | XX大学 | 计算机技术 | 985 | 北京 | 均分345 | 差距+10 | 招生15人 | 名额正常 | 可保底</p>
     */
    private String buildFactsText(List<CandidateCardVO> candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            SchoolFact f = candidates.get(i).getFact();
            if (f == null) continue;
            sb.append(i + 1).append(". ");
            sb.append("ID:").append(f.getProgramId());
            sb.append(" | ").append(nullToDash(f.getSchoolName()));
            sb.append(" | ").append(nullToDash(f.getProgramName()));
            sb.append(" | 层次:").append(nullToDash(f.getSchoolTier()));
            sb.append(" | 城市:").append(nullToDash(f.getCity()));
            sb.append(" | 均分:").append(nullToDash(f.getAvgAdmittedScore()));
            sb.append(" | 差距:").append(nullToDash(f.getGapLabel()));
            sb.append(" | 招生:").append(nullToDash(f.getUnifiedExamQuota() != null
                ? f.getUnifiedExamQuota() : f.getPlanCount()));
            sb.append(" | ").append(nullToDash(f.getQuotaLabel()));
            sb.append(" | ").append(Boolean.TRUE.equals(f.getCanBeSafe()) ? "可以保底" : "不可保底");
            sb.append(" | 数据:").append(nullToDash(f.getDataCompleteness()));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 加载指定档位的提示词（从 classpath 资源文件）。
     */
    private String loadTierPrompt(String tier) {
        try {
            org.springframework.core.io.Resource resource = switch (tier) {
                case "reach" -> reachPromptResource;
                case "steady" -> steadyPromptResource;
                case "safe" -> safePromptResource;
                default -> null;
            };
            if (resource == null) return "";
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[AiSelector] Failed to load prompt for tier={}: {}", tier, e.getMessage());
            return "";
        }
    }

    /**
     * 解析 AI 返回的 JSON（多层防御）。
     * <p>策略：提取 ```json``` 代码块 → 提取首个 [...] → JSON.parseArray → 兜底空列表。</p>
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<SelectedItem> parseAiResponse(String raw) {
        String cleaned = raw.trim();

        // 1. 提取 ```json ... ``` 代码块
        int codeStart = cleaned.indexOf("```json");
        if (codeStart >= 0) {
            int contentStart = cleaned.indexOf('\n', codeStart);
            if (contentStart >= 0) {
                int codeEnd = cleaned.indexOf("```", contentStart);
                if (codeEnd > contentStart) {
                    cleaned = cleaned.substring(contentStart, codeEnd).trim();
                }
            }
        } else {
            // 尝试 ``` 不带 json 标记
            codeStart = cleaned.indexOf("```");
            if (codeStart >= 0) {
                int contentStart = cleaned.indexOf('\n', codeStart);
                if (contentStart >= 0) {
                    int codeEnd = cleaned.indexOf("```", contentStart);
                    if (codeEnd > contentStart) {
                        cleaned = cleaned.substring(contentStart, codeEnd).trim();
                    }
                }
            }
        }

        // 2. 提取第一个 [ 到最后一个 ]
        int arrayStart = cleaned.indexOf('[');
        int arrayEnd = cleaned.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            cleaned = cleaned.substring(arrayStart, arrayEnd + 1).trim();
        }

        if (cleaned.isBlank()) {
            return Collections.emptyList();
        }

        // 3. JSON 解析
        try {
            List<SelectedItem> items = new ArrayList<>();
            List<com.alibaba.fastjson2.JSONObject> rawList = JSON.parseArray(cleaned, com.alibaba.fastjson2.JSONObject.class);
            if (rawList == null) return Collections.emptyList();

            for (com.alibaba.fastjson2.JSONObject obj : rawList) {
                SelectedItem item = new SelectedItem();
                item.setProgramId(obj.getLong("programId"));
                item.setReason(obj.getString("reason"));
                item.setRisks(toStringList(obj, "risks"));
                item.setPros(toStringList(obj, "pros"));
                item.setCons(toStringList(obj, "cons"));
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("[AiSelector] JSON parse failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> toStringList(com.alibaba.fastjson2.JSONObject obj, String key) {
        Object val = obj.get(key);
        if (val instanceof List<?> list) {
            List<String> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) result.add(item.toString());
            }
            return result;
        }
        return Collections.emptyList();
    }

    /**
     * 候选数 ≤ 上限时全部选中（AI 给出通用理由）。
     */
    private AiSelectionResult selectAll(String tier, List<CandidateCardVO> candidates) {
        AiSelectionResult result = new AiSelectionResult();
        result.setTier(tier);

        List<SelectedItem> selected = new ArrayList<>(candidates.size());
        for (CandidateCardVO c : candidates) {
            SchoolFact f = c.getFact();
            SelectedItem item = new SelectedItem();
            item.setProgramId(f.getProgramId());
            item.setReason(String.format("候选池数量有限（共%d所），按数据完整度和分数匹配度入选。",
                candidates.size()));
            item.setRisks(Collections.emptyList());
            item.setPros(Collections.emptyList());
            item.setCons(Collections.emptyList());
            selected.add(item);
        }
        result.setSelected(selected);
        result.setBlocked(Collections.emptyList());
        return result;
    }

    private AiSelectionResult emptyResult(String tier) {
        AiSelectionResult result = new AiSelectionResult();
        result.setTier(tier);
        result.setSelected(Collections.emptyList());
        result.setBlocked(Collections.emptyList());
        return result;
    }

    private int tierLimit(String tier) {
        return switch (tier) {
            case "reach" -> REACH_LIMIT;
            case "steady" -> STEADY_LIMIT;
            case "safe" -> SAFE_LIMIT;
            default -> 3;
        };
    }

    private static String nullToDash(Object v) {
        return v == null ? "-" : v.toString();
    }
}
