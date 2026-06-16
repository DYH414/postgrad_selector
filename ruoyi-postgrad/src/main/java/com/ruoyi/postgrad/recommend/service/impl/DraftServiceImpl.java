package com.ruoyi.postgrad.recommend.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.recommend.domain.AiOpinion;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult.SelectedItem;
import com.ruoyi.postgrad.recommend.domain.BlockedCandidateVO;
import com.ruoyi.postgrad.recommend.domain.DraftMutationResultVO;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.CandidateUniverseVO;
import com.ruoyi.postgrad.recommend.domain.CandidateWorkspaceVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.ProfileBasisVO;
import com.ruoyi.postgrad.recommend.domain.ReplaceResultVO;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.domain.WorkspaceTierVO;
import com.ruoyi.postgrad.recommend.service.DraftGenerationCallback;
import com.ruoyi.postgrad.recommend.service.IAiSelectorService;
import com.ruoyi.postgrad.recommend.service.ICandidatePoolService;
import com.ruoyi.postgrad.recommend.service.IDraftService;

/**
 * 草稿服务实现 —— 编排候选池构建、AI 选择、校验、Redis 持久化。
 * <p>调整操作（remove/replace/addBack/alternatives）委托给 {@code IDraftMutationService}，触发 refill 策略。</p>
 */
@Service
public class DraftServiceImpl implements IDraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftServiceImpl.class);

    public static final String DRAFT_KEY_PREFIX = "ai:v2:draft:";
    public static final String DRAFT_POOL_KEY_PREFIX = "ai:v2:draft:pool:";
    static final String UNIVERSE_KEY_PREFIX = "ai:v2:universe:";
    static final String WORKSPACE_KEY_PREFIX = "ai:v2:workspace:";
    private static final String LOCK_KEY_PREFIX = "ai:v2:draft:lock:";
    private static final long TTL_DAYS = 7;
    /** 生成锁 TTL：5 分钟，防止重复点击 */
    private static final java.time.Duration LOCK_TTL = java.time.Duration.ofMinutes(5);
    /**
     * 安全释放锁的 Lua 脚本：仅当 value 匹配时才删除。
     * <p>防止锁过期后被其他请求获取，当前请求误删他人锁。</p>
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end");
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private ICandidatePoolService candidatePoolService;

    @Autowired
    private IAiSelectorService aiSelectorService;

    @Autowired
    private com.ruoyi.postgrad.recommend.service.IDraftMutationService draftMutationService;

    @Autowired
    private com.ruoyi.postgrad.recommend.service.ICandidateUniverseService universeService;

    @Autowired
    private com.ruoyi.postgrad.recommend.service.ICandidateWorkspaceService workspaceService;

    @Autowired
    private com.ruoyi.postgrad.mapper.AiChatMapper aiChatMapper;

    // ==================== generateDraft ====================

    @Override
    public void generateDraft(Long userId, DraftGenerationCallback callback) {
        // 生成锁：防止重复点击导致多个 LLM 调用并行
        // 使用 UUID 作为锁持有者标识，释放时 Lua 原子校验，防止误删他人锁
        String lockKey = LOCK_KEY_PREFIX + userId;
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL);
        if (Boolean.FALSE.equals(locked)) {
            callback.onError(new IllegalStateException("已有草稿正在生成，请稍候"));
            return;
        }
        try {
            // 1. 终结旧对话 + 清除 Redis 聊天记忆（新草稿 = 新上下文）
            aiChatMapper.finalizeActiveConversations(userId);
            redisTemplate.delete("ai:v2:chat:msg:" + userId);
            redisTemplate.delete("ai:v2:chat:system:" + userId);

            // 2. 加载用户画像
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

            // 2.5 构建 Universe → Workspace（混合架构：保留广泛候选供给）
            ProfileBasisVO basis = buildProfileBasis(up, totalCandidates);
            CandidateUniverseVO universe = universeService.buildUniverse(
                userId, basis, estimatedScore, regions, schoolTierPref);
            saveUniverse(userId, universe);
            // 用 Universe 真实规模更新画像依据
            int universeCount = universe.candidateCount();
            basis = buildProfileBasis(up, universeCount);
            CandidateWorkspaceVO workspace = workspaceService.buildWorkspace(
                universe, schoolTierPref, up.getRegionStrategy());
            saveWorkspace(userId, workspace);
            log.info("[Draft] Universe={} Workspace={} Pool={}",
                universeCount, workspace.totalCandidates(), totalCandidates);

            // 3. 对每档从 Workspace 调用 AI 选择（混合架构：30/档 而不是旧池的 15/档）
            List<TierCandidates> resultTiers = new ArrayList<>(3);
            List<BlockedCandidateVO> allBlocked = new ArrayList<>();
            Map<String, Integer> wsSummary = new LinkedHashMap<>();

            for (WorkspaceTierVO wsTier : workspace.getTiers()) {
                String tierLevel = wsTier.getLevel();
                String tierLabel = wsTier.getLabel();
                List<CandidateCardVO> wsCandidates = wsTier.getCandidates();
                int draftTarget = "reach".equals(tierLevel) ? 3 : "steady".equals(tierLevel) ? 4 : 3;

                callback.onProgress("ai_selecting",
                    "AI 正在" + tierLabel + "挑选合适的学校...（工作集 " + wsCandidates.size() + " 所）",
                    null, tierLevel);

                AiSelectionResult sel = aiSelectorService.select(
                    tierLevel, wsCandidates, estimatedScore);

                // 包装为 TierCandidates 保持下游兼容
                TierCandidates rawTier = new TierCandidates();
                rawTier.setLevel(tierLevel);
                rawTier.setLabel(tierLabel);
                rawTier.setTargetCount(draftTarget);
                rawTier.setCandidates(wsCandidates);
                TierCandidates resultTier = mergeSelection(rawTier, sel);
                resultTiers.add(resultTier);
                wsSummary.put(tierLevel, wsCandidates.size());

                // ★ 每档选完立刻持久化：刷新/断连后能从 Redis 恢复已完成的档位
                DraftVO partial = buildPartialDraft(resultTiers, allBlocked, basis, wsSummary);
                saveDraft(userId, partial);

                callback.onTierComplete(tierLevel, JSON.toJSONString(resultTier));

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

            // 4. 构建最终 DraftVO（已在循环中逐档持久化）
            DraftVO draft = new DraftVO();
            draft.setTiers(resultTiers);
            draft.setRemovedCandidates(Collections.emptyList());
            draft.setBlockedCandidates(allBlocked);
            draft.setProfileBasis(basis);
            draft.setGeneratedAt(LocalDateTime.now());
            draft.setWorkspaceSummary(wsSummary);

            saveDraft(userId, draft);
            callback.onDone(draft, basis, allBlocked.size());

        } catch (Exception e) {
            log.error("[Draft] generateDraft failed for userId={}: {}", userId, e.getMessage(), e);
            callback.onError(e);
        } finally {
            // 仅当锁仍属于当前持有者时才释放（Lua 原子操作）
            redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
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

    // ==================== delegate to DraftMutationService ====================

    private CandidateWorkspaceVO loadWorkspace(Long userId) {
        String json = redisTemplate.opsForValue().get(WORKSPACE_KEY_PREFIX + userId);
        if (json == null || json.isBlank()) return null;
        try { return JSON.parseObject(json, CandidateWorkspaceVO.class); }
        catch (Exception e) { return null; }
    }

    @Override
    public DraftVO removeCandidate(Long userId, Long programId) {
        CandidateWorkspaceVO ws = loadWorkspace(userId);
        DraftMutationResultVO r = draftMutationService.removeCandidate(userId, programId, ws);
        return r.getDraft();
    }

    @Override
    public ReplaceResultVO replaceCandidate(Long userId, Long removeProgramId, String tier, String preference) {
        CandidateWorkspaceVO ws = loadWorkspace(userId);
        DraftMutationResultVO r = draftMutationService.replaceCandidate(
            userId, removeProgramId, removeProgramId /* add candidate handled by mutation */, tier, ws);
        ReplaceResultVO result = new ReplaceResultVO();
        result.setDraft(r.getDraft());
        return result;
    }

    @Override
    public DraftVO addFromWorkspace(Long userId, String tier, String preference) {
        CandidateWorkspaceVO ws = loadWorkspace(userId);
        if (ws == null) throw new IllegalStateException("工作集已过期，请重新生成草稿");

        DraftVO draft = getDraft(userId);
        // 检查档位是否已满
        for (TierCandidates t : draft.getTiers()) {
            if (t.getLevel().equals(tier)) {
                int current = t.getCandidates() != null ? t.getCandidates().size() : 0;
                if (current >= t.getTargetCount()) {
                    throw new IllegalStateException(t.getLabel() + "已满（" + current + "/" + t.getTargetCount() + "），无需补充");
                }
                break;
            }
        }

        // 收集已在草稿中的候选 ID
        java.util.Set<Long> draftIds = new java.util.LinkedHashSet<>();
        if (draft.getTiers() != null) {
            for (TierCandidates t : draft.getTiers()) {
                if (t.getCandidates() != null) {
                    for (CandidateCardVO c : t.getCandidates()) {
                        if (c.getFact().getProgramId() != null) draftIds.add(c.getFact().getProgramId());
                    }
                }
            }
        }

        // 从工作集同档选取最佳候选
        var wsTier = ws.tierByLevel(tier);
        if (wsTier == null || wsTier.getCandidates() == null || wsTier.getCandidates().isEmpty()) {
            throw new IllegalStateException("工作集中该档位没有候选");
        }

        CandidateCardVO best = wsTier.getCandidates().stream()
            .filter(c -> c.getFact().getProgramId() != null
                && !draftIds.contains(c.getFact().getProgramId()))
            .min((a, b) -> {
                if ("safer".equals(preference)) {
                    Integer ga = a.getFact().getScoreGap(); Integer gb = b.getFact().getScoreGap();
                    return Integer.compare(gb != null ? gb : 0, ga != null ? ga : 0);
                }
                return 0;
            })
            .orElseThrow(() -> new IllegalStateException("该档位没有可用的新候选"));

        DraftMutationResultVO r = draftMutationService.addCandidate(userId,
            best.getFact().getProgramId(), tier, ws);
        return r.getDraft();
    }

    @Override
    public DraftVO addBackCandidate(Long userId, Long programId) {
        // 从 removedCandidates 找到候选的原始档位
        DraftVO draft = getDraft(userId);
        String tier = "steady";
        if (draft.getRemovedCandidates() != null) {
            for (CandidateCardVO c : draft.getRemovedCandidates()) {
                if (programId.equals(c.getFact().getProgramId())) {
                    tier = c.getFact().inferTier();
                    break;
                }
            }
        }
        CandidateWorkspaceVO ws = loadWorkspace(userId);
        DraftMutationResultVO r = draftMutationService.addCandidate(userId, programId, tier, ws);
        return r.getDraft();
    }

    @Override
    public List<CandidateCardVO> getAlternatives(Long userId, String tier, Long excludeId) {
        // 从 workspace 获取替代候选
        CandidateWorkspaceVO ws = loadWorkspace(userId);
        if (ws == null) return Collections.emptyList();
        var wsTier = ws.tierByLevel(tier);
        if (wsTier == null || wsTier.getCandidates() == null) return Collections.emptyList();
        return wsTier.getCandidates().stream()
            .filter(c -> !c.getFact().getProgramId().equals(excludeId))
            .limit(10)
            .collect(java.util.stream.Collectors.toList());
    }

    // ==================== private helpers ====================

    private void saveDraft(Long userId, DraftVO draft) {
        redisTemplate.opsForValue().set(draftKey(userId), JSON.toJSONString(draft), Duration.ofDays(TTL_DAYS));
    }

    /**
     * 构建部分草稿（用于逐档持久化，支持刷新恢复）。
     * <p>未完成的档位标记为 insufficient，前端据此显示 loading 状态。</p>
     */
    private DraftVO buildPartialDraft(List<TierCandidates> doneTiers,
                                       List<BlockedCandidateVO> blocked,
                                       ProfileBasisVO basis,
                                       Map<String, Integer> wsSummary) {
        DraftVO partial = new DraftVO();
        // 已完成的档位直接拷贝
        List<TierCandidates> tiers = new ArrayList<>();
        for (TierCandidates t : doneTiers) {
            TierCandidates copy = new TierCandidates();
            copy.setLevel(t.getLevel());
            copy.setLabel(t.getLabel());
            copy.setTargetCount(t.getTargetCount());
            copy.setCandidates(t.getCandidates() != null ? new ArrayList<>(t.getCandidates()) : new ArrayList<>());
            copy.setInsufficient(false);
            tiers.add(copy);
        }
        // 补齐未完成的档位（空占位）
        String[][] allTierDefs = {{"reach", "冲刺档", "3"}, {"steady", "稳妥档", "4"}, {"safe", "保底档", "3"}};
        for (String[] def : allTierDefs) {
            boolean alreadyDone = tiers.stream().anyMatch(t -> def[0].equals(t.getLevel()));
            if (!alreadyDone) {
                TierCandidates pending = new TierCandidates();
                pending.setLevel(def[0]);
                pending.setLabel(def[1]);
                pending.setTargetCount(Integer.parseInt(def[2]));
                pending.setCandidates(new ArrayList<>());
                pending.setInsufficient(true);
                pending.setInsufficientReason("AI 正在" + def[1] + "挑选合适的学校...");
                tiers.add(pending);
            }
        }
        partial.setTiers(tiers);
        partial.setRemovedCandidates(Collections.emptyList());
        partial.setBlockedCandidates(blocked != null ? blocked : Collections.emptyList());
        partial.setProfileBasis(basis);
        partial.setGeneratedAt(LocalDateTime.now());
        partial.setWorkspaceSummary(wsSummary);
        return partial;
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

    private void saveUniverse(Long userId, CandidateUniverseVO universe) {
        redisTemplate.opsForValue().set(
            UNIVERSE_KEY_PREFIX + userId, JSON.toJSONString(universe), Duration.ofDays(TTL_DAYS));
    }

    private void saveWorkspace(Long userId, CandidateWorkspaceVO workspace) {
        redisTemplate.opsForValue().set(
            WORKSPACE_KEY_PREFIX + userId, JSON.toJSONString(workspace), Duration.ofDays(TTL_DAYS));
    }

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
        if (raw == null || raw.isBlank() || "不限".equals(raw) || "[]".equals(raw)) {
            log.info("[Draft] parseRegions: empty/unset (raw={})", raw == null ? "null" : "'" + raw + "'");
            return Collections.emptyList();
        }
        try {
            List<String> result = JSON.parseArray(raw, String.class);
            log.info("[Draft] parseRegions: parsed {} regions from raw='{}'", result.size(), raw);
            return result;
        } catch (Exception e) {
            log.warn("[Draft] parseRegions: FAILED to parse raw='{}' — {}", raw, e.getMessage());
            return Collections.emptyList();
        }
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
