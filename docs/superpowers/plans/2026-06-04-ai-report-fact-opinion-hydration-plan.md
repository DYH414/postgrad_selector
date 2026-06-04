# AI Report Fact Opinion Hydration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make AI report generation store database-backed facts and AI-authored opinions separately, so report cards are complete and consistent with screening results.

**Architecture:** The backend will treat AI output as opinion-only JSON keyed by `programId`, validate IDs against the candidate pool, hydrate each selected program from `RecommendationMapper.selectProgramForRecommendation`, and store a complete report snapshot. The frontend will normalize both old and new report shapes, rendering database facts as card data and AI opinion as recommendation copy.

**Tech Stack:** Java 17, Spring/RuoYi, FastJSON2, JUnit 5, Mockito, Vue 2/Element UI, Vite static checks via Node `.mjs` tests.

---

## File Structure

- Modify `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
  - Owns report prompt, parsing, validation, fallback, and the new hydration step.
- Modify `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`
  - Adds focused reflection tests for prompt/schema, hydration, invalid ID filtering, duplicate handling, and fallback hydration.
- Modify `user-ui/src/utils/aiReport.js`
  - Normalizes `school.opinion` into the fields used by existing `AiReport.vue`.
- Add `user-ui/src/utils/aiReport.opinion.test.mjs`
  - Static frontend test for new report shape and legacy compatibility.
- Optionally modify `user-ui/src/views/AiReport.vue`
  - Only if the current template cannot clearly show `opinion.tradeoffs` or `opinion.decision` after normalization.

---

### Task 1: Backend Test Harness For Report Hydration

**Files:**
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Add Mockito support and field injection helpers**

Replace the top of `AiRecommendationServiceImplTest.java` with this structure while keeping the existing `shouldNormalizeReportJudgementAndAction` test body:

```java
package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiRecommendationServiceImplTest {
    private AiRecommendationServiceImpl service;

    @Mock
    private RecommendationMapper recommendationMapper;

    @BeforeEach
    void setUp() throws Exception {
        service = new AiRecommendationServiceImpl();
        setField("recommendationMapper", recommendationMapper);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AiRecommendationServiceImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeHydrate(Map<String, Object> report, int estimatedScore, String poolJson) throws Exception {
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("hydrateReportPrograms", Map.class, int.class, String.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, report, estimatedScore, poolJson);
    }

    private RowMap detailRow(long programId) {
        RowMap row = new RowMap();
        row.put("programId", programId);
        row.put("schoolId", 20L + programId);
        row.put("schoolName", "北京信息科技大学");
        row.put("province", "北京");
        row.put("city", "北京");
        row.put("collegeName", "计算机学院");
        row.put("programName", "计算机科学与技术");
        row.put("programCode", "081200");
        row.put("degreeType", "学硕");
        row.put("examCombo", "11408");
        row.put("schoolTier", "普通本科");
        row.put("scoreLine", 273);
        row.put("avgAdmittedScore", new BigDecimal("295"));
        row.put("admissionLow", 270);
        row.put("admissionHigh", 331);
        row.put("planCount", 33);
        row.put("unifiedExamQuota", 28);
        row.put("dataYear", 2025);
        row.put("dataCompleteness", "C");
        row.put("sourceUrl", "https://example.com/source");
        row.put("sourceOwner", "N诺");
        return row;
    }
}
```

- [ ] **Step 2: Restore the existing normalization test inside the new class**

Paste this test inside the class:

```java
@Test
void shouldNormalizeReportJudgementAndAction() throws Exception {
    Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("normalizeReportItem", Map.class);
    method.setAccessible(true);

    Map<String, Object> item = new LinkedHashMap<>();
    item.put("aiJudgement", "稳妥偏冲刺");
    item.put("verificationStatus", "unknown");

    @SuppressWarnings("unchecked")
    Map<String, Object> normalized = (Map<String, Object>) method.invoke(service, item);

    assertEquals("steady_reach", normalized.get("judgement"));
    assertEquals("稳妥偏冲", normalized.get("judgementLabel"));
    assertEquals("pending", normalized.get("verificationStatus"));
    assertEquals("可作为稳妥偏冲候选，建议核验近年复试与录取波动", normalized.get("recommendedAction"));
}
```

- [ ] **Step 3: Add a failing hydration test**

Paste this test inside the class:

```java
@Test
void shouldHydrateOpinionOnlyReportWithDatabaseFacts() throws Exception {
    when(recommendationMapper.selectProgramForRecommendation(123L)).thenReturn(detailRow(123L));

    Map<String, Object> school = new LinkedHashMap<>();
    school.put("programId", 123);
    school.put("judgement", "steady");
    school.put("risk", "medium");
    school.put("decision", "适合作为主力稳妥候选");
    school.put("reason", "分数匹配，地区符合偏好");
    school.put("pros", List.of("分数匹配度较高"));
    school.put("cons", List.of("数据完整度为 C，需要核验"));
    school.put("tradeoffs", List.of("上岸稳定性优先于学校层次"));

    Map<String, Object> tier = new LinkedHashMap<>();
    tier.put("level", "steady");
    tier.put("label", "稳妥档");
    tier.put("schools", List.of(school));

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("summary", "推荐以稳妥为主");
    report.put("tiers", List.of(tier));

    Map<String, Object> hydrated = invokeHydrate(report, 300, "[{\"programId\":123}]");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tiers = (List<Map<String, Object>>) hydrated.get("tiers");
    @SuppressWarnings("unchecked")
    Map<String, Object> hydratedSchool = ((List<Map<String, Object>>) tiers.get(0).get("schools")).get(0);
    @SuppressWarnings("unchecked")
    Map<String, Object> opinion = (Map<String, Object>) hydratedSchool.get("opinion");

    assertEquals("北京信息科技大学", hydratedSchool.get("schoolName"));
    assertEquals("计算机学院", hydratedSchool.get("collegeName"));
    assertEquals("计算机科学与技术", hydratedSchool.get("programName"));
    assertEquals(295, hydratedSchool.get("avgAdmittedScore"));
    assertEquals(5, hydratedSchool.get("avgScoreGap"));
    assertEquals("270-331", hydratedSchool.get("admissionRange"));
    assertEquals(28, hydratedSchool.get("unifiedExamQuota"));
    assertEquals("适合作为主力稳妥候选", opinion.get("decision"));
    assertEquals("分数匹配，地区符合偏好", opinion.get("reason"));
}
```

- [ ] **Step 4: Run the focused backend test and verify failure**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest#shouldHydrateOpinionOnlyReportWithDatabaseFacts test
```

Expected: FAIL with `NoSuchMethodException: hydrateReportPrograms`.

---

### Task 2: Implement Backend Hydration

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Add helper methods to `AiRecommendationServiceImpl`**

Add these methods near `injectMatchScores`:

```java
@SuppressWarnings({"unchecked", "rawtypes"})
private Map<String, Object> hydrateReportPrograms(Map<String, Object> report, int estimatedScore, String poolJson) {
    Map<String, Object> result = new LinkedHashMap<>(report);
    Map<Long, Map<String, Object>> poolMap = parsePoolMap(poolJson);
    List<Long> invalidIds = new ArrayList<>();
    List<Long> duplicateIds = new ArrayList<>();
    List<Long> missingDetailIds = new ArrayList<>();
    Set<Long> seen = new LinkedHashSet<>();

    List<Map<String, Object>> tiers = (List<Map<String, Object>>) result.get("tiers");
    if (tiers == null) {
        result.put("tiers", Collections.emptyList());
        return result;
    }

    for (Map<String, Object> tier : tiers) {
        List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
        if (schools == null) {
            tier.put("schools", Collections.emptyList());
            continue;
        }
        List<Map<String, Object>> hydratedSchools = new ArrayList<>();
        for (Map<String, Object> school : schools) {
            Long programId = longValue(school.get("programId"));
            if (programId == null || !poolMap.containsKey(programId)) {
                if (programId != null) invalidIds.add(programId);
                continue;
            }
            if (!seen.add(programId)) {
                duplicateIds.add(programId);
                continue;
            }
            Map<String, Object> detail = recommendationMapper.selectProgramForRecommendation(programId);
            if (detail == null) {
                missingDetailIds.add(programId);
                continue;
            }
            hydratedSchools.add(hydratedReportSchool(school, detail, estimatedScore));
        }
        tier.put("schools", hydratedSchools);
    }

    Map<String, Object> meta = new LinkedHashMap<>();
    Object existingMeta = result.get("meta") != null ? result.get("meta") : result.get("metadata");
    if (existingMeta instanceof Map) {
        meta.putAll((Map) existingMeta);
    }
    meta.put("invalidProgramIds", invalidIds);
    meta.put("duplicateProgramIds", duplicateIds);
    meta.put("missingDetailProgramIds", missingDetailIds);
    result.put("meta", meta);
    result.put("metadata", meta);
    return result;
}

@SuppressWarnings({"unchecked", "rawtypes"})
private Map<Long, Map<String, Object>> parsePoolMap(String poolJson) {
    Map<Long, Map<String, Object>> poolMap = new LinkedHashMap<>();
    if (poolJson == null || poolJson.isBlank() || "[]".equals(poolJson.trim())) {
        return poolMap;
    }
    for (Object item : JSON.parseArray(poolJson)) {
        if (!(item instanceof Map)) continue;
        Map<String, Object> row = (Map<String, Object>) item;
        Long programId = longValue(row.get("programId"));
        if (programId != null) poolMap.put(programId, row);
    }
    return poolMap;
}

private Map<String, Object> hydratedReportSchool(Map<String, Object> opinionSource, Map<String, Object> detail, int estimatedScore) {
    Map<String, Object> item = new LinkedHashMap<>();
    putFact(item, detail, "programId");
    putFact(item, detail, "schoolId");
    putFact(item, detail, "schoolName");
    putFact(item, detail, "province");
    putFact(item, detail, "city");
    putFact(item, detail, "collegeName");
    putFact(item, detail, "programName");
    putFact(item, detail, "programCode");
    putFact(item, detail, "degreeType");
    putFact(item, detail, "examCombo");
    putFact(item, detail, "schoolTier");
    putFact(item, detail, "scoreLine");
    putFact(item, detail, "admissionLow");
    putFact(item, detail, "admissionHigh");
    putFact(item, detail, "planCount");
    putFact(item, detail, "unifiedExamQuota");
    putFact(item, detail, "dataYear");
    putFact(item, detail, "dataCompleteness");
    putFact(item, detail, "sourceUrl");
    putFact(item, detail, "sourceOwner");

    Integer avg = integerValue(detail.get("avgAdmittedScore"));
    item.put("avgAdmittedScore", avg);
    item.put("avgScoreGap", avg == null || estimatedScore <= 0 ? null : estimatedScore - avg);
    item.put("admissionRange", admissionRange(detail.get("admissionLow"), detail.get("admissionHigh")));
    item.put("opinion", buildOpinion(opinionSource));
    mirrorOpinionForLegacyFrontend(item);
    return item;
}

private void putFact(Map<String, Object> target, Map<String, Object> source, String key) {
    if (source.containsKey(key)) target.put(key, source.get(key));
}

private Map<String, Object> buildOpinion(Map<String, Object> source) {
    Map<String, Object> opinion = new LinkedHashMap<>();
    opinion.put("judgement", source.getOrDefault("judgement", source.getOrDefault("aiJudgement", "data_insufficient_pending")));
    opinion.put("risk", source.getOrDefault("risk", "medium"));
    opinion.put("decision", source.getOrDefault("decision", ""));
    opinion.put("reason", source.getOrDefault("reason", ""));
    opinion.put("pros", source.getOrDefault("pros", Collections.emptyList()));
    opinion.put("cons", source.getOrDefault("cons", Collections.emptyList()));
    opinion.put("tradeoffs", source.getOrDefault("tradeoffs", Collections.emptyList()));
    opinion.put("recommendedAction", source.getOrDefault("recommendedAction", ""));
    return opinion;
}

@SuppressWarnings("unchecked")
private void mirrorOpinionForLegacyFrontend(Map<String, Object> item) {
    Map<String, Object> opinion = (Map<String, Object>) item.get("opinion");
    item.put("judgement", opinion.get("judgement"));
    item.put("risk", opinion.get("risk"));
    item.put("reason", opinion.get("reason"));
    item.put("pros", opinion.get("pros"));
    item.put("cons", opinion.get("cons"));
    item.put("tradeoffs", opinion.get("tradeoffs"));
    item.put("recommendedAction", opinion.get("recommendedAction"));
}

private Long longValue(Object value) {
    if (value instanceof Number n) return n.longValue();
    if (value == null) return null;
    try {
        return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException e) {
        return null;
    }
}

private Integer integerValue(Object value) {
    if (value instanceof Number n) return n.intValue();
    if (value == null) return null;
    try {
        return new BigDecimal(String.valueOf(value)).intValue();
    } catch (NumberFormatException e) {
        return null;
    }
}

private String admissionRange(Object low, Object high) {
    Integer lowValue = integerValue(low);
    Integer highValue = integerValue(high);
    if (lowValue == null && highValue == null) return null;
    if (lowValue == null) return String.valueOf(highValue);
    if (highValue == null) return String.valueOf(lowValue);
    return lowValue + "-" + highValue;
}
```

- [ ] **Step 2: Import missing collection classes**

Ensure `AiRecommendationServiceImpl.java` imports these classes if not already present:

```java
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
```

- [ ] **Step 3: Replace the report generation enrichment call**

In `generateReport`, replace:

```java
injectMatchScores(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");
Map<String, Object> validated = validateAndNormalizeReport(reportJson, AiRecommendationTools.currentTrace());
```

with:

```java
Map<String, Object> hydrated = hydrateReportPrograms(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");
Map<String, Object> validated = validateAndNormalizeReport(hydrated, AiRecommendationTools.currentTrace());
```

- [ ] **Step 4: Run the focused backend test**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest#shouldHydrateOpinionOnlyReportWithDatabaseFacts test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java
git commit -m "feat(ai-report): hydrate report facts from program ids"
```

---

### Task 3: Backend Validation For Invalid And Duplicate Program IDs

**Files:**
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`
- Modify if needed: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`

- [ ] **Step 1: Add invalid ID and duplicate ID tests**

Paste these tests into `AiRecommendationServiceImplTest.java`:

```java
@Test
void shouldDropProgramIdsOutsideCandidatePool() throws Exception {
    Map<String, Object> school = new LinkedHashMap<>();
    school.put("programId", 999);
    school.put("reason", "池外 ID 不应保留");

    Map<String, Object> tier = new LinkedHashMap<>();
    tier.put("level", "steady");
    tier.put("schools", List.of(school));

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("tiers", List.of(tier));

    Map<String, Object> hydrated = invokeHydrate(report, 300, "[{\"programId\":123}]");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tiers = (List<Map<String, Object>>) hydrated.get("tiers");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> schools = (List<Map<String, Object>>) tiers.get(0).get("schools");
    @SuppressWarnings("unchecked")
    Map<String, Object> meta = (Map<String, Object>) hydrated.get("meta");

    assertTrue(schools.isEmpty());
    assertEquals(List.of(999L), meta.get("invalidProgramIds"));
}

@Test
void shouldKeepFirstDuplicateProgramIdOnly() throws Exception {
    when(recommendationMapper.selectProgramForRecommendation(123L)).thenReturn(detailRow(123L));

    Map<String, Object> first = new LinkedHashMap<>();
    first.put("programId", 123);
    first.put("reason", "第一次出现");

    Map<String, Object> duplicate = new LinkedHashMap<>();
    duplicate.put("programId", 123);
    duplicate.put("reason", "重复出现");

    Map<String, Object> tier = new LinkedHashMap<>();
    tier.put("level", "steady");
    tier.put("schools", List.of(first, duplicate));

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("tiers", List.of(tier));

    Map<String, Object> hydrated = invokeHydrate(report, 300, "[{\"programId\":123}]");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tiers = (List<Map<String, Object>>) hydrated.get("tiers");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> schools = (List<Map<String, Object>>) tiers.get(0).get("schools");
    @SuppressWarnings("unchecked")
    Map<String, Object> meta = (Map<String, Object>) hydrated.get("meta");

    assertEquals(1, schools.size());
    assertEquals(List.of(123L), meta.get("duplicateProgramIds"));
}
```

- [ ] **Step 2: Run the new tests**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest#shouldDropProgramIdsOutsideCandidatePool,AiRecommendationServiceImplTest#shouldKeepFirstDuplicateProgramIdOnly test
```

Expected: PASS. If either test fails, adjust only `hydrateReportPrograms` filtering and `meta` recording until both pass.

- [ ] **Step 3: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java
git commit -m "test(ai-report): validate hydrated report ids"
```

---

### Task 4: Make The AI Report Prompt Opinion-Only

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Add a prompt contract test**

Paste this test into `AiRecommendationServiceImplTest.java`:

```java
@Test
void shouldAskAiForOpinionOnlyReportJson() throws Exception {
    Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("buildReportPrompt", String.class, String.class);
    method.setAccessible(true);

    String prompt = (String) method.invoke(service, "[]", "[{\"programId\":123,\"schoolName\":\"北京信息科技大学\"}]");

    assertTrue(prompt.contains("AI 只输出观点字段"));
    assertTrue(prompt.contains("\"programId\""));
    assertTrue(prompt.contains("\"decision\""));
    assertTrue(prompt.contains("\"tradeoffs\""));
    assertTrue(prompt.contains("不要输出 schoolName"));
    assertFalse(prompt.contains("\"schoolName\":\"...\""));
}
```

- [ ] **Step 2: Run the prompt contract test and verify failure**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest#shouldAskAiForOpinionOnlyReportJson test
```

Expected: FAIL because the current prompt example still asks for `schoolName` and `programName`.

- [ ] **Step 3: Update `buildReportPrompt`**

Replace the output-format section in `buildReportPrompt` with this wording:

```java
            ## 要求
            1. 从上面的候选列表中选学校，不要推荐列表之外的学校
            2. programId 必须与候选列表中的 ID 一致
            3. 按冲刺/稳妥/保底三档推荐，每档 1-3 所学校
            4. AI 只输出观点字段，事实字段由后端数据库补全
            5. 不要输出 schoolName、collegeName、programName、分数、招生人数等事实字段

            ## 输出格式（严格 JSON）
            {
              "summary": "一句话总结本次择校策略",
              "tiers": [
                {
                  "level": "reach",
                  "label": "冲刺档",
                  "schools": [
                    {
                      "programId": 1,
                      "judgement": "small_reach",
                      "risk": "high",
                      "decision": "适合作为冲刺候选",
                      "reason": "结合用户画像和候选事实后的推荐理由",
                      "pros": ["优势1", "优势2"],
                      "cons": ["风险1"],
                      "tradeoffs": ["取舍说明"],
                      "recommendedAction": "下一步行动建议"
                    }
                  ]
                },
                {"level": "steady", "label": "稳妥档", "schools": []},
                {"level": "safe", "label": "保底档", "schools": []}
              ]
            }
```

- [ ] **Step 4: Enrich `buildPoolSummary` with facts useful for judgement**

In `buildPoolSummary`, after city and before average score, append the fields AI should use as evidence:

```java
                sb.append(" | 学院:").append(p.getOrDefault("collegeName", ""));
                sb.append(" | 地区:").append(p.getOrDefault("province", p.getOrDefault("city", "")));
                sb.append(" | 考试:").append(p.getOrDefault("examCombo", ""));
                sb.append(" | 最低录取:").append(p.getOrDefault("admissionLow", ""));
                sb.append(" | 录取区间:").append(p.getOrDefault("admissionLow", "")).append("-").append(p.getOrDefault("admissionHigh", ""));
                sb.append(" | 招生:").append(p.getOrDefault("unifiedExamQuota", p.getOrDefault("planCount", "")));
                sb.append(" | 完整度:").append(p.getOrDefault("dataCompleteness", ""));
```

- [ ] **Step 5: Run prompt and hydration tests**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java
git commit -m "feat(ai-report): request opinion-only AI report output"
```

---

### Task 5: Hydrate Rule-Based Fallback Reports

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Add fallback hydration test**

Paste this test into `AiRecommendationServiceImplTest.java`:

```java
@Test
void shouldHydrateRuleBasedFallbackItems() throws Exception {
    when(recommendationMapper.selectProgramForRecommendation(123L)).thenReturn(detailRow(123L));

    Method fallbackMethod = AiRecommendationServiceImpl.class.getDeclaredMethod("ruleBasedFallback", String.class);
    fallbackMethod.setAccessible(true);

    @SuppressWarnings("unchecked")
    Map<String, Object> fallback = (Map<String, Object>) fallbackMethod.invoke(service,
        "[{\"programId\":123,\"schoolName\":\"临时名称\",\"programName\":\"临时专业\",\"avgAdmittedScore\":295}]");

    Map<String, Object> hydrated = invokeHydrate(fallback, 300, "[{\"programId\":123}]");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> tiers = (List<Map<String, Object>>) hydrated.get("tiers");
    @SuppressWarnings("unchecked")
    Map<String, Object> school = ((List<Map<String, Object>>) tiers.get(1).get("schools")).get(0);

    assertEquals("计算机学院", school.get("collegeName"));
    assertNotNull(school.get("opinion"));
}
```

- [ ] **Step 2: Run the fallback test**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest#shouldHydrateRuleBasedFallbackItems test
```

Expected: PASS after Task 2. If it fails because the fallback puts the item in a different tier, inspect fallback tier construction and update the test index to the tier containing one school.

- [ ] **Step 3: Simplify fallback output to opinion-compatible fields**

In `ruleBasedFallback`, keep `programId`, `judgement`, `risk`, `reason`, `pros`, `cons`, and `recommendedAction`. Do not rely on fallback `schoolName` or `programName` for final display because hydration supplies them.

Use this item shape inside fallback:

```java
school.put("programId", p.get("programId"));
school.put("judgement", level.equals("safe") ? "safe" : level.equals("reach") ? "small_reach" : "steady");
school.put("risk", level.equals("reach") ? "high" : level.equals("safe") ? "low" : "medium");
school.put("decision", level.equals("reach") ? "适合作为冲刺候选" : level.equals("safe") ? "适合作为保底候选" : "适合作为稳妥候选");
school.put("reason", "基于候选池数据自动生成的兜底推荐");
school.put("pros", Collections.emptyList());
school.put("cons", Collections.emptyList());
school.put("tradeoffs", Collections.emptyList());
school.put("recommendedAction", "建议核验院校官网后再加入最终备选");
```

- [ ] **Step 4: Run all backend AI report tests**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java
git commit -m "feat(ai-report): align fallback with hydrated report schema"
```

---

### Task 6: Frontend Normalization For Opinion Reports

**Files:**
- Modify: `user-ui/src/utils/aiReport.js`
- Add: `user-ui/src/utils/aiReport.opinion.test.mjs`

- [ ] **Step 1: Add frontend opinion normalization test**

Create `user-ui/src/utils/aiReport.opinion.test.mjs`:

```js
import assert from 'node:assert/strict'
import { normalizeAiReport } from './aiReport.js'

const report = normalizeAiReport({
  summary: '推荐以稳妥为主',
  tiers: [{
    level: 'steady',
    label: '稳妥档',
    schools: [{
      programId: 123,
      schoolName: '北京信息科技大学',
      collegeName: '计算机学院',
      programName: '计算机科学与技术',
      avgAdmittedScore: 295,
      avgScoreGap: 5,
      admissionLow: 270,
      admissionHigh: 331,
      opinion: {
        judgement: 'steady',
        risk: 'medium',
        decision: '适合作为主力稳妥候选',
        reason: '分数匹配，地区符合偏好',
        pros: ['分数匹配度较高'],
        cons: ['数据完整度为 C，需要核验'],
        tradeoffs: ['上岸稳定性优先于学校层次'],
        recommendedAction: '加入备选并核验官网'
      }
    }]
  }]
})

const school = report.tiers[0].schools[0]

assert.equal(school.collegeName, '计算机学院')
assert.equal(school.judgement, 'steady')
assert.equal(school.risk, 'medium')
assert.deepEqual(school.evidence, ['分数匹配，地区符合偏好', '分数匹配度较高'])
assert.deepEqual(school.risks, ['数据完整度为 C，需要核验'])
assert.deepEqual(school.tradeoffs, ['上岸稳定性优先于学校层次'])
assert.equal(school.recommendedAction, '加入备选并核验官网')

const legacy = normalizeAiReport({
  tiers: [{
    level: 'safe',
    schools: [{
      programId: 456,
      schoolName: '旧报告大学',
      collegeName: '',
      programName: '软件工程',
      judgement: 'safe',
      reason: '旧结构理由',
      cons: '旧结构风险'
    }]
  }]
})

assert.equal(legacy.tiers[0].schools[0].judgement, 'safe')
assert.deepEqual(legacy.tiers[0].schools[0].evidence, ['旧结构理由'])
assert.deepEqual(legacy.tiers[0].schools[0].risks, ['旧结构风险'])
```

- [ ] **Step 2: Run the frontend test and verify failure**

Run:

```powershell
node user-ui\src\utils\aiReport.opinion.test.mjs
```

Expected: FAIL because `normalizeSchool` does not merge `opinion` into `evidence`, `risks`, and `recommendedAction`.

- [ ] **Step 3: Update `normalizeSchool`**

In `user-ui/src/utils/aiReport.js`, replace `normalizeSchool` with:

```js
function normalizeSchool(school) {
  const opinion = school.opinion || {}
  const opinionJudgement = opinion.judgement || school.judgement || school.aiJudgement
  const judgement = JUDGEMENT_LABELS[opinionJudgement] ? opinionJudgement : 'data_insufficient_pending'
  const verificationStatus = VERIFICATION_STATUS_LABELS[school.verificationStatus]
    ? school.verificationStatus
    : 'local_data_only'
  const reasonItems = toArray(opinion.reason || school.reason)
  const proItems = toArray(opinion.pros || school.pros)
  const evidence = [...reasonItems, ...proItems]
  const risks = toArray(opinion.cons || opinion.risks || school.risks || school.cons)
  const avgScoreGap = school.avgScoreGap ?? school.gap ?? null

  return {
    ...school,
    opinion,
    judgement,
    risk: opinion.risk || school.risk || 'medium',
    decision: opinion.decision || school.decision || '',
    judgementLabel: school.judgementLabel || JUDGEMENT_LABELS[judgement],
    verificationStatus,
    verificationStatusLabel: VERIFICATION_STATUS_LABELS[verificationStatus],
    evidence,
    risks,
    tradeoffs: toArray(opinion.tradeoffs || school.tradeoffs),
    avgScoreGap,
    recommendedAction: opinion.recommendedAction || school.recommendedAction || ''
  }
}
```

- [ ] **Step 4: Run frontend normalization tests**

Run:

```powershell
node user-ui\src\utils\aiReport.opinion.test.mjs
node user-ui\src\views\AiReport.cards.test.mjs
node user-ui\src\views\AiReport.loading.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add user-ui/src/utils/aiReport.js user-ui/src/utils/aiReport.opinion.test.mjs
git commit -m "feat(ai-report): normalize hydrated opinion reports"
```

---

### Task 7: End-To-End Verification And Cleanup

**Files:**
- Verify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Verify: `user-ui/src/utils/aiReport.js`
- Verify: `user-ui/src/views/AiReport.vue`

- [ ] **Step 1: Run backend module tests**

Run:

```powershell
mvn -pl ruoyi-postgrad test
```

Expected: PASS.

- [ ] **Step 2: Run frontend static tests**

Run:

```powershell
node user-ui\src\utils\aiReport.opinion.test.mjs
node user-ui\src\views\AiReport.cards.test.mjs
node user-ui\src\views\AiReport.loading.test.mjs
node user-ui\src\views\Results.backup.test.mjs
```

Expected: PASS.

- [ ] **Step 3: Build the frontend**

Run:

```powershell
cd user-ui
npm run build
```

Expected: build completes. Existing Vite chunk-size or CommonJS warnings are acceptable if no new errors appear.

- [ ] **Step 4: Inspect working tree**

Run:

```powershell
git status --short
```

Expected: only intentional files remain modified. Existing unrelated UI changes from earlier work may still be present; do not revert them.

- [ ] **Step 5: Final commit if verification required small fixes**

If Step 1, 2, or 3 required small fixes, commit only the files changed for those fixes:

```powershell
git add <fixed-file-1> <fixed-file-2>
git commit -m "fix(ai-report): stabilize hydrated report verification"
```

---

## Self-Review

- Spec coverage: The plan implements backend fact hydration, opinion-only AI output, ID validation, fallback handling, frontend opinion normalization, and verification.
- Placeholder scan: The plan contains exact files, commands, and code snippets for each code-changing task.
- Type consistency: The schema uses `programId`, `opinion`, `judgement`, `risk`, `decision`, `reason`, `pros`, `cons`, `tradeoffs`, and `recommendedAction` consistently across backend and frontend.
