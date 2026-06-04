# AI Report Fact Opinion Hydration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework AI reports so user profile preferences guide AI opinions, while all school/program facts are hydrated by backend data in both synchronous and MQ report paths.

**Architecture:** Add a shared report builder in `ruoyi-postgrad` that owns prompt construction, AI response parsing/fallback, candidate ID validation, and fact hydration. `AiRecommendationServiceImpl` and `AiReportConsumer` both call this shared builder. Extend the profile model/UI with lightweight preference fields, then normalize the new `opinion` report shape in the frontend.

**Tech Stack:** Java 17, Spring/RuoYi, MyBatis XML, FastJSON2, JUnit 5, Mockito, Vue 2/Element UI, Node `.mjs` static tests, Maven.

---

## File Structure

- Create `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/AiReportBuilder.java`
  - Public interface used by both report paths.
- Create `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiReportBuilderImpl.java`
  - Shared prompt, parse, fallback, validation, hydration, and normalization logic.
- Modify `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
  - Delegates report JSON generation to `AiReportBuilder`.
- Modify `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java`
  - Delegates conversation and analyze MQ report generation to `AiReportBuilder`.
- Modify `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/UserProfile.java`
  - Adds profile preference fields.
- Modify `ruoyi-postgrad/src/main/resources/mapper/postgrad/UserProfileMapper.xml`
  - Reads and writes preference columns.
- Modify `sql/408_school_selection_schema.sql`
  - Adds preference columns to schema.
- Create `sql/2026-06-04-user-profile-preferences.sql`
  - Migration for existing databases.
- Modify `user-ui/src/views/Profile.vue`
  - Adds lightweight preference controls.
- Modify `user-ui/src/views/AiRecommend.vue`
  - Shows preference summary.
- Modify `user-ui/src/utils/aiReport.js`
  - Normalizes hydrated `opinion` reports.
- Add `user-ui/src/utils/aiReport.opinion.test.mjs`
  - Frontend normalization test.
- Add/modify tests:
  - `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiReportBuilderImplTest.java`
  - `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

---

### Task 1: Add Profile Preference Fields

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/UserProfile.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/UserProfileMapper.xml`
- Modify: `sql/408_school_selection_schema.sql`
- Create: `sql/2026-06-04-user-profile-preferences.sql`

- [ ] **Step 1: Add failing mapper/domain contract test by static scan**

Create a temporary check command to prove the fields do not exist yet:

```powershell
rg -n "priorityPreference|schoolTierPreference|regionStrategy|dataReliabilityPreference|priority_preference|school_tier_preference|region_strategy|data_reliability_preference" ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/UserProfile.java ruoyi-postgrad/src/main/resources/mapper/postgrad/UserProfileMapper.xml
```

Expected: no matches for the new fields.

- [ ] **Step 2: Add fields to `UserProfile.java`**

Add fields after `riskPreference`:

```java
private String priorityPreference;
private String schoolTierPreference;
private String regionStrategy;
private String dataReliabilityPreference;
```

Add getters and setters:

```java
public String getPriorityPreference() { return priorityPreference; }
public void setPriorityPreference(String priorityPreference) { this.priorityPreference = priorityPreference; }
public String getSchoolTierPreference() { return schoolTierPreference; }
public void setSchoolTierPreference(String schoolTierPreference) { this.schoolTierPreference = schoolTierPreference; }
public String getRegionStrategy() { return regionStrategy; }
public void setRegionStrategy(String regionStrategy) { this.regionStrategy = regionStrategy; }
public String getDataReliabilityPreference() { return dataReliabilityPreference; }
public void setDataReliabilityPreference(String dataReliabilityPreference) { this.dataReliabilityPreference = dataReliabilityPreference; }
```

Add to `toString()`:

```java
.append("priorityPreference", getPriorityPreference())
.append("schoolTierPreference", getSchoolTierPreference())
.append("regionStrategy", getRegionStrategy())
.append("dataReliabilityPreference", getDataReliabilityPreference())
```

- [ ] **Step 3: Update `UserProfileMapper.xml`**

Add result mappings after `riskPreference`:

```xml
<result property="priorityPreference"        column="priority_preference"         />
<result property="schoolTierPreference"     column="school_tier_preference"      />
<result property="regionStrategy"           column="region_strategy"             />
<result property="dataReliabilityPreference" column="data_reliability_preference" />
```

Add columns to `selectUserProfileVo`:

```xml
priority_preference, school_tier_preference, region_strategy,
data_reliability_preference,
```

Add insert column conditions:

```xml
<if test="priorityPreference != null">priority_preference,</if>
<if test="schoolTierPreference != null">school_tier_preference,</if>
<if test="regionStrategy != null">region_strategy,</if>
<if test="dataReliabilityPreference != null">data_reliability_preference,</if>
```

Add insert value conditions:

```xml
<if test="priorityPreference != null">#{priorityPreference},</if>
<if test="schoolTierPreference != null">#{schoolTierPreference},</if>
<if test="regionStrategy != null">#{regionStrategy},</if>
<if test="dataReliabilityPreference != null">#{dataReliabilityPreference},</if>
```

Add update assignments:

```xml
<if test="priorityPreference != null">priority_preference = #{priorityPreference},</if>
<if test="schoolTierPreference != null">school_tier_preference = #{schoolTierPreference},</if>
<if test="regionStrategy != null">region_strategy = #{regionStrategy},</if>
<if test="dataReliabilityPreference != null">data_reliability_preference = #{dataReliabilityPreference},</if>
```

- [ ] **Step 4: Add SQL migration**

Create `sql/2026-06-04-user-profile-preferences.sql`:

```sql
alter table user_profile
    add column priority_preference varchar(32) null comment '择校最看重: success_rate/school_tier/developed_region/major_strength' after risk_preference,
    add column school_tier_preference varchar(32) null comment '学校层次倾向: must_211_or_better/prefer_211_or_better/no_strict_requirement' after priority_preference,
    add column region_strategy varchar(32) null comment '地区策略: no_limit/developed_regions/specific_regions/near_home' after school_tier_preference,
    add column data_reliability_preference varchar(32) null comment '数据可靠性偏好: strict/medium/loose' after region_strategy;
```

Add the same columns to `sql/408_school_selection_schema.sql` in the `user_profile` table definition.

- [ ] **Step 5: Verify backend compiles for postgrad module**

Run:

```powershell
mvn -pl ruoyi-postgrad -DskipTests compile
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/domain/UserProfile.java ruoyi-postgrad/src/main/resources/mapper/postgrad/UserProfileMapper.xml sql/408_school_selection_schema.sql sql/2026-06-04-user-profile-preferences.sql
git commit -m "feat(profile): add school selection preferences"
```

---

### Task 2: Add Profile Preference UI

**Files:**
- Modify: `user-ui/src/views/Profile.vue`
- Modify: `user-ui/src/views/AiRecommend.vue`
- Add: `user-ui/src/views/Profile.preferences.test.mjs`

- [ ] **Step 1: Write frontend static test**

Create `user-ui/src/views/Profile.preferences.test.mjs`:

```js
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const profile = readFileSync(new URL('./Profile.vue', import.meta.url), 'utf8')
const recommend = readFileSync(new URL('./AiRecommend.vue', import.meta.url), 'utf8')

for (const field of [
  'priorityPreference',
  'schoolTierPreference',
  'regionStrategy',
  'dataReliabilityPreference'
]) {
  assert.match(profile, new RegExp(`form\\.${field}`), `${field} should be editable in profile form`)
  assert.match(profile, new RegExp(`profile\\.${field}`), `${field} should be loaded into profile state`)
}

assert.match(profile, /择校偏好/)
assert.match(profile, /更看重/)
assert.match(profile, /学校层次倾向/)
assert.match(profile, /地区策略/)
assert.match(profile, /数据可靠性/)
assert.match(recommend, /择校偏好/)
```

- [ ] **Step 2: Run and verify failure**

Run:

```powershell
node user-ui\src\views\Profile.preferences.test.mjs
```

Expected: FAIL because the fields are not in the UI yet.

- [ ] **Step 3: Add fields to Profile state and form**

In `Profile.vue`, extend both `profile` and `form` reactive objects with:

```js
priorityPreference: 'success_rate',
schoolTierPreference: 'no_strict_requirement',
regionStrategy: 'no_limit',
dataReliabilityPreference: 'medium'
```

When loading profile data, assign:

```js
profile.priorityPreference = p.priorityPreference || 'success_rate'
profile.schoolTierPreference = p.schoolTierPreference || 'no_strict_requirement'
profile.regionStrategy = p.regionStrategy || 'no_limit'
profile.dataReliabilityPreference = p.dataReliabilityPreference || 'medium'
```

When entering edit mode, assign the same fields from `profile` to `form`.

When saving, include these fields in the `payload`.

- [ ] **Step 4: Add controls to Profile form**

Add this section after target provinces:

```vue
<section class="preference-form-band">
  <h3>择校偏好</h3>
  <el-form-item label="更看重">
    <el-radio-group v-model="form.priorityPreference">
      <el-radio-button label="success_rate">上岸概率</el-radio-button>
      <el-radio-button label="school_tier">学校层次</el-radio-button>
      <el-radio-button label="developed_region">发达地区</el-radio-button>
      <el-radio-button label="major_strength">专业实力</el-radio-button>
    </el-radio-group>
  </el-form-item>
  <el-form-item label="学校层次倾向">
    <el-radio-group v-model="form.schoolTierPreference">
      <el-radio-button label="must_211_or_better">强烈希望 211+</el-radio-button>
      <el-radio-button label="prefer_211_or_better">优先 211+</el-radio-button>
      <el-radio-button label="no_strict_requirement">不强求</el-radio-button>
    </el-radio-group>
  </el-form-item>
  <el-form-item label="地区策略">
    <el-radio-group v-model="form.regionStrategy">
      <el-radio-button label="no_limit">不限</el-radio-button>
      <el-radio-button label="developed_regions">发达地区</el-radio-button>
      <el-radio-button label="specific_regions">按目标省份</el-radio-button>
      <el-radio-button label="near_home">离家近</el-radio-button>
    </el-radio-group>
  </el-form-item>
  <el-form-item label="数据可靠性">
    <el-radio-group v-model="form.dataReliabilityPreference">
      <el-radio-button label="strict">只看较完整</el-radio-button>
      <el-radio-button label="medium">缺失时提醒</el-radio-button>
      <el-radio-button label="loose">可接受缺失</el-radio-button>
    </el-radio-group>
  </el-form-item>
</section>
```

- [ ] **Step 5: Add preference summary rows**

Add helper label maps:

```js
const priorityLabels = {
  success_rate: '上岸概率',
  school_tier: '学校层次',
  developed_region: '发达地区',
  major_strength: '专业实力'
}
const schoolTierPreferenceLabels = {
  must_211_or_better: '强烈希望 211+',
  prefer_211_or_better: '优先 211+',
  no_strict_requirement: '不强求'
}
const regionStrategyLabels = {
  no_limit: '不限',
  developed_regions: '发达地区',
  specific_regions: '按目标省份',
  near_home: '离家近'
}
const dataReliabilityLabels = {
  strict: '只看较完整',
  medium: '缺失时提醒',
  loose: '可接受缺失'
}
```

Add rows to `profileRows`:

```js
{ label: '更看重', value: priorityLabels[profile.priorityPreference] || '上岸概率' },
{ label: '学校层次倾向', value: schoolTierPreferenceLabels[profile.schoolTierPreference] || '不强求' },
{ label: '地区策略', value: regionStrategyLabels[profile.regionStrategy] || '不限' },
{ label: '数据可靠性', value: dataReliabilityLabels[profile.dataReliabilityPreference] || '缺失时提醒' }
```

- [ ] **Step 6: Add AiRecommend summary**

In `AiRecommend.vue`, add the same four fields to the `profile` reactive object and load response assignment. Add one small stat or line that contains `择校偏好` and displays the main preference, for example:

```vue
<div class="profile-stat">
  <span>择校偏好</span>
  <strong>{{ priorityLabels[profile.priorityPreference] || '上岸概率' }}</strong>
</div>
```

- [ ] **Step 7: Run frontend checks**

Run:

```powershell
node user-ui\src\views\Profile.preferences.test.mjs
cd user-ui
npm run build
```

Expected: PASS. Build may show existing Vite warnings but no errors.

- [ ] **Step 8: Commit**

```powershell
git add user-ui/src/views/Profile.vue user-ui/src/views/AiRecommend.vue user-ui/src/views/Profile.preferences.test.mjs
git commit -m "feat(profile): capture AI recommendation preferences"
```

---

### Task 3: Create Shared AI Report Builder

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/AiReportBuilder.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiReportBuilderImpl.java`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiReportBuilderImplTest.java`

- [ ] **Step 1: Add interface**

Create `AiReportBuilder.java`:

```java
package com.ruoyi.postgrad.service;

import dev.langchain4j.model.chat.ChatModel;
import java.util.Map;

public interface AiReportBuilder {
    Map<String, Object> buildConversationReport(ChatModel chatModel, String conversationJson,
        String poolJson, int estimatedScore, Map<String, Object> preferenceProfile);

    Map<String, Object> buildAnalyzeReport(ChatModel chatModel, String poolJson,
        int estimatedScore, Map<String, Object> preferenceProfile);
}
```

- [ ] **Step 2: Add failing builder tests**

Create `AiReportBuilderImplTest.java`:

```java
package com.ruoyi.postgrad.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import dev.langchain4j.model.chat.ChatModel;
import java.lang.reflect.Field;
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
class AiReportBuilderImplTest {
    private AiReportBuilderImpl builder;

    @Mock private RecommendationMapper recommendationMapper;
    @Mock private ChatModel chatModel;

    @BeforeEach
    void setUp() throws Exception {
        builder = new AiReportBuilderImpl();
        Field field = AiReportBuilderImpl.class.getDeclaredField("recommendationMapper");
        field.setAccessible(true);
        field.set(builder, recommendationMapper);
    }

    @Test
    void shouldBuildOpinionOnlyPrompt() {
        String prompt = builder.buildConversationPrompt("[]", "[{\"programId\":123,\"avgAdmittedScore\":295}]",
            Map.of("priorityPreference", "school_tier"));

        assertTrue(prompt.contains("AI 只输出观点字段"));
        assertTrue(prompt.contains("preferenceProfile"));
        assertTrue(prompt.contains("\"programId\""));
        assertTrue(prompt.contains("\"decision\""));
        assertTrue(prompt.contains("\"tradeoffs\""));
        assertTrue(prompt.contains("不要输出 schoolName"));
        assertFalse(prompt.contains("\"schoolName\":\"...\""));
    }

    @Test
    void shouldHydrateAiOpinionWithDatabaseFacts() {
        when(chatModel.chat(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
            {"summary":"稳妥优先","tiers":[{"level":"steady","label":"稳妥档","schools":[{"programId":123,"judgement":"steady","risk":"medium","decision":"主力稳妥","reason":"分数和地区匹配","pros":["地区符合"],"cons":["需核验"],"tradeoffs":["稳妥优先"],"recommendedAction":"加入备选"}]}]}
            """);
        when(recommendationMapper.selectProgramForRecommendation(123L)).thenReturn(detailRow(123L));

        Map<String, Object> report = builder.buildConversationReport(chatModel, "[]", "[{\"programId\":123}]", 300,
            Map.of("priorityPreference", "success_rate"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tiers = (List<Map<String, Object>>) report.get("tiers");
        @SuppressWarnings("unchecked")
        Map<String, Object> school = ((List<Map<String, Object>>) tiers.get(0).get("schools")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> opinion = (Map<String, Object>) school.get("opinion");

        assertEquals("北京信息科技大学", school.get("schoolName"));
        assertEquals("计算机学院", school.get("collegeName"));
        assertEquals(295, school.get("avgAdmittedScore"));
        assertEquals(5, school.get("avgScoreGap"));
        assertEquals("270-331", school.get("admissionRange"));
        assertEquals("主力稳妥", opinion.get("decision"));
    }

    private RowMap detailRow(long programId) {
        RowMap row = new RowMap();
        row.put("programId", programId);
        row.put("schoolName", "北京信息科技大学");
        row.put("collegeName", "计算机学院");
        row.put("programName", "计算机科学与技术");
        row.put("province", "北京");
        row.put("city", "北京");
        row.put("schoolTier", "普通本科");
        row.put("examCombo", "11408");
        row.put("scoreLine", 273);
        row.put("avgAdmittedScore", new BigDecimal("295"));
        row.put("admissionLow", 270);
        row.put("admissionHigh", 331);
        row.put("unifiedExamQuota", 28);
        row.put("planCount", 33);
        row.put("dataYear", 2025);
        row.put("dataCompleteness", "C");
        row.put("sourceOwner", "N诺");
        return row;
    }
}
```

- [ ] **Step 3: Run test and verify failure**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiReportBuilderImplTest test
```

Expected: FAIL because `AiReportBuilderImpl` does not exist.

- [ ] **Step 4: Implement `AiReportBuilderImpl`**

Create `AiReportBuilderImpl.java` with these public methods and helper structure:

```java
package com.ruoyi.postgrad.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.service.AiReportBuilder;
import dev.langchain4j.model.chat.ChatModel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AiReportBuilderImpl implements AiReportBuilder {
    @Autowired private RecommendationMapper recommendationMapper;

    @Override
    public Map<String, Object> buildConversationReport(ChatModel chatModel, String conversationJson,
        String poolJson, int estimatedScore, Map<String, Object> preferenceProfile) {
        String prompt = buildConversationPrompt(conversationJson, poolJson, preferenceProfile);
        return hydrateReportPrograms(parseReportJson(chatModel.chat(prompt), poolJson), estimatedScore, poolJson);
    }

    @Override
    public Map<String, Object> buildAnalyzeReport(ChatModel chatModel, String poolJson,
        int estimatedScore, Map<String, Object> preferenceProfile) {
        String prompt = buildAnalyzePrompt(poolJson, estimatedScore, preferenceProfile);
        return hydrateReportPrograms(parseReportJson(chatModel.chat(prompt), poolJson), estimatedScore, poolJson);
    }

    String buildConversationPrompt(String convJson, String poolJson, Map<String, Object> preferenceProfile) {
        return basePrompt(poolJson, preferenceProfile) + "\n## 对话历史\n" + convJson;
    }

    String buildAnalyzePrompt(String poolJson, int estimatedScore, Map<String, Object> preferenceProfile) {
        return basePrompt(poolJson, preferenceProfile) + "\n## 用户预估分\n" + estimatedScore;
    }
}
```

Then add private helpers from the previous plan into this class:

- `basePrompt(String poolJson, Map<String, Object> preferenceProfile)`
- `buildPoolSummary(String poolJson)`
- `parseReportJson(String aiResponse, String poolJson)`
- `ruleBasedFallback(String poolJson)`
- `hydrateReportPrograms(Map<String,Object> report, int estimatedScore, String poolJson)`
- `parsePoolMap(String poolJson)`
- `hydratedReportSchool(Map<String,Object> opinionSource, Map<String,Object> detail, int estimatedScore)`
- `buildOpinion(Map<String,Object> source)`
- `longValue`, `integerValue`, `admissionRange`, `stripMarkdown`

Use this exact `basePrompt` schema:

```java
private String basePrompt(String poolJson, Map<String, Object> preferenceProfile) {
    return """
        这不是对话。请直接输出推荐报告 JSON，不要回复确认语。

        ## preferenceProfile
        %s

        ## 候选学校事实摘要
        %s

        ## 要求
        1. 只能从候选列表中选学校，programId 必须与候选列表一致
        2. 按冲刺/稳妥/保底三档推荐，每档 1-3 所
        3. AI 只输出观点字段，事实字段由后端数据库补全
        4. 不要输出 schoolName、collegeName、programName、分数、招生人数等事实字段
        5. 推荐理由必须基于候选事实摘要和 preferenceProfile 的取舍

        ## 输出格式（严格 JSON）
        {"summary":"一句话总结","tiers":[{"level":"reach","label":"冲刺档","schools":[{"programId":1,"judgement":"small_reach","risk":"high","decision":"适合作为冲刺候选","reason":"推荐理由","pros":["优势"],"cons":["风险"],"tradeoffs":["取舍"],"recommendedAction":"行动建议"}]},{"level":"steady","label":"稳妥档","schools":[]},{"level":"safe","label":"保底档","schools":[]}]}
        """.formatted(JSON.toJSONString(defaultedPreferenceProfile(preferenceProfile)), buildPoolSummary(poolJson));
}
```

Use a concise `buildPoolSummary`: include `programId`、学校名、专业名、学院名、地区、层次、均分、均分差、最低录取、招生规模、完整度. Do not include source URLs or long text.

- [ ] **Step 5: Run builder tests**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiReportBuilderImplTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/AiReportBuilder.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiReportBuilderImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiReportBuilderImplTest.java
git commit -m "feat(ai-report): add shared report builder"
```

---

### Task 4: Delegate Synchronous Report Path To Shared Builder

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java`

- [ ] **Step 1: Inject shared builder**

Add field:

```java
@Autowired
private AiReportBuilder aiReportBuilder;
```

Import:

```java
import com.ruoyi.postgrad.service.AiReportBuilder;
```

- [ ] **Step 2: Add preference profile helper**

Add helper:

```java
private Map<String, Object> buildPreferenceProfile(Map<String, Object> profile) {
    Map<String, Object> pref = new LinkedHashMap<>();
    pref.put("riskPreference", profile.getOrDefault("riskPreference", "balanced"));
    pref.put("priorityPreference", profile.getOrDefault("priorityPreference", "success_rate"));
    pref.put("schoolTierPreference", profile.getOrDefault("schoolTierPreference", "no_strict_requirement"));
    pref.put("regionStrategy", profile.getOrDefault("regionStrategy", "no_limit"));
    pref.put("dataReliabilityPreference", profile.getOrDefault("dataReliabilityPreference", "medium"));
    pref.put("targetRegions", profile.getOrDefault("targetRegions", "不限"));
    return pref;
}
```

Update `loadUserProfile` to put the four new fields from `UserProfile`, with defaults.

- [ ] **Step 3: Replace inline report construction**

In `generateReport`, replace the block that calls `buildReportPrompt`, `parseReportJson`, `injectMatchScores`, and `validateAndNormalizeReport` with:

```java
Map<String, Object> reportJson = aiReportBuilder.buildConversationReport(
    chatModel,
    cleanedConvJson,
    poolJson != null ? poolJson : "[]",
    estimatedScore,
    buildPreferenceProfile(profile)
);
Map<String, Object> validated = validateAndNormalizeReport(reportJson, AiRecommendationTools.currentTrace());
```

- [ ] **Step 4: Keep old private methods only if still used**

Run:

```powershell
rg -n "buildReportPrompt|buildPoolSummary|parseReportJson|injectMatchScores|ruleBasedFallback" ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java
```

If any method is now unused in `AiRecommendationServiceImpl`, remove it from this class. Do not remove `validateAndNormalizeReport` if it is still used.

- [ ] **Step 5: Run tests**

Run:

```powershell
mvn -pl ruoyi-postgrad -Dtest=AiRecommendationServiceImplTest,AiReportBuilderImplTest test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImplTest.java
git commit -m "feat(ai-report): delegate sync reports to shared builder"
```

---

### Task 5: Delegate MQ Consumer Path To Shared Builder

**Files:**
- Modify: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java`
- Add: `ruoyi-admin/src/test/java/com/ruoyi/web/controller/postgrad/AiReportConsumerContractTest.java`

- [ ] **Step 1: Add static consumer contract test**

Create `AiReportConsumerContractTest.java`:

```java
package com.ruoyi.web.controller.postgrad;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class AiReportConsumerContractTest {
    @Test
    void consumerShouldUseSharedReportBuilder() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java"));

        assertTrue(source.contains("AiReportBuilder"));
        assertTrue(source.contains("buildConversationReport"));
        assertTrue(source.contains("buildAnalyzeReport"));
        assertFalse(source.contains("private String buildReportPrompt"));
        assertFalse(source.contains("private void injectFullData"));
    }
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=AiReportConsumerContractTest test
```

Expected: FAIL because the consumer still has private prompt/injection methods.

- [ ] **Step 3: Inject `AiReportBuilder`**

Add import:

```java
import com.ruoyi.postgrad.service.AiReportBuilder;
```

Add field:

```java
@Autowired private AiReportBuilder aiReportBuilder;
```

- [ ] **Step 4: Replace conversation report generation**

Replace:

```java
String reportPrompt = buildReportPrompt(cleanedConvJson, poolJson != null ? poolJson : "[]");
JSONObject reportJson = parseReportJson(chatModel, reportPrompt, poolJson);

injectFullData(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");
normalizeReport(reportJson);

String resultJsonStr = reportJson.toJSONString();
```

with:

```java
Map<String, Object> reportJson = aiReportBuilder.buildConversationReport(
    chatModel,
    cleanedConvJson,
    poolJson != null ? poolJson : "[]",
    estimatedScore,
    loadPreferenceProfile((Number) msg.get("userId"))
);

String resultJsonStr = JSON.toJSONString(reportJson);
```

If `userId` is not present in conversation messages, pass `Collections.emptyMap()` and document that analyze mode uses profile-backed preferences. Use:

```java
Map<String, Object> preferences = msg.get("userId") instanceof Number userId
    ? loadPreferenceProfile(userId)
    : Collections.emptyMap();
```

- [ ] **Step 5: Replace analyze report generation**

Replace prompt/parse/inject/normalize in `handleAnalyzeMessage` with:

```java
Map<String, Object> reportJson = aiReportBuilder.buildAnalyzeReport(
    chatModel,
    poolJson,
    estimatedScore,
    loadPreferenceProfile(userId)
);

String resultJsonStr = JSON.toJSONString(reportJson);
```

- [ ] **Step 6: Add preference loader in consumer**

Either reuse existing `loadProfileForAnalysis` by returning preference keys too, or add:

```java
private Map<String, Object> loadPreferenceProfile(Number userIdValue) {
    Map<String, Object> profile = userIdValue == null
        ? new LinkedHashMap<>()
        : loadProfileForAnalysis(userIdValue.longValue());
    Map<String, Object> pref = new LinkedHashMap<>();
    pref.put("riskPreference", profile.getOrDefault("riskPreference", "balanced"));
    pref.put("priorityPreference", profile.getOrDefault("priorityPreference", "success_rate"));
    pref.put("schoolTierPreference", profile.getOrDefault("schoolTierPreference", "no_strict_requirement"));
    pref.put("regionStrategy", profile.getOrDefault("regionStrategy", "no_limit"));
    pref.put("dataReliabilityPreference", profile.getOrDefault("dataReliabilityPreference", "medium"));
    pref.put("targetRegions", profile.getOrDefault("targetRegions", "不限"));
    return pref;
}
```

- [ ] **Step 7: Remove duplicate private methods**

Remove from `AiReportConsumer` after delegation:

- `buildReportPrompt`
- `buildPoolSummary`
- `parseReportJson`
- `ruleBasedFallback`
- `injectFullData`
- old `normalizeReport` and helpers only used by those removed methods

Keep helpers still needed by `loadProfileForAnalysis`, Redis handling, or error handling.

- [ ] **Step 8: Run admin contract test**

Run:

```powershell
mvn -pl ruoyi-admin -Dtest=AiReportConsumerContractTest test
```

Expected: PASS.

- [ ] **Step 9: Commit**

```powershell
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java ruoyi-admin/src/test/java/com/ruoyi/web/controller/postgrad/AiReportConsumerContractTest.java
git commit -m "feat(ai-report): delegate MQ reports to shared builder"
```

---

### Task 6: Frontend Normalize Hydrated Opinion Reports

**Files:**
- Modify: `user-ui/src/utils/aiReport.js`
- Add: `user-ui/src/utils/aiReport.opinion.test.mjs`
- Modify if needed: `user-ui/src/views/AiReport.vue`

- [ ] **Step 1: Add opinion normalization test**

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
        decision: '主力稳妥',
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
assert.equal(school.decision, '主力稳妥')
assert.deepEqual(school.evidence, ['分数匹配，地区符合偏好', '分数匹配度较高'])
assert.deepEqual(school.risks, ['数据完整度为 C，需要核验'])
assert.deepEqual(school.tradeoffs, ['上岸稳定性优先于学校层次'])
assert.equal(school.recommendedAction, '加入备选并核验官网')
```

- [ ] **Step 2: Run and verify failure**

Run:

```powershell
node user-ui\src\utils\aiReport.opinion.test.mjs
```

Expected: FAIL because `normalizeSchool` does not merge `opinion` yet.

- [ ] **Step 3: Update `normalizeSchool`**

Replace `normalizeSchool` with:

```js
function normalizeSchool(school) {
  const opinion = school.opinion || {}
  const opinionJudgement = opinion.judgement || school.judgement || school.aiJudgement
  const judgement = JUDGEMENT_LABELS[opinionJudgement] ? opinionJudgement : 'data_insufficient_pending'
  const verificationStatus = VERIFICATION_STATUS_LABELS[school.verificationStatus]
    ? school.verificationStatus
    : 'local_data_only'
  const evidence = [
    ...toArray(opinion.reason || school.reason),
    ...toArray(opinion.pros || school.pros)
  ]
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

- [ ] **Step 4: Run frontend tests**

Run:

```powershell
node user-ui\src\utils\aiReport.opinion.test.mjs
node user-ui\src\views\AiReport.cards.test.mjs
node user-ui\src\views\AiReport.loading.test.mjs
node user-ui\src\views\Results.backup.test.mjs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add user-ui/src/utils/aiReport.js user-ui/src/utils/aiReport.opinion.test.mjs
git commit -m "feat(ai-report): normalize hydrated opinion reports"
```

---

### Task 7: Full Verification

**Files:**
- Verify backend and frontend files touched by Tasks 1-6.

- [ ] **Step 1: Run backend module tests**

Run:

```powershell
mvn -pl ruoyi-postgrad test
mvn -pl ruoyi-admin -Dtest=AiReportConsumerContractTest test
```

Expected: PASS.

- [ ] **Step 2: Run frontend tests and build**

Run:

```powershell
node user-ui\src\views\Profile.preferences.test.mjs
node user-ui\src\utils\aiReport.opinion.test.mjs
node user-ui\src\views\AiReport.cards.test.mjs
node user-ui\src\views\AiReport.loading.test.mjs
node user-ui\src\views\Results.backup.test.mjs
cd user-ui
npm run build
```

Expected: PASS. Existing Vite warnings are acceptable if build exits successfully.

- [ ] **Step 3: Inspect duplicate logic is gone**

Run:

```powershell
rg -n "private String buildReportPrompt|private void injectFullData" ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java
```

Expected: no matches.

- [ ] **Step 4: Inspect working tree**

Run:

```powershell
git status --short
```

Expected: only intentional files remain modified. Existing unrelated UI changes from prior work may still appear; do not revert them.

---

## Self-Review

- Spec coverage: This revised plan covers profile preferences, shared report builder, synchronous path, MQ path, backend hydration, frontend opinion normalization, and verification.
- Reviewer feedback coverage: The MQ consumer path is explicit in Task 5. Pool summary is concise and limited to judgement-relevant facts. Opinion compatibility is mostly handled in frontend normalization. User preference fields are now first-class profile fields.
- Completion scan: No unresolved filler task remains.
- Type consistency: Preference fields use `priorityPreference`, `schoolTierPreference`, `regionStrategy`, and `dataReliabilityPreference`; report fields use `programId`, `opinion`, `judgement`, `risk`, `decision`, `reason`, `pros`, `cons`, `tradeoffs`, and `recommendedAction`.
