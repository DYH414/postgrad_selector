# AI Agent Recommendation Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Phase 1 local-database AI recommendation agent: broad candidate pool, database tools, tool traces, judgement/evidence report schema, backend validation, and frontend report rendering.

**Architecture:** Keep local database data as the recommendation source of truth. Add small backend support classes for report enums, action templates, tool budgets, trace recording, and report validation, then wire them into the existing AI analyze/report flow. Frontend keeps legacy report support but renders new reports with judgement, evidence, risks, verification status, notices, and source links.

**Tech Stack:** Java 17, Spring Boot, MyBatis, Redis via `StringRedisTemplate`, LangChain4j tools, FastJSON2, JUnit 5, Mockito, Vue 3, Element Plus.

---

## Scope

This plan implements Phase 1 only.

Included:

- Broad 300-500 local working candidate pool.
- Local database tools: search, detail, compare, expand.
- Verification interface stub and schema fields, with no internet provider yet.
- Tool-call trace and configurable budgets.
- Report validation, judgement enum mapping, action templates, and same-school sorting.
- New AI report frontend layout and legacy report fallback.

Excluded:

- Real internet search provider.
- Admin pending-verification console.
- Full production observability dashboards.

## File Structure

Backend create:

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiReportSupport.java`
  - Defines judgement/status constants, enum mapping, labels, action templates, same-school comparator helpers.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolBudget.java`
  - Holds per-report and per-turn budget counters.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolTrace.java`
  - Records tool calls, program IDs inspected, verification calls, removed item count, and exploration-limited state.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiVerificationService.java`
  - Verification interface used by `verifyOfficialInfo`.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/NoopAiVerificationServiceImpl.java`
  - Phase 1 stub returning `local_data_only` or `pending`.

Backend modify:

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiCandidatePoolService.java`
  - Add `buildAgentPool(...)` and `expandAgentPool(...)`.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImpl.java`
  - Build 300-500 working pools and dedupe expanded pools.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java`
  - Add mapper methods for agent pool search.
- `ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml`
  - Add agent-pool SQL with broader score range and evidence filters.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/tool/AiRecommendationTools.java`
  - Add budgeted tool calls, trace recording, expansion, and verification stub call.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
  - Use agent pool for analyze, generate prompt, validate report, inject support fields, persist trace metadata.
- `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java`
  - Apply the same prompt/report validation path for async analyze reports.

Backend tests:

- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiReportSupportTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiToolBudgetTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImplTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/tool/AiRecommendationToolsTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

Frontend modify:

- `user-ui/src/views/AiReport.vue`
  - Render new report schema, notices, legacy fallback, deterministic same-school primary card sorting.

Frontend optional helper:

- `user-ui/src/utils/aiReport.js`
  - Normalizes new and legacy AI report data for `AiReport.vue`.

---

### Task 1: Backend Report Contract Helpers

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiReportSupport.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiReportSupportTest.java`

- [ ] **Step 1: Write failing tests for judgement/status mapping and action templates**

Create `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiReportSupportTest.java`:

```java
package com.ruoyi.postgrad.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiReportSupportTest {
    @Test
    void mapsJudgementTextToLegalEnum() {
        assertEquals("steady_reach", AiReportSupport.normalizeJudgement("稳妥偏冲刺"));
        assertEquals("small_reach", AiReportSupport.normalizeJudgement("小冲一下"));
        assertEquals("data_insufficient_pending", AiReportSupport.normalizeJudgement("无法判断"));
    }

    @Test
    void mapsVerificationStatusToLegalEnum() {
        assertEquals("official", AiReportSupport.normalizeVerificationStatus("official"));
        assertEquals("third_party", AiReportSupport.normalizeVerificationStatus("third_party_only"));
        assertEquals("pending", AiReportSupport.normalizeVerificationStatus("unknown-source"));
    }

    @Test
    void recommendedActionUsesTemplate() {
        assertEquals("可作为稳妥候选，建议优先核验官网招生计划",
            AiReportSupport.recommendedAction("steady", "local_data_only"));
        assertEquals("数据不足，先放入待核验池，不作为主推荐",
            AiReportSupport.recommendedAction("data_insufficient_pending", "official"));
    }

    @Test
    void directionComparatorPrefersSteadyThenCompletenessThenGap() {
        Map<String, Object> steadyB = row("steady", "B", -8, 2024, "软件学院", "软件工程");
        Map<String, Object> safeA = row("safe", "A", 30, 2025, "计算机学院", "计算机技术");
        Map<String, Object> steadyA = row("steady", "A", 12, 2023, "人工智能学院", "人工智能");

        assertTrue(AiReportSupport.directionComparator().compare(steadyA, steadyB) < 0);
        assertTrue(AiReportSupport.directionComparator().compare(steadyB, safeA) < 0);
    }

    private Map<String, Object> row(String judgement, String completeness, int gap, int year,
        String college, String program) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("judgement", judgement);
        row.put("dataCompleteness", completeness);
        row.put("avgScoreGap", gap);
        row.put("dataYear", year);
        row.put("collegeName", college);
        row.put("programName", program);
        return row;
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiReportSupportTest test
```

Expected: FAIL because `AiReportSupport` does not exist.

- [ ] **Step 3: Create `AiReportSupport`**

Create `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiReportSupport.java`:

```java
package com.ruoyi.postgrad.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class AiReportSupport {
    public static final String JUDGEMENT_SAFE = "safe";
    public static final String JUDGEMENT_STEADY = "steady";
    public static final String JUDGEMENT_STEADY_REACH = "steady_reach";
    public static final String JUDGEMENT_SMALL_REACH = "small_reach";
    public static final String JUDGEMENT_HIGH_RISK_REACH = "high_risk_reach";
    public static final String JUDGEMENT_DATA_INSUFFICIENT_PENDING = "data_insufficient_pending";

    public static final String STATUS_OFFICIAL = "official";
    public static final String STATUS_THIRD_PARTY = "third_party";
    public static final String STATUS_LOCAL_DATA_ONLY = "local_data_only";
    public static final String STATUS_VERIFICATION_FAILED = "verification_failed";
    public static final String STATUS_PENDING = "pending";

    private static final List<String> JUDGEMENT_ORDER = List.of(
        JUDGEMENT_STEADY,
        JUDGEMENT_STEADY_REACH,
        JUDGEMENT_SAFE,
        JUDGEMENT_SMALL_REACH,
        JUDGEMENT_HIGH_RISK_REACH,
        JUDGEMENT_DATA_INSUFFICIENT_PENDING
    );

    private AiReportSupport() {}

    public static String normalizeJudgement(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (JUDGEMENT_ORDER.contains(text)) return text;
        if (text.contains("保底")) return JUDGEMENT_SAFE;
        if (text.contains("稳妥偏冲") || text.contains("稳中偏冲")) return JUDGEMENT_STEADY_REACH;
        if (text.contains("稳妥")) return JUDGEMENT_STEADY;
        if (text.contains("小冲")) return JUDGEMENT_SMALL_REACH;
        if (text.contains("高风险") || text.contains("冲刺")) return JUDGEMENT_HIGH_RISK_REACH;
        return JUDGEMENT_DATA_INSUFFICIENT_PENDING;
    }

    public static String judgementLabel(String judgement) {
        return switch (normalizeJudgement(judgement)) {
            case JUDGEMENT_SAFE -> "保底";
            case JUDGEMENT_STEADY -> "稳妥";
            case JUDGEMENT_STEADY_REACH -> "稳妥偏冲";
            case JUDGEMENT_SMALL_REACH -> "小冲";
            case JUDGEMENT_HIGH_RISK_REACH -> "高风险冲刺";
            default -> "数据不足待核验";
        };
    }

    public static String normalizeVerificationStatus(Object raw) {
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (STATUS_OFFICIAL.equals(text)) return STATUS_OFFICIAL;
        if (STATUS_THIRD_PARTY.equals(text) || "third_party_only".equals(text)) return STATUS_THIRD_PARTY;
        if (STATUS_LOCAL_DATA_ONLY.equals(text)) return STATUS_LOCAL_DATA_ONLY;
        if (STATUS_VERIFICATION_FAILED.equals(text)) return STATUS_VERIFICATION_FAILED;
        if (STATUS_PENDING.equals(text)) return STATUS_PENDING;
        return STATUS_PENDING;
    }

    public static String recommendedAction(String judgement, String verificationStatus) {
        String j = normalizeJudgement(judgement);
        String s = normalizeVerificationStatus(verificationStatus);
        if (JUDGEMENT_DATA_INSUFFICIENT_PENDING.equals(j)) {
            return "数据不足，先放入待核验池，不作为主推荐";
        }
        if (JUDGEMENT_HIGH_RISK_REACH.equals(j)) {
            return "风险较高，仅建议在用户明确愿意冲刺时保留";
        }
        if (JUDGEMENT_SMALL_REACH.equals(j)) {
            return "可作为小冲目标，建议同时准备更稳妥备选";
        }
        if (JUDGEMENT_SAFE.equals(j)) {
            return "可作为保底备选，建议复查当年招生计划后加入最终名单";
        }
        if (JUDGEMENT_STEADY_REACH.equals(j)) {
            return "可作为稳妥偏冲候选，建议核验近年复试与录取波动";
        }
        if (STATUS_OFFICIAL.equals(s)) {
            return "可作为稳妥候选，建议复查当年招生计划后加入最终名单";
        }
        return "可作为稳妥候选，建议优先核验官网招生计划";
    }

    public static Comparator<Map<String, Object>> directionComparator() {
        return Comparator
            .comparingInt((Map<String, Object> row) -> judgementRank(row.get("judgement")))
            .thenComparingInt(row -> completenessRank(row.get("dataCompleteness")))
            .thenComparingInt(row -> Math.abs(intVal(row.get("avgScoreGap"), 999)))
            .thenComparing((Map<String, Object> row) -> -intVal(row.get("dataYear"), 0))
            .thenComparing(row -> String.valueOf(row.getOrDefault("collegeName", ""))
                + String.valueOf(row.getOrDefault("programName", "")));
    }

    private static int judgementRank(Object value) {
        int idx = JUDGEMENT_ORDER.indexOf(normalizeJudgement(value));
        return idx < 0 ? JUDGEMENT_ORDER.size() : idx;
    }

    private static int completenessRank(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if ("A".equalsIgnoreCase(text)) return 0;
        if ("B".equalsIgnoreCase(text)) return 1;
        if ("C".equalsIgnoreCase(text)) return 2;
        return 3;
    }

    private static int intVal(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { return fallback; }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiReportSupportTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiReportSupport.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiReportSupportTest.java
git commit -m "feat: add ai report support contracts"
```

---

### Task 2: Tool Budget And Trace Models

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolBudget.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolTrace.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiToolBudgetTest.java`

- [ ] **Step 1: Write failing tests**

Create `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiToolBudgetTest.java`:

```java
package com.ruoyi.postgrad.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiToolBudgetTest {
    @Test
    void budgetStopsAfterConfiguredToolLimits() {
        AiToolBudget budget = AiToolBudget.reportDefaults();
        for (int i = 0; i < 12; i++) {
            assertTrue(budget.tryUse("getProgramDetail", 100));
        }
        assertFalse(budget.tryUse("getProgramDetail", 100));
        assertTrue(budget.isExplorationLimited());
    }

    @Test
    void budgetStopsAfterTokenLimit() {
        AiToolBudget budget = new AiToolBudget(20, 12, 3, 5, 200);
        assertTrue(budget.tryUse("searchPrograms", 150));
        assertFalse(budget.tryUse("searchPrograms", 60));
        assertTrue(budget.isExplorationLimited());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiToolBudgetTest test
```

Expected: FAIL because `AiToolBudget` does not exist.

- [ ] **Step 3: Create budget and trace classes**

Create `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolBudget.java`:

```java
package com.ruoyi.postgrad.domain;

public class AiToolBudget {
    private final int maxTotalCalls;
    private final int maxDetailCalls;
    private final int maxExpandCalls;
    private final int maxVerificationCalls;
    private final int maxResultTokens;

    private int totalCalls;
    private int detailCalls;
    private int expandCalls;
    private int verificationCalls;
    private int resultTokens;
    private boolean explorationLimited;

    public AiToolBudget(int maxTotalCalls, int maxDetailCalls, int maxExpandCalls,
        int maxVerificationCalls, int maxResultTokens) {
        this.maxTotalCalls = maxTotalCalls;
        this.maxDetailCalls = maxDetailCalls;
        this.maxExpandCalls = maxExpandCalls;
        this.maxVerificationCalls = maxVerificationCalls;
        this.maxResultTokens = maxResultTokens;
    }

    public static AiToolBudget reportDefaults() {
        return new AiToolBudget(20, 12, 3, 5, 12000);
    }

    public static AiToolBudget chatTurnDefaults() {
        return new AiToolBudget(8, 5, 2, 3, 5000);
    }

    public boolean tryUse(String toolName, int estimatedResultTokens) {
        if (totalCalls + 1 > maxTotalCalls || resultTokens + estimatedResultTokens > maxResultTokens) {
            explorationLimited = true;
            return false;
        }
        if ("getProgramDetail".equals(toolName) && detailCalls + 1 > maxDetailCalls) {
            explorationLimited = true;
            return false;
        }
        if ("expandCandidatePool".equals(toolName) && expandCalls + 1 > maxExpandCalls) {
            explorationLimited = true;
            return false;
        }
        if ("verifyOfficialInfo".equals(toolName) && verificationCalls + 1 > maxVerificationCalls) {
            explorationLimited = true;
            return false;
        }
        totalCalls++;
        resultTokens += Math.max(0, estimatedResultTokens);
        if ("getProgramDetail".equals(toolName)) detailCalls++;
        if ("expandCandidatePool".equals(toolName)) expandCalls++;
        if ("verifyOfficialInfo".equals(toolName)) verificationCalls++;
        return true;
    }

    public boolean isExplorationLimited() {
        return explorationLimited;
    }
}
```

Create `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolTrace.java`:

```java
package com.ruoyi.postgrad.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiToolTrace {
    private final List<Map<String, Object>> calls = new ArrayList<>();
    private final Set<Long> detailedProgramIds = new LinkedHashSet<>();
    private final Set<Long> expandedProgramIds = new LinkedHashSet<>();
    private int removedIncompleteCount;
    private boolean explorationLimited;

    public void record(String toolName, Map<String, Object> args, Object resultSummary) {
        Map<String, Object> call = new LinkedHashMap<>();
        call.put("toolName", toolName);
        call.put("args", args);
        call.put("resultSummary", resultSummary);
        calls.add(call);
    }

    public void recordDetail(long programId) {
        detailedProgramIds.add(programId);
    }

    public void recordExpanded(long programId) {
        expandedProgramIds.add(programId);
    }

    public boolean hasDetail(long programId) {
        return detailedProgramIds.contains(programId);
    }

    public List<Map<String, Object>> getCalls() {
        return calls;
    }

    public int getRemovedIncompleteCount() {
        return removedIncompleteCount;
    }

    public void setRemovedIncompleteCount(int removedIncompleteCount) {
        this.removedIncompleteCount = Math.max(0, removedIncompleteCount);
    }

    public boolean isExplorationLimited() {
        return explorationLimited;
    }

    public void setExplorationLimited(boolean explorationLimited) {
        this.explorationLimited = explorationLimited;
    }
}
```

- [ ] **Step 4: Run tests**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiToolBudgetTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolBudget.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/AiToolTrace.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/domain/AiToolBudgetTest.java
git commit -m "feat: add ai tool budget and trace models"
```

---

### Task 3: Broad Agent Candidate Pool

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiCandidatePoolService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImpl.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImplTest.java`

- [ ] **Step 1: Add failing test for agent pool size and score-line-only bucket**

Append to `AiCandidatePoolServiceImplTest`:

```java
@Test
void buildAgentPoolKeepsBroadPoolAndMarksScoreLineOnlyAsPending() {
    List<RowMap> rows = rows(1, 320);
    for (int i = 0; i < rows.size(); i++) {
        rows.get(i).put("avgAdmittedScore", i < 280 ? 300 : null);
        rows.get(i).put("scoreLine", 270);
    }
    when(recommendationMapper.selectForAgentPool(330, Collections.emptyList(), 290, 370, 500))
        .thenReturn(rows);

    List<RowMap> pool = service.buildAgentPool(330, Collections.emptyList());

    assertEquals(300, pool.size());
    assertTrue(pool.stream().anyMatch(row -> "pending".equals(row.get("verificationStatus"))));
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiCandidatePoolServiceImplTest#buildAgentPoolKeepsBroadPoolAndMarksScoreLineOnlyAsPending test
```

Expected: FAIL because `buildAgentPool` and mapper method do not exist.

- [ ] **Step 3: Add service and mapper contracts**

In `IAiCandidatePoolService.java`, add:

```java
List<RowMap> buildAgentPool(int estimatedScore, List<String> regions);
List<RowMap> expandAgentPool(int estimatedScore, List<String> regions, Map<String, Object> filters, List<RowMap> existing);
```

In `RecommendationMapper.java`, add:

```java
List<RowMap> selectForAgentPool(@Param("estimatedScore") int estimatedScore,
    @Param("regions") List<String> regions,
    @Param("minScore") int minScore,
    @Param("maxScore") int maxScore,
    @Param("limit") int limit);
```

- [ ] **Step 4: Implement agent pool service behavior**

In `AiCandidatePoolServiceImpl`, add constants and methods:

```java
private static final int AGENT_INITIAL_LIMIT = 300;
private static final int AGENT_MAX_LIMIT = 500;

@Override
public List<RowMap> buildAgentPool(int estimatedScore, List<String> regions) {
    List<String> safeRegions = regions == null ? Collections.emptyList() : regions;
    int minScore = Math.max(0, estimatedScore - 80);
    int maxScore = estimatedScore + 40;
    List<RowMap> rows = recommendationMapper.selectForAgentPool(
        estimatedScore, safeRegions, minScore, maxScore, AGENT_MAX_LIMIT);
    return normalizeAgentPool(rows, estimatedScore, AGENT_INITIAL_LIMIT);
}

@Override
public List<RowMap> expandAgentPool(int estimatedScore, List<String> regions,
    Map<String, Object> filters, List<RowMap> existing) {
    List<RowMap> expanded = buildAgentPool(estimatedScore, regions);
    List<RowMap> merged = new ArrayList<>();
    if (existing != null) merged.addAll(existing);
    merged.addAll(expanded);
    return dedupeAgentPool(merged).stream().limit(AGENT_MAX_LIMIT).toList();
}

private List<RowMap> normalizeAgentPool(List<RowMap> rows, int estimatedScore, int limit) {
    if (rows == null) return Collections.emptyList();
    List<RowMap> normalized = dedupeAgentPool(rows);
    for (RowMap row : normalized) {
        Object avg = row.get("avgAdmittedScore");
        if (avg instanceof Number n) {
            row.put("gap", estimatedScore - n.intValue());
            row.put("verificationStatus", "local_data_only");
        } else {
            row.put("gap", null);
            row.put("verificationStatus", "pending");
        }
    }
    normalized.sort((a, b) -> Integer.compare(agentRank(a, estimatedScore), agentRank(b, estimatedScore)));
    return normalized.stream().limit(limit).toList();
}

private List<RowMap> dedupeAgentPool(List<RowMap> rows) {
    List<RowMap> result = new ArrayList<>();
    java.util.Set<String> seen = new java.util.LinkedHashSet<>();
    for (RowMap row : rows) {
        String key = dedupeKey(row);
        if (seen.add(key)) result.add(row);
    }
    return result;
}

private String dedupeKey(RowMap row) {
    Object id = row.get("programId");
    if (id != null) return "program:" + id;
    return "fallback:" + row.get("schoolName") + ":" + row.get("collegeName") + ":"
        + row.get("programCode") + ":" + row.get("programName") + ":" + row.get("dataYear");
}

private int agentRank(RowMap row, int estimatedScore) {
    Object avg = row.get("avgAdmittedScore");
    int distance = avg instanceof Number n ? Math.abs(n.intValue() - estimatedScore) : 999;
    int completeness = "A".equals(row.get("dataCompleteness")) ? 0 : "B".equals(row.get("dataCompleteness")) ? 1 : 2;
    return completeness * 1000 + distance;
}
```

- [ ] **Step 5: Add MyBatis SQL**

In `RecommendationMapper.xml`, add `selectForAgentPool` after `selectForAnalysis`:

```xml
<select id="selectForAgentPool" resultType="com.ruoyi.postgrad.domain.RowMap">
    select
        p.id as programId, s.id as schoolId, s.name as schoolName,
        s.province as province, s.city as city, s.tier as schoolTier,
        s.is_985 as is985, s.is_211 as is211, s.is_double_first as isDoubleFirst,
        c.name as collegeName,
        p.program_code as programCode, p.program_name as programName,
        p.research_direction as researchDirection,
        p.degree_type as degreeType, p.study_mode as studyMode,
        sc.year as dataYear, sc.score_line as scoreLine,
        ap.total_plan as planCount, ap.unified_exam_quota as unifiedExamQuota,
        ap.retest_count as retestCount,
        ar.admitted_count as admittedCount,
        ar.min_admitted_score as admissionLow,
        ar.avg_admitted_score as avgAdmittedScore,
        ar.max_admitted_score as admissionHigh,
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
    where p.status = 'active' and s.status = 'active'
      and p.is_408 = 1
      and p.study_mode = 'full_time'
      and (sc.score_line is not null or ar.avg_admitted_score is not null)
      and (ar.avg_admitted_score is null or ar.avg_admitted_score between #{minScore} and #{maxScore})
    <if test="regions != null and regions.size() > 0">
        and s.province in
        <foreach collection="regions" item="region" open="(" separator="," close=")">
            #{region}
        </foreach>
    </if>
    order by
        case coalesce(q.completeness_level, 'C') when 'A' then 0 when 'B' then 1 else 2 end,
        case when ar.avg_admitted_score is null then 1 else 0 end,
        abs(coalesce(cast(ar.avg_admitted_score as signed), #{estimatedScore}) - #{estimatedScore}),
        sc.year desc
    limit #{limit}
</select>
```

- [ ] **Step 6: Run tests**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiCandidatePoolServiceImplTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiCandidatePoolService.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImpl.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationMapper.xml ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiCandidatePoolServiceImplTest.java
git commit -m "feat: build broad ai agent candidate pool"
```

---

### Task 4: Verification Stub

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiVerificationService.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/NoopAiVerificationServiceImpl.java`

- [ ] **Step 1: Create verification interface**

Create `IAiVerificationService.java`:

```java
package com.ruoyi.postgrad.service;

import java.util.Map;

public interface IAiVerificationService {
    Map<String, Object> verify(Map<String, Object> input);
}
```

- [ ] **Step 2: Create Phase 1 no-op implementation**

Create `NoopAiVerificationServiceImpl.java`:

```java
package com.ruoyi.postgrad.service.impl;

import com.ruoyi.postgrad.domain.AiReportSupport;
import com.ruoyi.postgrad.service.IAiVerificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class NoopAiVerificationServiceImpl implements IAiVerificationService {
    @Override
    public Map<String, Object> verify(Map<String, Object> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean hasProgramId = input != null && input.get("programId") != null;
        result.put("verificationStatus", hasProgramId
            ? AiReportSupport.STATUS_LOCAL_DATA_ONLY
            : AiReportSupport.STATUS_PENDING);
        result.put("verificationProvider", null);
        result.put("sourceTitle", null);
        result.put("sourceUrl", null);
        result.put("summary", hasProgramId ? "Phase 1 未启用联网核验，使用本地数据。" : "本地数据不足，等待核验。");
        return result;
    }
}
```

- [ ] **Step 3: Compile module**

```powershell
mvn -pl ruoyi-postgrad -DskipTests compile
```

Expected: SUCCESS.

- [ ] **Step 4: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiVerificationService.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/NoopAiVerificationServiceImpl.java
git commit -m "feat: add ai verification service stub"
```

---

### Task 5: Budgeted AI Tools With Trace

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/tool/AiRecommendationTools.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/tool/AiRecommendationToolsTest.java`

- [ ] **Step 1: Write failing tool budget test**

Create `AiRecommendationToolsTest.java`:

```java
package com.ruoyi.postgrad.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RowMap;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class AiRecommendationToolsTest {
    @Test
    void getProgramDetailReturnsBudgetLimitWhenDetailCallsExhausted() throws Exception {
        AiRecommendationTools tools = new AiRecommendationTools();
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations ops = org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);
        org.mockito.Mockito.when(redis.opsForValue()).thenReturn(ops);

        RowMap row = new RowMap();
        row.put("programId", 1L);
        org.mockito.Mockito.when(ops.get("ai:agent:pool:c1")).thenReturn(JSON.toJSONString(List.of(row)));

        Field redisField = AiRecommendationTools.class.getDeclaredField("redisTemplate");
        redisField.setAccessible(true);
        redisField.set(tools, redis);

        AiRecommendationTools.setConversationId("c1");
        for (int i = 0; i < 12; i++) {
            tools.getProgramDetail(1L);
        }
        String result = tools.getProgramDetail(1L);
        AiRecommendationTools.clear();

        assertTrue(result.contains("tool_budget_exceeded"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationToolsTest test
```

Expected: FAIL because tools still read `ai:pool:` and have no budgets.

- [ ] **Step 3: Update `AiRecommendationTools`**

Modify `AiRecommendationTools`:

```java
private static final ThreadLocal<AiToolBudget> CURRENT_BUDGET =
    ThreadLocal.withInitial(AiToolBudget::reportDefaults);
private static final ThreadLocal<AiToolTrace> CURRENT_TRACE =
    ThreadLocal.withInitial(AiToolTrace::new);

public static void startReportContext(String id) {
    CURRENT_CONVERSATION.set(id);
    CURRENT_BUDGET.set(AiToolBudget.reportDefaults());
    CURRENT_TRACE.set(new AiToolTrace());
}

public static AiToolTrace currentTrace() {
    return CURRENT_TRACE.get();
}
```

Update `clear()`:

```java
public static void clear() {
    CURRENT_CONVERSATION.remove();
    CURRENT_BUDGET.remove();
    CURRENT_TRACE.remove();
}
```

Update pool key reads from `ai:pool:` to try `ai:agent:pool:` first:

```java
private String loadPoolJson(String conversationId) {
    String poolJson = redisTemplate.opsForValue().get("ai:agent:pool:" + conversationId);
    if (poolJson == null) {
        poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
    }
    return poolJson;
}
```

At the top of each tool, call budget:

```java
if (!CURRENT_BUDGET.get().tryUse("getProgramDetail", 800)) {
    CURRENT_TRACE.get().setExplorationLimited(true);
    return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
}
```

Record detail:

```java
CURRENT_TRACE.get().recordDetail(programId);
CURRENT_TRACE.get().record("getProgramDetail", Map.of("programId", programId), Map.of("found", true));
```

Add tools:

```java
@Tool("扩展当前候选池，例如按地区、学校层次、分数范围加入更多候选")
public String expandCandidatePool(@P("扩展条件 JSON") String filters) {
    if (!CURRENT_BUDGET.get().tryUse("expandCandidatePool", 600)) {
        CURRENT_TRACE.get().setExplorationLimited(true);
        return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
    }
    CURRENT_TRACE.get().record("expandCandidatePool", Map.of("filters", filters), Map.of("phase", "stub"));
    return "{\"addedCount\":0,\"duplicateCount\":0,\"totalPoolCount\":0,\"appliedFilters\":" + JSON.toJSONString(filters) + "}";
}

@Tool("核验院校官网或研究生院信息。Phase 1 仅返回本地数据状态，不联网")
public String verifyOfficialInfo(@P("核验输入 JSON") String inputJson) {
    if (!CURRENT_BUDGET.get().tryUse("verifyOfficialInfo", 500)) {
        CURRENT_TRACE.get().setExplorationLimited(true);
        return "{\"error\":\"tool_budget_exceeded\",\"explorationLimited\":true}";
    }
    CURRENT_TRACE.get().record("verifyOfficialInfo", Map.of("input", inputJson), Map.of("provider", null));
    return "{\"verificationStatus\":\"local_data_only\",\"verificationProvider\":null}";
}
```

- [ ] **Step 4: Run tool tests**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationToolsTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/tool/AiRecommendationTools.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/tool/AiRecommendationToolsTest.java
git commit -m "feat: add budgeted ai recommendation tools"
```

---

### Task 6: Report Validation And Injection

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Add failing private-helper tests**

Append tests using reflection:

```java
@Test
void shouldNormalizeReportJudgementAndAction() throws Exception {
    AiRecommendationServiceImpl service = new AiRecommendationServiceImpl();
    Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("normalizeReportItem", Map.class);
    method.setAccessible(true);

    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
    item.put("aiJudgement", "稳妥偏冲刺");
    item.put("verificationStatus", "unknown");

    @SuppressWarnings("unchecked")
    java.util.Map<String, Object> normalized = (java.util.Map<String, Object>) method.invoke(service, item);

    assertEquals("steady_reach", normalized.get("judgement"));
    assertEquals("稳妥偏冲", normalized.get("judgementLabel"));
    assertEquals("pending", normalized.get("verificationStatus"));
    assertEquals("可作为稳妥偏冲候选，建议核验近年复试与录取波动", normalized.get("recommendedAction"));
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest#shouldNormalizeReportJudgementAndAction test
```

Expected: FAIL because `normalizeReportItem` does not exist.

- [ ] **Step 3: Add normalization helper**

In `AiRecommendationServiceImpl`, add:

```java
private Map<String, Object> normalizeReportItem(Map<String, Object> item) {
    Map<String, Object> normalized = new LinkedHashMap<>(item);
    String judgement = AiReportSupport.normalizeJudgement(
        normalized.getOrDefault("judgement", normalized.get("aiJudgement")));
    String status = AiReportSupport.normalizeVerificationStatus(normalized.get("verificationStatus"));
    normalized.put("judgement", judgement);
    normalized.put("judgementLabel", AiReportSupport.judgementLabel(judgement));
    normalized.put("verificationStatus", status);
    normalized.putIfAbsent("verificationProvider", null);
    normalized.put("recommendedAction", AiReportSupport.recommendedAction(judgement, status));
    return normalized;
}
```

Add validation helper:

```java
@SuppressWarnings({"unchecked", "rawtypes"})
private Map<String, Object> validateAndNormalizeReport(Map<String, Object> report, AiToolTrace trace) {
    Map<String, Object> result = new LinkedHashMap<>(report);
    List<Map<String, Object>> tiers = (List<Map<String, Object>>) result.get("tiers");
    int removed = 0;
    if (tiers != null) {
        for (Map<String, Object> tier : tiers) {
            List<Map<String, Object>> schools = (List<Map<String, Object>>) tier.get("schools");
            if (schools == null) continue;
            List<Map<String, Object>> kept = new ArrayList<>();
            for (Map<String, Object> school : schools) {
                Object pidObj = school.get("programId");
                long pid = pidObj instanceof Number n ? n.longValue() : -1L;
                if (pid > 0 && trace != null && !trace.hasDetail(pid)) {
                    removed++;
                    continue;
                }
                kept.add(normalizeReportItem(school));
            }
            kept.sort(AiReportSupport.directionComparator());
            tier.put("schools", kept);
        }
    }
    Map<String, Object> meta = new LinkedHashMap<>();
    meta.put("toolTraceIncompleteCount", removed);
    meta.put("explorationLimited", trace != null && trace.isExplorationLimited());
    result.put("metadata", meta);
    return result;
}
```

- [ ] **Step 4: Wire into synchronous report generation**

After `parseReportJson(...)` and `injectMatchScores(...)`, replace `reportJson` with validated output:

```java
AiToolTrace trace = AiRecommendationTools.currentTrace();
Map<String, Object> validated = validateAndNormalizeReport(reportJson, trace);
String resultJson = JSON.toJSONString(validated);
```

- [ ] **Step 5: Run tests**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java
git commit -m "feat: validate ai recommendation reports"
```

---

### Task 7: Analyze Flow Uses Agent Pool

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java`
- Test: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Update analyze to store agent pool keys**

In `analyze(Long userId)`, replace `buildAnalysisPool(...)` with:

```java
List<RowMap> pool = aiCandidatePoolService.buildAgentPool(estimatedScore, regions);
```

Store Redis key:

```java
redisTemplate.opsForValue().set(
    "ai:agent:pool:" + reportId, poolJson, 1, TimeUnit.HOURS);
```

Keep old key for compatibility during rollout:

```java
redisTemplate.opsForValue().set(
    "ai:analyze:pool:" + reportId, poolJson, 1, TimeUnit.HOURS);
```

- [ ] **Step 2: Update consumer to prefer agent key**

In `AiReportConsumer.handleAnalyzeMessage`, load:

```java
String poolJson = redisTemplate.opsForValue().get("ai:agent:pool:" + reportId);
if (poolJson == null) {
    poolJson = redisTemplate.opsForValue().get("ai:analyze:pool:" + reportId);
}
```

- [ ] **Step 3: Update prompt to require tools and schema**

In `buildAnalysisPrompt`, add schema text:

```java
sb.append("输出学校时必须使用 judgement 枚举: safe, steady, steady_reach, small_reach, high_risk_reach, data_insufficient_pending。\\n");
sb.append("verificationStatus 必须是: official, third_party, local_data_only, verification_failed, pending。\\n");
sb.append("不要输出 matchScore。推荐理由写入 evidence 和 risks。\\n");
```

- [ ] **Step 4: Compile**

```powershell
mvn -pl ruoyi-admin -am -DskipTests compile
```

Expected: SUCCESS.

- [ ] **Step 5: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java
git commit -m "feat: use agent pool for ai analyze reports"
```

---

### Task 8: Frontend Report Normalization

**Files:**
- Create: `user-ui/src/utils/aiReport.js`
- Modify: `user-ui/src/views/AiReport.vue`

- [ ] **Step 1: Create report normalizer**

Create `user-ui/src/utils/aiReport.js`:

```javascript
export function normalizeAiReport(raw) {
  const data = raw && raw.result ? raw.result : raw
  if (!data) return { legacy: false, summary: '', tiers: [], metadata: {} }
  const tiers = Array.isArray(data.tiers) ? data.tiers : []
  const legacy = tiers.some(tier => (tier.schools || []).some(school => school.matchScore && !school.judgement))
  return {
    legacy,
    summary: data.summary || '',
    metadata: data.metadata || {},
    tiers: tiers.map(tier => ({
      ...tier,
      schools: sortSameSchoolDirections((tier.schools || []).map(normalizeSchool))
    }))
  }
}

function normalizeSchool(school) {
  return {
    ...school,
    judgement: school.judgement || 'data_insufficient_pending',
    judgementLabel: school.judgementLabel || school.aiJudgement || school.label || '数据不足待核验',
    evidence: Array.isArray(school.evidence) ? school.evidence : [],
    risks: Array.isArray(school.risks) ? school.risks : [],
    verificationStatus: school.verificationStatus || 'local_data_only',
    recommendedAction: school.recommendedAction || ''
  }
}

function sortSameSchoolDirections(schools) {
  return [...schools].sort((a, b) => {
    const rankA = judgementRank(a.judgement)
    const rankB = judgementRank(b.judgement)
    if (rankA !== rankB) return rankA - rankB
    const compA = completenessRank(a.dataCompleteness)
    const compB = completenessRank(b.dataCompleteness)
    if (compA !== compB) return compA - compB
    const gapA = Number.isFinite(Number(a.avgScoreGap || a.gap)) ? Math.abs(Number(a.avgScoreGap || a.gap)) : 999
    const gapB = Number.isFinite(Number(b.avgScoreGap || b.gap)) ? Math.abs(Number(b.avgScoreGap || b.gap)) : 999
    if (gapA !== gapB) return gapA - gapB
    return String((a.collegeName || '') + (a.programName || '')).localeCompare(String((b.collegeName || '') + (b.programName || '')), 'zh-CN')
  })
}

function judgementRank(value) {
  return ['steady', 'steady_reach', 'safe', 'small_reach', 'high_risk_reach', 'data_insufficient_pending'].indexOf(value)
}

function completenessRank(value) {
  return { A: 0, B: 1, C: 2 }[value] ?? 3
}
```

- [ ] **Step 2: Update `AiReport.vue` computed result**

Import:

```javascript
import { normalizeAiReport } from '@/utils/aiReport'
```

Replace `result` computed body:

```javascript
const result = computed(() => {
  if (!report.value) return { summary: '', tiers: [], metadata: {}, legacy: false }
  const data = report.value.result || report.value
  if (typeof data === 'string') {
    try { return normalizeAiReport(JSON.parse(data)) } catch (e) { return { summary: '', tiers: [], metadata: {}, legacy: false } }
  }
  return normalizeAiReport(data)
})
```

- [ ] **Step 3: Add notices in template**

After report header:

```vue
<el-alert
  v-if="result.legacy"
  type="warning"
  show-icon
  title="这是旧版 AI 报告，推荐依据字段不完整。"
/>
<el-alert
  v-if="result.metadata && result.metadata.toolTraceIncompleteCount"
  type="warning"
  show-icon
  :title="`有 ${result.metadata.toolTraceIncompleteCount} 所学校因数据核验不完整被移出推荐，可重新生成或查看调试详情。`"
/>
<el-alert
  v-if="result.metadata && result.metadata.explorationLimited"
  type="info"
  show-icon
  title="本次 AI 数据探索已达到工具调用上限，报告基于已核验的候选生成。"
/>
```

- [ ] **Step 4: Replace match bar with judgement/evidence block**

Replace `.match-bar` block with:

```vue
<div class="judgement-box" :class="'judgement-' + school.judgement">
  <span>AI 判断</span>
  <strong>{{ school.judgementLabel }}</strong>
</div>
<ul v-if="school.evidence && school.evidence.length" class="evidence-list">
  <li v-for="item in school.evidence" :key="item">{{ item }}</li>
</ul>
<ul v-if="school.risks && school.risks.length" class="risk-list">
  <li v-for="item in school.risks" :key="item">{{ item }}</li>
</ul>
<p v-if="school.recommendedAction" class="recommended-action">{{ school.recommendedAction }}</p>
```

- [ ] **Step 5: Run frontend build**

```powershell
cd user-ui
npm run build
```

Expected: build succeeds.

- [ ] **Step 6: Commit**

```powershell
git add user-ui/src/utils/aiReport.js user-ui/src/views/AiReport.vue
git commit -m "feat: render ai judgement report cards"
```

---

### Task 9: End-To-End Backend Verification

**Files:**
- No planned source file creation.

- [ ] **Step 1: Run backend unit tests for touched modules**

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiReportSupportTest,AiToolBudgetTest,AiCandidatePoolServiceImplTest,AiRecommendationServiceImplTest,AiRecommendationToolsTest test
```

Expected: PASS.

- [ ] **Step 2: Compile backend application graph**

```powershell
mvn -pl ruoyi-admin -am -DskipTests compile
```

Expected: SUCCESS.

- [ ] **Step 3: Inspect staged changes**

```powershell
git status --short
git log --oneline -8
```

Expected: only intentional files are modified or committed; recent commits correspond to tasks above.

- [ ] **Step 4: Commit verification notes if a docs update was needed**

If no docs update is needed, do not create a commit. If a command or behavior changed from the plan, update this plan or the spec with the exact command and commit:

```powershell
git add docs/superpowers/plans/2026-06-02-ai-agent-recommendation-phase1-plan.md
git commit -m "docs: update ai agent phase1 implementation plan"
```

---

## Self-Review

Spec coverage:

- Broad candidate pool: Task 3.
- Local tools and expansion: Task 5.
- Verification schema and provider reservation: Task 4 and Task 6.
- Judgement/status/action templates: Task 1 and Task 6.
- Tool trace and budgets: Task 2 and Task 5.
- Report validation and notices: Task 6 and Task 8.
- Frontend legacy fallback: Task 8.
- Backend/frontend verification: Task 9.

Implementation risk:

- `AiReportConsumer` and `AiRecommendationServiceImpl` currently duplicate report-generation behavior. Task 7 updates both to avoid split behavior between synchronous and async paths.
- The existing workspace has unrelated modified files. Implementation agents must read `git status --short` before each task and only stage files listed by that task.

