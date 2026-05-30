# AI Analyze Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-shot `POST /app/ai-recommend/analyze` endpoint that generates recommendations directly from profile + school data, without multi-turn conversation.

**Architecture:** New Mapper SQL queries 408 schools within ±20 score range filtered by profile. Service stratifies results into 保底/稳妥/冲刺 tiers (max 50 total). Pool stored in Redis; MQ message carries only `{reportId, estimatedScore, userId, mode:"analyze"}`. Consumer reads Redis, builds prompt, calls AI, injects full data. Frontend adds "快速推荐" button alongside existing "AI对话推荐".

**Tech Stack:** Spring Boot/MyBatis/Redis/RabbitMQ (Java 17), Vue 3/Element Plus/Vite (user-ui)

---

## File Structure

| File | Operation | Responsibility |
|------|-----------|---------------|
| `ruoyi-postgrad/.../mapper/RecommendationMapper.java` | Modify | Add `selectForAnalysis` method signature |
| `ruoyi-postgrad/.../mapper/postgrad/RecommendationMapper.xml` | Modify | Add `selectForAnalysis` SQL |
| `ruoyi-postgrad/.../service/IAiCandidatePoolService.java` | Modify | Add `buildAnalysisPool` method |
| `ruoyi-postgrad/.../service/impl/AiCandidatePoolServiceImpl.java` | Modify | Implement `buildAnalysisPool` with stratification |
| `ruoyi-postgrad/.../service/IAiRecommendationService.java` | Modify | Add `analyze` method |
| `ruoyi-postgrad/.../service/impl/AiRecommendationServiceImpl.java` | Modify | Implement `analyze` + `buildAnalysisPrompt` |
| `ruoyi-admin/.../controller/postgrad/AppAiRecommendationController.java` | Modify | Add `POST /analyze` endpoint |
| `ruoyi-admin/.../controller/postgrad/AiReportConsumer.java` | Modify | Support `mode: "analyze"`, replace `injectMatchScores` with `injectFullData` |
| `user-ui/src/views/AiRecommend.vue` | Modify | Two buttons layout |
| `user-ui/src/api/ai.js` | Modify | Add `postAiAnalyze` |

---

### Task 1: Mapper — `selectForAnalysis` SQL

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml`

- [ ] **Step 1: Add method signature to Mapper interface**

In `RecommendationMapper.java`, after the existing `selectCandidates` method, add:

```java
List<RowMap> selectForAnalysis(@Param("estimatedScore") int estimatedScore,
                               @Param("regions") List<String> regions,
                               @Param("minScore") int minScore,
                               @Param("maxScore") int maxScore);
```

- [ ] **Step 2: Add SQL to Mapper XML**

In `RecommendationMapper.xml`, after the existing `selectCandidates` block (before the closing `</mapper>` tag), add:

```xml
<select id="selectForAnalysis" resultType="com.ruoyi.postgrad.domain.RowMap">
    select
        p.id as programId, s.name as schoolName, s.tier as schoolTier,
        s.province as province, s.city as city,
        c.name as collegeName, p.program_name as programName,
        p.degree_type as degreeType,
        sc.year as dataYear, sc.score_line as scoreLine,
        ar.admitted_count as admittedCount,
        ar.min_admitted_score as admissionLow,
        ar.avg_admitted_score as avgAdmittedScore,
        ar.max_admitted_score as admissionHigh,
        ap.total_plan as planCount,
        ap.retest_count as retestCount,
        coalesce(q.completeness_level, 'C') as dataCompleteness,
        coalesce(ar_ds.url, sc_ds.url, ap_ds.url) as sourceUrl,
        coalesce(ar_ds.source_owner, sc_ds.source_owner, ap_ds.source_owner, 'N诺') as sourceOwner
    from program p
    join college c on p.college_id = c.id
    join school s on c.school_id = s.id
    left join admission_score sc on sc.program_id = p.id
      and sc.year = (
        select max(sc2.year) from admission_score sc2
        where sc2.program_id = p.id and sc2.score_line is not null
      )
    left join admission_plan ap on ap.program_id = p.id and ap.year = sc.year
    left join admission_result ar on ar.program_id = p.id and ar.year = sc.year
    left join program_year_data_quality q on q.program_id = p.id and q.year = sc.year
    left join data_source sc_ds on sc_ds.id = sc.source_id
    left join data_source ap_ds on ap_ds.id = ap.source_id
    left join data_source ar_ds on ar_ds.id = ar.source_id
    where p.status = 'active' and s.status = 'active' and p.is_408 = 1
      and p.study_mode = 'full_time'
      and (sc.score_line is not null or ar.avg_admitted_score is not null)
      and ar.avg_admitted_score &gt;= #{minScore}
      and ar.avg_admitted_score &lt;= #{maxScore}
    <if test="regions != null and regions.size() > 0">
        and s.province in
        <foreach collection="regions" item="region" open="(" separator="," close=")">
            #{region}
        </foreach>
    </if>
    order by abs(cast(ar.avg_admitted_score as signed) - #{estimatedScore}) asc
    limit 300
</select>
```

- [ ] **Step 3: Compile check**

Run: `mvn -pl ruoyi-postgrad -am -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java \
  ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml
git commit -m "feat: add selectForAnalysis mapper for one-shot AI recommendations"
```

---

### Task 2: Candidate Pool Service — `buildAnalysisPool` with Stratification

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiCandidatePoolService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImpl.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImplTest.java`

- [ ] **Step 1: Add method to interface**

In `IAiCandidatePoolService.java`, add:

```java
List<RowMap> buildAnalysisPool(int estimatedScore, List<String> regions);
```

- [ ] **Step 2: Write the failing test**

In `AiCandidatePoolServiceImplTest.java`, add these tests:

```java
@Test
void buildAnalysisPoolStratifiesByGap()
{
    List<RowMap> rows = new ArrayList<>();
    // Mix of safe, steady, and reach schools
    rows.add(row(1L, "保底校A", 278));  // gap = 22 → safe
    rows.add(row(2L, "保底校B", 280));  // gap = 20 → safe
    rows.add(row(3L, "稳妥校A", 290));  // gap = 10 → steady
    rows.add(row(4L, "稳妥校B", 295));  // gap = 5  → steady
    rows.add(row(5L, "冲刺校A", 305));  // gap = -5 → reach
    rows.add(row(6L, "冲刺校B", 310));  // gap = -10 → reach
    rows.add(row(7L, "太难校", 312));   // gap = -12 → skip

    when(recommendationMapper.selectForAnalysis(300, List.of("福建"), 280, 320))
        .thenReturn(rows);

    List<RowMap> pool = service.buildAnalysisPool(300, List.of("福建"));

    // All 3 strata represented, skip gap < -10
    assertTrue(pool.stream().anyMatch(r -> r.get("schoolName").equals("保底校A")));
    assertTrue(pool.stream().anyMatch(r -> r.get("schoolName").equals("稳妥校A")));
    assertTrue(pool.stream().anyMatch(r -> r.get("schoolName").equals("冲刺校B")));
    assertFalse(pool.stream().anyMatch(r -> r.get("schoolName").equals("太难校")));
    assertEquals(6, pool.size());
}

@Test
void buildAnalysisPoolCapsPerStratum()
{
    List<RowMap> rows = new ArrayList<>();
    for (int i = 0; i < 25; i++) rows.add(row((long) (1000 + i), "保底" + i, 280));  // gap=20
    for (int i = 0; i < 25; i++) rows.add(row((long) (2000 + i), "稳妥" + i, 295));  // gap=5
    for (int i = 0; i < 25; i++) rows.add(row((long) (3000 + i), "冲刺" + i, 308));  // gap=-8

    when(recommendationMapper.selectForAnalysis(300, List.of("福建"), 280, 320))
        .thenReturn(rows);

    List<RowMap> pool = service.buildAnalysisPool(300, List.of("福建"));

    // Caps: safe 15, steady 20, reach 15 = 50
    assertEquals(50, pool.size());
    long safeCount = pool.stream().filter(r -> r.get("schoolName").toString().startsWith("保底")).count();
    long steadyCount = pool.stream().filter(r -> r.get("schoolName").toString().startsWith("稳妥")).count();
    long reachCount = pool.stream().filter(r -> r.get("schoolName").toString().startsWith("冲刺")).count();
    assertEquals(15, safeCount);
    assertEquals(20, steadyCount);
    assertEquals(15, reachCount);
}

private RowMap row(long programId, String schoolName, int avgScore)
{
    RowMap row = new RowMap();
    row.put("programId", programId);
    row.put("schoolName", schoolName);
    row.put("avgAdmittedScore", avgScore);
    row.put("schoolTier", "双非");
    row.put("city", "测试市");
    row.put("programName", "计算机科学与技术");
    return row;
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -pl ruoyi-postgrad -Dtest=AiCandidatePoolServiceImplTest#buildAnalysisPoolStratifiesByGap+test test`
Expected: compilation error — `buildAnalysisPool` method not defined

- [ ] **Step 4: Implement `buildAnalysisPool`**

In `AiCandidatePoolServiceImpl.java`, add the method:

```java
private static final int SAFE_LIMIT = 15;
private static final int STEADY_LIMIT = 20;
private static final int REACH_LIMIT = 15;

@Override
public List<RowMap> buildAnalysisPool(int estimatedScore, List<String> regions)
{
    int minScore = estimatedScore - 20;
    int maxScore = estimatedScore + 20;
    if (regions == null) regions = Collections.emptyList();

    List<RowMap> all = recommendationMapper.selectForAnalysis(
        estimatedScore, regions, minScore, maxScore);
    if (all == null || all.isEmpty())
    {
        return Collections.emptyList();
    }

    List<RowMap> safe = new ArrayList<>();
    List<RowMap> steady = new ArrayList<>();
    List<RowMap> reach = new ArrayList<>();

    for (RowMap row : all)
    {
        int gap = estimatedScore - scoreAvg(row);
        if (gap >= 15) safe.add(row);
        else if (gap >= 5) steady.add(row);
        else if (gap >= -10) reach.add(row);
        // gap < -10: skip
    }

    List<RowMap> result = new ArrayList<>(SAFE_LIMIT + STEADY_LIMIT + REACH_LIMIT);
    result.addAll(limitByProximity(safe, estimatedScore, SAFE_LIMIT));
    result.addAll(limitByProximity(steady, estimatedScore, STEADY_LIMIT));
    result.addAll(limitByProximity(reach, estimatedScore, REACH_LIMIT));

    // Sort final result by proximity
    result.sort((a, b) -> Integer.compare(
        Math.abs(scoreAvg(a) - estimatedScore),
        Math.abs(scoreAvg(b) - estimatedScore)));
    return result;
}

private List<RowMap> limitByProximity(List<RowMap> rows, int estimatedScore, int limit)
{
    if (rows.size() <= limit) return new ArrayList<>(rows);
    rows.sort((a, b) -> Integer.compare(
        Math.abs(scoreAvg(a) - estimatedScore),
        Math.abs(scoreAvg(b) - estimatedScore)));
    return new ArrayList<>(rows.subList(0, limit));
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -pl ruoyi-postgrad -Dtest=AiCandidatePoolServiceImplTest test`
Expected: all tests pass (existing 7 + new 2 = 9)

- [ ] **Step 6: Compile check**

Run: `mvn -pl ruoyi-postgrad -am -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiCandidatePoolService.java \
  ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImpl.java \
  ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImplTest.java
git commit -m "feat: add buildAnalysisPool with gap-based stratification"
```

---

### Task 3: Service — `analyze()` Method

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiRecommendationService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`

- [ ] **Step 1: Add method to interface**

In `IAiRecommendationService.java`, add:

```java
Map<String, Object> analyze(Long userId);
```

- [ ] **Step 2: Implement `analyze()` in ServiceImpl**

In `AiRecommendationServiceImpl.java`, add the method after the existing `generateReport` method:

```java
@Override
public Map<String, Object> analyze(Long userId)
{
    // 1. Load profile
    Map<String, Object> profile = loadUserProfile(userId);
    int estimatedScore = getEstimatedScore(Collections.emptyMap(), profile);
    List<String> regions = parseRegionsForAnalysis(
        formatProfileField(profile, "targetRegions", "不限"));

    // 2. Query and stratify schools
    List<RowMap> pool = aiCandidatePoolService.buildAnalysisPool(estimatedScore, regions);

    // 3. Serialize pool data for Redis (full fields for injectFullData)
    List<Map<String, Object>> poolList = new ArrayList<>();
    for (RowMap row : pool)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("programId", row.get("programId"));
        item.put("schoolName", row.get("schoolName"));
        item.put("schoolTier", row.get("schoolTier"));
        item.put("city", row.get("city"));
        item.put("province", row.get("province"));
        item.put("collegeName", row.get("collegeName"));
        item.put("programName", row.get("programName"));
        item.put("degreeType", row.get("degreeType"));
        item.put("scoreLine", row.get("scoreLine"));
        item.put("avgAdmittedScore", row.get("avgAdmittedScore"));
        item.put("admissionLow", row.get("admissionLow"));
        item.put("admissionHigh", row.get("admissionHigh"));
        item.put("planCount", row.get("planCount"));
        item.put("admittedCount", row.get("admittedCount"));
        item.put("retestCount", row.get("retestCount"));
        item.put("dataYear", row.get("dataYear"));
        item.put("dataCompleteness", row.get("dataCompleteness"));
        item.put("sourceUrl", row.get("sourceUrl"));
        item.put("sourceOwner", row.get("sourceOwner"));
        Object avgObj = row.get("avgAdmittedScore");
        item.put("gap", avgObj instanceof Number n ? estimatedScore - n.intValue() : 0);
        poolList.add(item);
    }
    String poolJson = JSON.toJSONString(poolList);

    // 4. Insert PENDING log
    RecommendationLog log = new RecommendationLog();
    log.setUserId(userId);
    log.setResultJson("{\"status\":\"PENDING\"}");
    logMapper.insertRecommendationLog(log);
    long reportId = log.getId();

    // 5. Store pool in Redis
    redisTemplate.opsForValue().set(
        "ai:analyze:pool:" + reportId, poolJson, 1, TimeUnit.HOURS);

    // 6. Send MQ message (lightweight)
    if (rabbitTemplate != null)
    {
        Map<String, Object> mqMsg = new LinkedHashMap<>();
        mqMsg.put("reportId", reportId);
        mqMsg.put("estimatedScore", estimatedScore);
        mqMsg.put("userId", userId);
        mqMsg.put("mode", "analyze");
        rabbitTemplate.convertAndSend(RabbitMQConfig.AI_REPORT_QUEUE, mqMsg);
    }

    // 7. Return reportId
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("reportId", reportId);
    result.put("msg", "报告生成中，请稍候");
    return result;
}

private List<String> parseRegionsForAnalysis(String targetRegions)
{
    if (targetRegions == null || targetRegions.isEmpty() || "不限".equals(targetRegions))
        return Collections.emptyList();
    try
    {
        return JSON.parseArray(targetRegions, String.class);
    }
    catch (Exception e)
    {
        return Collections.emptyList();
    }
}
```

- [ ] **Step 3: Compile check**

Run: `mvn -pl ruoyi-postgrad -am -DskipTests compile`
Expected: BUILD SUCCESS. If `RabbitMQConfig.AI_REPORT_QUEUE` or `RecommendationLog` or `RabbitTemplate` not available, add the needed imports:

```java
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.domain.RecommendationLog;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
```

- [ ] **Step 4: Commit**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiRecommendationService.java \
  ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java
git commit -m "feat: implement analyze() — one-shot recommendation via MQ"
```

---

### Task 4: Controller — `POST /analyze` Endpoint

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppAiRecommendationController.java`

- [ ] **Step 1: Add endpoint**

In `AppAiRecommendationController.java`, after the `/resume` endpoint, add:

```java
@PostMapping("/analyze")
public AjaxResult analyze() {
    AppLoginUser user = getCurrentAppUser();
    if (user == null) return AjaxResult.error("未登录");
    try {
        return AjaxResult.success(aiService.analyze(user.getUserId()));
    } catch (Exception e) {
        return AjaxResult.error(e.getMessage());
    }
}
```

- [ ] **Step 2: Compile check**

Run: `mvn -pl ruoyi-admin -am -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppAiRecommendationController.java
git commit -m "feat: add POST /analyze endpoint for one-shot AI recommendations"
```

---

### Task 5: Consumer — `mode: "analyze"` + `injectFullData`

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java`

- [ ] **Step 1: Modify `onMessage` to support analyze mode**

Replace the body of `onMessage(Map<String, Object> msg)` in `AiReportConsumer.java`. The old method reads conversation from Redis and builds a conversation-based prompt. Add branching at the top:

```java
@RabbitListener(queues = RabbitMQConfig.AI_REPORT_QUEUE, concurrency = "1")
public void onMessage(Map<String, Object> msg) {
    Long reportId = ((Number) msg.get("reportId")).longValue();
    int estimatedScore = ((Number) msg.get("estimatedScore")).intValue();
    String mode = (String) msg.getOrDefault("mode", "conversation");

    try {
        if ("analyze".equals(mode)) {
            handleAnalyzeMessage(reportId, estimatedScore, msg);
        } else {
            handleConversationMessage(reportId, estimatedScore, msg);
        }
    } catch (Exception e) {
        redisTemplate.opsForValue().set("ai:report:" + reportId,
            "{\"error\": \"" + e.getMessage() + "\"}", 7, TimeUnit.DAYS);
    }
}
```

- [ ] **Step 2: Extract existing conversation logic to `handleConversationMessage`**

Wrap the existing `onMessage` body (reading conversation from Redis, stripping tail, building conversation-based prompt) into:

```java
private void handleConversationMessage(Long reportId, int estimatedScore, Map<String, Object> msg) {
    // Existing logic: read conv from Redis, strip tail, build prompt, parse, inject
    String conversationId = (String) msg.get("conversationId");
    String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
    if (convJson == null) {
        redisTemplate.opsForValue().set("ai:report:" + reportId,
            "{\"error\": \"对话已过期\"}", 7, TimeUnit.DAYS);
        return;
    }
    String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);

    ChatModel chatModel = OpenAiChatModel.builder()
        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-max")
        .build();

    String cleanedConvJson = stripTailExchange(convJson);
    String reportPrompt = buildReportPrompt(cleanedConvJson, poolJson != null ? poolJson : "[]");
    JSONObject reportJson = parseReportJson(chatModel, reportPrompt, poolJson);

    injectFullData(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");

    String resultJsonStr = reportJson.toJSONString();
    redisTemplate.opsForValue().set("ai:report:" + reportId, resultJsonStr, 7, TimeUnit.DAYS);
    try {
        logMapper.updateReportResult(reportId, resultJsonStr);
    } catch (Exception dbEx) { /* best-effort */ }
}
```

- [ ] **Step 3: Add `handleAnalyzeMessage`**

```java
private void handleAnalyzeMessage(Long reportId, int estimatedScore, Map<String, Object> msg) {
    Long userId = ((Number) msg.get("userId")).longValue();

    // 1. Read pool from Redis
    String poolJson = redisTemplate.opsForValue().get("ai:analyze:pool:" + reportId);
    if (poolJson == null || poolJson.isEmpty()) {
        redisTemplate.opsForValue().set("ai:report:" + reportId,
            "{\"error\": \"候选学校数据已过期，请重新发起快速推荐\"}", 7, TimeUnit.DAYS);
        return;
    }

    // 2. Load profile for prompt
    Map<String, Object> profile = loadProfileForAnalysis(userId);

    // 3. Build prompt with full school data
    String prompt = buildAnalysisPrompt(poolJson, estimatedScore, profile);

    // 4. Call AI
    ChatModel chatModel = OpenAiChatModel.builder()
        .baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-max")
        .build();

    JSONObject reportJson = parseReportJson(chatModel, prompt, poolJson);

    // 5. Inject full data
    injectFullData(reportJson, estimatedScore, poolJson);

    // 6. Save
    String resultJsonStr = reportJson.toJSONString();
    redisTemplate.opsForValue().set("ai:report:" + reportId, resultJsonStr, 7, TimeUnit.DAYS);
    try {
        logMapper.updateReportResult(reportId, resultJsonStr);
    } catch (Exception dbEx) { /* best-effort */ }

    // 7. Clean up analysis pool
    redisTemplate.delete("ai:analyze:pool:" + reportId);
}
```

- [ ] **Step 4: Add `buildAnalysisPrompt` and `loadProfileForAnalysis`**

```java
private Map<String, Object> loadProfileForAnalysis(Long userId) {
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("undergradTier", "双非");
    profile.put("isCrossMajor", "否");
    profile.put("targetRegions", "不限");
    try {
        // Use JdbcTemplate for simplicity — read-only profile lookup
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT estimated_score, undergrad_tier, is_cross_major, target_regions " +
            "FROM user_profile WHERE user_id = ?", userId);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            profile.put("undergradTier", row.getOrDefault("undergrad_tier", "双非"));
            profile.put("isCrossMajor", "1".equals(String.valueOf(row.getOrDefault("is_cross_major", "0"))) ? "是" : "否");
            Object regions = row.get("target_regions");
            profile.put("targetRegions", regions != null ? String.valueOf(regions) : "不限");
        }
    } catch (Exception e) {
        log.warn("[Report-Consumer] Failed to load profile for userId={}, using defaults", userId);
    }
    return profile;
}

@SuppressWarnings({"unchecked", "rawtypes"})
private String buildAnalysisPrompt(String poolJson, int estimatedScore, Map<String, Object> profile) {
    StringBuilder sb = new StringBuilder();
    sb.append("你是考研择校顾问。请基于以下用户画像和候选学校数据，直接输出一份择校推荐报告。\n\n");
    sb.append("## 用户画像\n");
    sb.append("- 预估总分: ").append(estimatedScore).append("\n");
    sb.append("- 本科层次: ").append(profile.getOrDefault("undergradTier", "双非")).append("\n");
    sb.append("- 跨考: ").append(profile.getOrDefault("isCrossMajor", "否")).append("\n");
    sb.append("- 目标地区: ").append(profile.getOrDefault("targetRegions", "不限")).append("\n\n");
    sb.append("## 推荐要求\n");
    sb.append("1. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校\n");
    sb.append("2. 学校选择需综合考虑：录取均分、差距、招生人数、报录比、复试线\n");
    sb.append("3. 差距 ≥ 5 分优先稳妥/保底档，差距 ≤ -6 分优先冲刺档\n");
    sb.append("4. 差距 > -5 且 < 5 时可归入稳妥档\n");
    sb.append("5. 不要推荐差距 < -10 分的学校（难度过高）\n");
    sb.append("6. 推荐理由必须引用具体数据（均分、招生人数等），不要只说\"分数合适\"\n\n");
    sb.append("## 候选学校数据\n");
    sb.append("格式: ID | 学校 | 专业 | 层次 | 城市 | 均分 | 差距 | 复试线 | 招生 | 录取 | 报录比 | 数据年份\n\n");

    List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
    int idx = 1;
    for (Map<String, Object> p : pool) {
        sb.append(idx++).append(". ID:").append(p.get("programId"));
        sb.append(" | ").append(p.getOrDefault("schoolName", "?"));
        sb.append(" | ").append(p.getOrDefault("programName", ""));
        sb.append(" | ").append(p.getOrDefault("schoolTier", ""));
        sb.append(" | ").append(p.getOrDefault("city", ""));
        sb.append(" | 均分:").append(p.getOrDefault("avgAdmittedScore", "-"));
        sb.append(" | 差距:").append(formatGap(p.get("gap")));
        sb.append(" | 复试线:").append(p.getOrDefault("scoreLine", "-"));
        sb.append(" | 招生:").append(p.getOrDefault("planCount", "-"));
        sb.append(" | 录取:").append(p.getOrDefault("admittedCount", "-"));
        sb.append(" | 报录比:").append(calcRatio(p.get("planCount"), p.get("admittedCount")));
        sb.append(" | ").append(p.getOrDefault("dataYear", "-")).append("年\n");
    }

    sb.append("\n## 输出格式（严格 JSON）\n");
    sb.append("{\n  \"summary\": \"一句话总结\",\n  \"tiers\": [\n");
    sb.append("    {\"level\": \"reach\", \"label\": \"冲刺档\", \"schools\": [{...}]},\n");
    sb.append("    {\"level\": \"steady\", \"label\": \"稳妥档\", \"schools\": [{...}]},\n");
    sb.append("    {\"level\": \"safe\", \"label\": \"保底档\", \"schools\": [{...}]}\n  ]\n}");
    return sb.toString();
}

private String formatGap(Object gapObj) {
    if (gapObj instanceof Number n) {
        int g = n.intValue();
        return g > 0 ? "+" + g : String.valueOf(g);
    }
    return "-";
}

private String calcRatio(Object planObj, Object admittedObj) {
    if (planObj instanceof Number p && admittedObj instanceof Number a && a.doubleValue() > 0) {
        return String.format("%.1f:1", p.doubleValue() / a.doubleValue());
    }
    return "-";
}
```

- [ ] **Step 5: Replace `injectMatchScores` with `injectFullData`**

Delete the existing `injectMatchScores` method. Replace with `injectFullData`:

```java
@SuppressWarnings({"unchecked", "rawtypes"})
private void injectFullData(JSONObject report, int estimatedScore, String poolJson) {
    List<Map<String, Object>> pool = (List) JSON.parseArray(poolJson, Map.class);
    Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
    for (Map<String, Object> p : pool) {
        Object idObj = p.get("programId");
        long pid = idObj instanceof Number ? ((Number) idObj).longValue()
            : Long.parseLong(String.valueOf(idObj));
        poolMap.put(pid, p);
    }

    JSONArray tiers = report.getJSONArray("tiers");
    if (tiers == null) return;
    for (int i = 0; i < tiers.size(); i++) {
        JSONObject tier = tiers.getJSONObject(i);
        JSONArray schools = tier.getJSONArray("schools");
        if (schools == null) continue;
        String level = tier.getString("level");
        for (int j = 0; j < schools.size(); j++) {
            JSONObject school = schools.getJSONObject(j);
            long pid = school.getLongValue("programId");
            Map<String, Object> stats = poolMap.get(pid);
            if (stats == null) continue;

            injectStat(school, stats, "scoreLine");
            injectStat(school, stats, "avgAdmittedScore");
            injectStat(school, stats, "admissionLow");
            injectStat(school, stats, "admissionHigh");
            injectStat(school, stats, "planCount");
            injectStat(school, stats, "admittedCount");
            injectStat(school, stats, "retestCount");
            injectStat(school, stats, "dataYear");
            injectStat(school, stats, "dataCompleteness");
            injectStat(school, stats, "sourceUrl");
            injectStat(school, stats, "sourceOwner");

            // gap
            Object avgObj = stats.get("avgAdmittedScore");
            if (avgObj instanceof Number n) {
                school.put("gap", estimatedScore - n.intValue());
            }

            // retestRatio
            Object plan = stats.get("planCount");
            Object admitted = stats.get("admittedCount");
            if (plan instanceof Number p && admitted instanceof Number a && a.doubleValue() > 0) {
                school.put("retestRatio", String.format("%.1f:1", p.doubleValue() / a.doubleValue()));
            }

            // matchScore
            if (avgObj instanceof Number n) {
                double gap = Math.abs(estimatedScore - n.doubleValue());
                double weight = "reach".equals(level) ? 0.5 : 0.3;
                school.put("matchScore", (int) Math.max(0, 100 - gap * weight));
            } else {
                school.put("matchScore", 50);
            }
        }
    }
}
```

- [ ] **Step 6: Remove old helper methods if unused**

Delete `buildReportPrompt`, `buildPoolSummary`, `stripTailExchange` if they're no longer referenced outside `handleConversationMessage`. Keep the three-layer defense `parseReportJson` and `ruleBasedFallback` intact.

- [ ] **Step 7: Compile check**

Run: `mvn -pl ruoyi-admin -am -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java
git commit -m "feat: support analyze mode in consumer with injectFullData"
```

---

### Task 6: Frontend — Dual Buttons + API

**Files:**
- Modify: `user-ui/src/views/AiRecommend.vue`
- Modify: `user-ui/src/api/ai.js`

- [ ] **Step 1: Add API function**

In `user-ui/src/api/ai.js`, add:

```js
export function postAiAnalyze() {
  return request({ url: '/app/ai-recommend/analyze', method: 'post' })
}
```

- [ ] **Step 2: Update AiRecommend.vue template**

Replace the intro-actions div (containing the single "开始 AI 推荐" button and "编辑画像" button) with:

```vue
<div class="intro-actions">
  <el-button type="primary" size="large" :disabled="!canStart || analyzing" :loading="analyzing" @click="startAnalyze">
    快速推荐
  </el-button>
  <el-button size="large" :disabled="!canStart || panelOpen" :loading="starting" @click="startAi">
    AI 对话推荐
  </el-button>
  <el-button size="large" @click="router.push('/profile')">
    编辑画像
  </el-button>
</div>
<div class="intro-modes">
  <span class="mode-hint fast">快速推荐：基于画像+录取数据，一键生成完整报告（约10-30秒）</span>
  <span class="mode-hint chat">AI 对话推荐：与AI多轮交流，逐步细化需求和偏好</span>
</div>
```

- [ ] **Step 3: Add script logic**

In the `<script setup>` section:

Add the import:
```js
import { postAiAnalyze } from '@/api/ai'
```

Add the state:
```js
const analyzing = ref(false)
```

Add the handler (after `startAi`):
```js
function startAnalyze() {
  if (!canStart.value) {
    ElMessage.warning('请先填写预计初试总分，AI 才能判断分数区间')
    return
  }
  analyzing.value = true
  postAiAnalyze().then(res => {
    const data = res.data || {}
    if (data.reportId) {
      router.push('/ai-report/' + data.reportId)
    } else {
      ElMessage.error('启动推荐失败')
    }
  }).catch(() => {
    ElMessage.error('快速推荐启动失败，请稍后重试')
  }).finally(() => {
    analyzing.value = false
  })
}
```

- [ ] **Step 4: Add CSS for mode hints**

In the `<style scoped>` section, add:

```css
.intro-modes {
  display: flex;
  gap: 24px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.mode-hint {
  font-size: 13px;
  color: #7a8aa4;
  display: flex;
  align-items: center;
  gap: 4px;
}
.mode-hint.fast::before { content: "⚡"; }
.mode-hint.chat::before { content: "💬"; }
```

- [ ] **Step 5: Build and verify**

Run: `cd user-ui && npm run build`
Expected: build succeeds

- [ ] **Step 6: Commit**

```bash
git add user-ui/src/views/AiRecommend.vue user-ui/src/api/ai.js
git commit -m "feat: add dual entry buttons for quick analyze and AI chat"
```

---

### Task 7: Full Verification

**Files:**
- None

- [ ] **Step 1: Run backend tests**

Run: `mvn -pl ruoyi-postgrad -Dtest=AiCandidatePoolServiceImplTest test`
Expected: 9 tests pass

- [ ] **Step 2: Run backend compile**

Run: `mvn -pl ruoyi-admin -am -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run frontend build**

Run: `cd user-ui && npm run build`
Expected: build succeeds

- [ ] **Step 4: Manual API smoke test**

With backend running and a valid App-Token:

```bash
curl -s -X POST http://localhost:8080/app/ai-recommend/analyze \
  -H "Authorization: Bearer <APP_TOKEN>"
```

Expected: `{"code":200,"data":{"reportId":NN,"msg":"报告生成中，请稍候"}}`

- [ ] **Step 5: Verify report generation**

Wait 30 seconds, then:

```bash
curl -s http://localhost:8080/app/ai-recommend/report/<reportId> \
  -H "Authorization: Bearer <APP_TOKEN>"
```

Expected: JSON with `tiers` array containing schools with full data fields.

- [ ] **Step 6: Manual frontend smoke test**

- Open `/ai-recommend` page
- Verify two buttons: "快速推荐" and "AI 对话推荐"
- Click "快速推荐" → should redirect to `/ai-report/{id}` with terminal loading
- Wait for report → verify school cards show full data (复试线, 招录比, etc.)

- [ ] **Step 6: Commit if any verification fixes needed**

If verification required any fixes, commit them:
```bash
git add <fixed-files>
git commit -m "fix: stabilize AI analyze flow"
```

---

## Spec Coverage

| Spec Requirement | Task |
|------------------|------|
| New Mapper `selectForAnalysis` with `is_408=1`, ±20 range, region filter | Task 1 |
| `buildAnalysisPool` with gap-based stratification (15/20/15) | Task 2 |
| `analyze()` — profile → schools → stratify → Redis → MQ → reportId | Task 3 |
| Controller `POST /analyze` endpoint | Task 4 |
| Consumer `mode: "analyze"` — reads Redis, builds prompt, injects full data | Task 5 |
| `injectFullData` with all fields + retestRatio + matchScore | Task 5 |
| Frontend dual buttons (快速推荐 / AI对话推荐) | Task 6 |
| Existing endpoints preserved unchanged | All tasks (no modification to start/chat/generate-report) |
| No new MQ Queue | Task 3 (reuses `ai.report.queue`) |
| No new Redis key convention for reports | Task 5 (reuses `ai:report:{id}`) |
| Pool in Redis `ai:analyze:pool:{id}` with 1h TTL | Task 3 |
| MQ message lightweight (<200 bytes) | Task 3 |
