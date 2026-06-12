package com.ruoyi.postgrad.recommend.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.recommend.domain.AiOpinion;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.SelectedItem;
import com.ruoyi.postgrad.recommend.domain.BlockedCandidateVO;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;
import com.ruoyi.postgrad.recommend.domain.ReplaceResultVO;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.DraftGenerationCallback;
import com.ruoyi.postgrad.recommend.service.IAiSelectorService;
import com.ruoyi.postgrad.recommend.service.ICandidatePoolService;
import com.ruoyi.postgrad.recommend.service.IDraftService;

/**
 * 草稿服务实现 —— 编排候选池构建、AI 选择、校验、Redis 持久化。
 * <p>调整操作（remove/replace/addBack/alternatives）委托给 {@link DraftAdjustServiceImpl}。</p>
 */
@Service
public class DraftServiceImpl implements IDraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftServiceImpl.class);

    static final String DRAFT_KEY_PREFIX = "ai:v2:draft:";
    static final String DRAFT_POOL_KEY_PREFIX = "ai:v2:draft:pool:";
    private static final long TTL_DAYS = 7;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private ICandidatePoolService candidatePoolService;

    @Autowired
    private IAiSelectorService aiSelectorService;

    @Autowired
    private DraftAdjustServiceImpl adjustService;

    // ==================== generateDraft ====================

    @Override
    public void generateDraft(Long userId, DraftGenerationCallback callback) {
        try {
            // 1. 加载用户画像
            callback.onProgress("loading_profile", "正在加载用户画像...", null, null);
            UserProfile up = userProfileMapper.selectUserProfileByUserId(userId);
            if (up == null || up.getEstimatedScore() == null || up.getEstimatedScore() <= 0) {
                callback.onError(new IllegalArgumentException("请先在个人资料中补充预计分数"));
                return;
            }
            int estimatedScore = up.getEstimatedScore();
            List<String> regions = parseRegions(up.getTargetRegions());
            String schoolTierPref = up.getSchoolTierPreference() != null
                ? up.getSchoolTierPreference() : "no_strict_requirement";

            // 2. 构建候选池 + 分档
            callback.onProgress("building_pool", "正在筛选候选学校...", null, null);
            List<TierCandidates> allTiers = candidatePoolService.buildPool(estimatedScore, regions, schoolTierPref);
            int totalCandidates = allTiers.stream().mapToInt(t -> t.getCandidates().size()).sum();
            log.info("[Draft] userId={} — pool: {} candidates across 3 tiers", userId, totalCandidates);

            savePoolSnapshot(userId, allTiers);

            // 3. 对每档调用 AI 选择
            List<TierCandidates> resultTiers = new ArrayList<>(3);
            List<BlockedCandidateVO> allBlocked = new ArrayList<>();

            for (TierCandidates tier : allTiers) {
                callback.onProgress("ai_selecting",
                    "AI 正在" + tier.getLabel() + "挑选合适的学校...", null, tier.getLevel());

                AiSelectionResult sel = aiSelectorService.select(
                    tier.getLevel(), tier.getCandidates(), estimatedScore);

                TierCandidates resultTier = mergeSelection(tier, sel);
                resultTiers.add(resultTier);

                if (sel.getBlocked() != null) {
                    for (AiSelectionResult.BlockedItem bi : sel.getBlocked()) {
                        BlockedCandidateVO bvo = new BlockedCandidateVO();
                        bvo.setProgramId(bi.getProgramId());
                        bvo.setSchoolName(bi.getSchoolName());
                        bvo.setBlockReason(bi.getBlockReason());
                        allBlocked.add(bvo);
                    }
                }
            }

            callback.onProgress("validating", "正在校验 AI 推荐结果...", null, null);

            // 4. 构建 DraftVO + 持久化
            ProfileBasisVO basis = buildProfileBasis(up, totalCandidates);
            DraftVO draft = new DraftVO();
            draft.setTiers(resultTiers);
            draft.setRemovedCandidates(Collections.emptyList());
            draft.setBlockedCandidates(allBlocked);
            draft.setProfileBasis(basis);
            draft.setGeneratedAt(LocalDateTime.now());

            saveDraft(userId, draft);
            callback.onDone(draft, basis, allBlocked.size());

        } catch (Exception e) {
            log.error("[Draft] generateDraft failed for userId={}: {}", userId, e.getMessage(), e);
            callback.onError(e);
        }
    }

    // ==================== getDraft ====================

    @Override
    public DraftVO getDraft(Long userId) {
        String json = redisTemplate.opsForValue().get(draftKey(userId));
        if (json == null || json.isBlank()) return emptyDraft();
        try {
            return JSON.parseObject(json, DraftVO.class);
        } catch (Exception e) {
            log.warn("[Draft] Failed to parse draft for userId={}: {}", userId, e.getMessage());
            return emptyDraft();
        }
    }

    // ==================== delegate to DraftAdjustServiceImpl ====================

    @Override
    public DraftVO removeCandidate(Long userId, Long programId) {
        return adjustService.removeCandidate(userId, programId);
    }

    @Override
    public ReplaceResultVO replaceCandidate(Long userId, Long removeProgramId, String tier, String preference) {
        return adjustService.replaceCandidate(userId, removeProgramId, tier, preference);
    }

    @Override
    public DraftVO addBackCandidate(Long userId, Long programId) {
        return adjustService.addBackCandidate(userId, programId);
    }

    @Override
    public List<CandidateCardVO> getAlternatives(Long userId, String tier, Long excludeId) {
        return adjustService.getAlternatives(userId, tier, excludeId);
    }

    // ==================== private helpers ====================

    private void saveDraft(Long userId, DraftVO draft) {
        redisTemplate.opsForValue().set(draftKey(userId), JSON.toJSONString(draft), Duration.ofDays(TTL_DAYS));
    }

    /**
     * 将 AI 选择结果合并到 TierCandidates：选中的填充 opinion，未选中的移除。
     */
    private TierCandidates mergeSelection(TierCandidates tier, AiSelectionResult sel) {
        Map<Long, SelectedItem> selectedMap = new LinkedHashMap<>();
        if (sel.getSelected() != null) {
            for (SelectedItem si : sel.getSelected()) {
                if (si.getProgramId() != null) selectedMap.put(si.getProgramId(), si);
            }
        }

        List<CandidateCardVO> kept = new ArrayList<>();
        for (CandidateCardVO c : tier.getCandidates()) {
            Long pid = c.getFact().getProgramId();
            SelectedItem si = selectedMap.get(pid);
            if (si != null) {
                AiOpinion op = new AiOpinion();
                op.setReason(si.getReason());
                op.setRisks(si.getRisks());
                op.setPros(si.getPros());
                op.setCons(si.getCons());
                c.setOpinion(op);
                c.setStatus("selected");
                c.setFinalJudgement(tier.getLevel());
                c.setAdjusted(false);
                kept.add(c);
            }
        }

        kept.sort((a, b) -> {
            Integer ga = a.getFact().getScoreGap();
            Integer gb = b.getFact().getScoreGap();
            if (ga == null && gb == null) return 0;
            if (ga == null) return 1;
            if (gb == null) return -1;
            return Integer.compare(gb, ga);
        });

        TierCandidates result = new TierCandidates();
        result.setLevel(tier.getLevel());
        result.setLabel(tier.getLabel());
        result.setTargetCount(tier.getTargetCount());
        result.setCandidates(kept);
        result.setInsufficient(kept.size() < tier.getTargetCount());
        if (result.isInsufficient()) {
            result.setInsufficientReason(
                String.format("%s候选不足，仅找到 %d 所可信候选。", tier.getLabel(), kept.size()));
        }
        return result;
    }

    private ProfileBasisVO buildProfileBasis(UserProfile up, int candidateCount) {
        ProfileBasisVO b = new ProfileBasisVO();
        b.setEstimatedScore(up.getEstimatedScore());
        b.setTargetRegions(up.getTargetRegions() != null ? up.getTargetRegions() : "不限");
        b.setUndergradTier(up.getUndergradTier() != null ? up.getUndergradTier() : "双非");
        b.setIsCrossMajor((up.getIsCrossMajor() != null && up.getIsCrossMajor() == 1) ? "是" : "否");
        b.setRiskPreference(prefLabel(up.getRiskPreference(), "risk"));
        b.setSchoolTierPreference(prefLabel(up.getSchoolTierPreference(), "tier"));
        b.setRegionStrategy(prefLabel(up.getRegionStrategy(), "region"));
        b.setCandidateScope("系统按画像自动选择最多 " + candidateCount + " 个具备录取数据的 408 项目");
        return b;
    }

    private void savePoolSnapshot(Long userId, List<TierCandidates> allTiers) {
        List<CandidateCardVO> all = new ArrayList<>();
        for (TierCandidates t : allTiers) {
            if (t.getCandidates() != null) all.addAll(t.getCandidates());
        }
        redisTemplate.opsForValue().set(draftPoolKey(userId), JSON.toJSONString(all), Duration.ofDays(TTL_DAYS));
    }

    // ── key / util / empty ──

    private String draftKey(Long userId) { return DRAFT_KEY_PREFIX + userId; }
    private String draftPoolKey(Long userId) { return DRAFT_POOL_KEY_PREFIX + userId; }

    private DraftVO emptyDraft() {
        DraftVO d = new DraftVO();
        List<TierCandidates> tiers = new ArrayList<>(3);
        tiers.add(emptyTier("reach", "冲刺档", 3));
        tiers.add(emptyTier("steady", "稳妥档", 4));
        tiers.add(emptyTier("safe", "保底档", 3));
        d.setTiers(tiers);
        d.setRemovedCandidates(Collections.emptyList());
        d.setBlockedCandidates(Collections.emptyList());
        return d;
    }

    private TierCandidates emptyTier(String level, String label, int target) {
        TierCandidates t = new TierCandidates();
        t.setLevel(level); t.setLabel(label); t.setTargetCount(target);
        t.setCandidates(Collections.emptyList());
        t.setInsufficient(true);
        t.setInsufficientReason("点击「生成 AI 推荐草稿」开始");
        return t;
    }

    private List<String> parseRegions(String raw) {
        if (raw == null || raw.isBlank() || "不限".equals(raw) || "[]".equals(raw)) return Collections.emptyList();
        try { return JSON.parseArray(raw, String.class); }
        catch (Exception e) { return Collections.emptyList(); }
    }

    private String prefLabel(String val, String type) {
        if (val == null) val = "";
        return switch (type) {
            case "risk" -> switch (val) {
                case "safe_first" -> "稳妥优先";
                case "reach_first" -> "冲刺优先";
                default -> "均衡策略";
            };
            case "tier" -> switch (val) {
                case "must_211_or_better" -> "强烈倾向 211/双一流及以上";
                case "prefer_211_or_better" -> "优先 211/双一流及以上";
                default -> "不强求层次";
            };
            case "region" -> switch (val) {
                case "developed_priority" -> "发达地区优先";
                case "developed_balanced" -> "发达地区兼顾稳妥";
                case "target_regions_only" -> "只看目标地区";
                default -> "地区不强求";
            };
            default -> val;
        };
    }
}
