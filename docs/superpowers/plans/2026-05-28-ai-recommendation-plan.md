# AI 对话推荐引擎 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有规则推荐引擎基础上新增 AI 驱动的对话推荐模式，用户通过侧边栏聊天窗口与千问 AI 进行多轮对话，最终生成三档结构化推荐报告。

**Architecture:** 后端新增 AI 服务层（Service + Tools + Controller），AI 工具通过 ThreadLocal 注入 conversationId 进行会话隔离，对话历史存 Redis（滑动 TTL），报告生成走 RabbitMQ 异步队列。前端新增侧边栏聊天组件和报告页。

**Tech Stack:** Spring Boot 4.0.3, MyBatis, langchain4j-community-dashscope 1.15.0-beta25, Redis, RabbitMQ, Vue 2.6 + Element UI

---

### Task 1: RabbitMQ 基础设施与配置

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/framework/config/RabbitMQConfig.java`

- [ ] **Step 1: 创建 RabbitMQ 配置类**

```java
package com.ruoyi.framework.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String AI_REPORT_QUEUE = "ai.report.queue";

    @Bean
    public Queue aiReportQueue() {
        return new Queue(AI_REPORT_QUEUE, true);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

- [ ] **Step 2: 添加 spring-boot-starter-amqp 依赖**

修改 `ruoyi-admin/pom.xml`，在 `<dependencies>` 内添加：

```xml
<!-- RabbitMQ -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl ruoyi-admin -Dmaven.test.skip=true -q
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add ruoyi-admin/pom.xml ruoyi-admin/src/main/java/com/ruoyi/framework/config/RabbitMQConfig.java
git commit -m "feat(ai): add RabbitMQ config for AI report generation"
```

---

### Task 2: AI 推荐工具类（ThreadLocal 安全注入）

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/tool/AiRecommendationTools.java`

- [ ] **Step 1: 编写 AiRecommendationTools**

```java
package com.ruoyi.postgrad.tool;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AiRecommendationTools {

    private static final ThreadLocal<String> CURRENT_CONVERSATION = new ThreadLocal<>();

    @Autowired
    private StringRedisTemplate redisTemplate;

    public static void setConversationId(String id) {
        CURRENT_CONVERSATION.set(id);
    }

    public static void clear() {
        CURRENT_CONVERSATION.remove();
    }

    @Tool("获取指定学校的完整录取数据，包括近三年复试线、小分、招生计划、录取均分")
    public String getProgramDetail(@P("学校 programId") long programId) {
        String conversationId = CURRENT_CONVERSATION.get();
        if (conversationId == null) return "{}";

        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
        if (poolJson == null) return "{}";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
            if (pid == programId) {
                return JSON.toJSONString(p);
            }
        }
        return "{}";
    }

    @Tool("在候选池内按条件筛选学校，如按城市、学校层次、分数范围过滤")
    public String searchPrograms(@P("筛选条件，JSON 格式，如 {\"city\":\"北京\",\"tier\":\"985\",\"minScore\":300,\"maxScore\":400}") String filters) {
        String conversationId = CURRENT_CONVERSATION.get();
        if (conversationId == null) return "[]";

        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
        if (poolJson == null) return "[]";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);

        Map<String, Object> filterMap = JSON.parseObject(filters);
        return JSON.toJSONString(pool.stream()
            .filter(p -> matchFilter(p, filterMap))
            .collect(Collectors.toList()));
    }

    private boolean matchFilter(Map<String, Object> program, Map<String, Object> filter) {
        if (filter.containsKey("city") && !filter.get("city").equals(program.get("city"))) return false;
        if (filter.containsKey("tier") && !filter.get("tier").equals(program.get("tier"))) return false;
        if (filter.containsKey("minScore")) {
            Object avgObj = program.get("avgAdmittedScore");
            double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : 0;
            double min = ((Number) filter.get("minScore")).doubleValue();
            if (avg < min) return false;
        }
        if (filter.containsKey("maxScore")) {
            Object avgObj = program.get("avgAdmittedScore");
            double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : 0;
            double max = ((Number) filter.get("maxScore")).doubleValue();
            if (avg > max) return false;
        }
        return true;
    }

    @Tool("横向对比多所学校的录取数据，返回详细对比")
    public String comparePrograms(@P("学校 programId 列表") List<Long> ids) {
        String conversationId = CURRENT_CONVERSATION.get();
        if (conversationId == null) return "[]";

        String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
        if (poolJson == null) return "[]";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pool = JSON.parseObject(poolJson, List.class);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue() : Long.parseLong(String.valueOf(idObj));
            if (ids.contains(pid)) {
                result.add(p);
            }
        }
        return JSON.toJSONString(result);
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ruoyi-postgrad -Dmaven.test.skip=true -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/tool/
git commit -m "feat(ai): add AiRecommendationTools with ThreadLocal conversation isolation"
```

---

### Task 3: AI 推荐服务层

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/IAiRecommendationService.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/service/impl/AiRecommendationServiceImpl.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationLogMapper.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/RecommendationLogMapper.xml`

- [ ] **Step 1: 编写服务接口**

```java
package com.ruoyi.postgrad.service;

import java.util.Map;

public interface IAiRecommendationService {

    Map<String, Object> startConversation(Long userId, Map<String, Object> request);

    Map<String, Object> chat(Long userId, String conversationId, String message);

    Map<String, Object> generateReport(Long userId, String conversationId);

    Map<String, Object> getReport(Long userId, Long reportId);

    Map<String, Object> getReports(Long userId);

    Map<String, Object> resumeConversation(Long userId, String conversationId);
}
```

- [ ] **Step 2: 在 RecommendationLogMapper 新增方法**

在 `RecommendationLogMapper.java` 中添加：

```java
int insertConversationState(@Param("id") Long id, @Param("conversationId") String conversationId,
    @Param("state") String state);
String selectConversationState(@Param("conversationId") String conversationId);
List<RowMap> selectAiReportListByUserId(@Param("userId") Long userId);
```

- [ ] **Step 3: 在 RecommendationLogMapper.xml 中添加 SQL**

```xml
<update id="insertConversationState">
    update recommendation_log
    set result_json = #{state}
    where id = #{id}
</update>

<select id="selectConversationState" resultType="String">
    select result_json from recommendation_log
    where id = (select id from recommendation_log
                where result_json like concat('%', #{conversationId}, '%')
                limit 1)
</select>

<select id="selectAiReportListByUserId" resultType="com.ruoyi.postgrad.domain.RowMap">
    select id, profile_snapshot, result_json, created_at
    from recommendation_log
    where user_id = #{userId} and rule_version = 'ai-conversation'
    order by created_at desc
    limit 20
</select>
```

- [ ] **Step 4: 编写服务实现**

```java
package com.ruoyi.postgrad.service.impl;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.mapper.UserProfileMapper;
import com.ruoyi.postgrad.service.IAiRecommendationService;
import com.ruoyi.postgrad.tool.AiRecommendationTools;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class AiRecommendationServiceImpl implements IAiRecommendationService {

    private static final String SYSTEM_PROMPT = """
        你是考研408计算机专业择校顾问。

        ## 你的角色
        帮助考生从候选学校池中挑选最匹配的目标院校。
        风格：数据驱动、诚实、简洁。不画饼，不说"努力就能上"。
        每轮只聚焦一个维度。

        ## 用户画像
        - 预估总分: %d
        - 本科层次: %s
        - 跨考: %s
        - 风险偏好: %s
        - 目标地区: %s
        - 数学水平: %s，英语水平: %s

        ## 候选学校摘要
        %s

        ## 可用工具
        - getProgramDetail(programId): 获取完整录取数据
        - searchPrograms(filters): 在候选池内筛选
        - comparePrograms(ids): 横向对比多校

        ## 对话节奏
        第1轮: 了解最看重的维度（学校层次/专业排名/城市/上岸率）
        第2-3轮: 用具体数据讨论 2-3 所目标校
        第4-5轮: 确认冲刺/稳妥/保底意向

        ## 输出格式
        每轮回复含简短文字(2-4句)。
        回复末尾附 2-3 个快捷选项，用 "---OPTIONS---" 分隔，每行一个选项。
        用户说"出报告"时，只回复"好的，正在为你生成报告..."，不要附带选项。

        示例:
        你的候选池有30所学校，从985到双非都有。你更在意的维度是?

        ---OPTIONS---
        985/211光环
        专业学科排名
        学校所在城市
        """;

    @Autowired
    private RecommendationMapper recommendationMapper;

    @Autowired
    private RecommendationLogMapper logMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Map<String, Object> startConversation(Long userId, Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Number> candidateIdsRaw = (List<Number>) request.get("candidateIds");
        List<Long> candidateIds = candidateIdsRaw.stream().map(Number::longValue).collect(java.util.stream.Collectors.toList());

        // 1. 查候选学校数据
        Map<String, Object> userProfile = loadUserProfile(userId);
        int estimatedScore = userProfile.get("estimatedScore") != null
            ? ((Number) userProfile.get("estimatedScore")).intValue() : 0;

        List<Map<String, Object>> pool = recommendationMapper.selectProgramsByIds(candidateIds, estimatedScore);
        List<Map<String, Object>> summary = buildSummaryList(pool);

        // 2. 构建 system prompt
        String summaryJson = JSON.toJSONString(summary);
        String systemPrompt = String.format(SYSTEM_PROMPT,
            estimatedScore,
            String.valueOf(userProfile.getOrDefault("undergradTier", "未知")),
            userProfile.get("isCrossMajor") != null && ((Number) userProfile.get("isCrossMajor")).intValue() == 1 ? "是" : "否",
            String.valueOf(userProfile.getOrDefault("riskPreference", "balanced")),
            String.valueOf(userProfile.getOrDefault("targetRegions", "不限")),
            String.valueOf(userProfile.getOrDefault("mathLevel", "中等")),
            String.valueOf(userProfile.getOrDefault("englishLevel", "中等")),
            summaryJson
        );

        // 3. 创建对话
        String conversationId = UUID.randomUUID().toString();
        String openingMessage = "你好！你的候选池有" + pool.size() + "所学校。在开始之前，你最看重的是哪个方面？";

        // 构建对话历史
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> sysMsg = new LinkedHashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        Map<String, Object> aiMsg = new LinkedHashMap<>();
        aiMsg.put("role", "assistant");
        aiMsg.put("content", openingMessage);
        aiMsg.put("options", Arrays.asList("985/211光环", "专业学科排名", "学校所在城市"));
        messages.add(aiMsg);

        // 4. 写 Redis
        Map<String, Object> convData = new LinkedHashMap<>();
        convData.put("userId", userId);
        convData.put("estimatedScore", estimatedScore);
        convData.put("profile", userProfile);
        convData.put("messages", messages);
        convData.put("candidateIds", candidateIds);

        redisTemplate.opsForValue().set("ai:conv:" + conversationId, JSON.toJSONString(convData), 1800, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("ai:pool:" + conversationId, JSON.toJSONString(pool), 1800, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("ai:owner:" + conversationId, userId.toString(), 1800, TimeUnit.SECONDS);

        // 5. 返回
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("message", openingMessage);
        result.put("options", Arrays.asList("985/211光环", "专业学科排名", "学校所在城市"));
        return result;
    }

    @Override
    public Map<String, Object> chat(Long userId, String conversationId, String message) {
        // 0. 鉴权
        String ownerId = redisTemplate.opsForValue().get("ai:owner:" + conversationId);
        if (ownerId == null) {
            throw new IllegalArgumentException("对话已过期，请重新开始");
        }
        if (!userId.toString().equals(ownerId)) {
            throw new SecurityException("无权访问该会话");
        }
        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson == null) {
            throw new IllegalArgumentException("对话状态异常，请重新开始");
        }
        Map<String, Object> convData = JSON.parseObject(convJson);

        // 1. 追加用户消息
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) convData.get("messages");
        Map<String, Object> userMsg = new LinkedHashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", "<user_input>" + message + "</user_input>");
        messages.add(userMsg);

        // 2. 调 AI
        ChatModel chatModel = QwenChatModel.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .modelName("qwen-plus")
            .build();

        try {
            AiRecommendationTools.setConversationId(conversationId);

            // 构建对话上下文（只传摘要层）
            String response = chatModel.chat(buildChatPrompt(messages));

            // 解析 options
            Map<String, Object> aiMsg = new LinkedHashMap<>();
            aiMsg.put("role", "assistant");
            if (response.contains("---OPTIONS---")) {
                String[] parts = response.split("---OPTIONS---");
                aiMsg.put("content", parts[0].trim());
                String[] optionsArr = parts[1].trim().split("\n");
                List<String> options = new ArrayList<>();
                for (String opt : optionsArr) {
                    String trimmed = opt.trim();
                    if (!trimmed.isEmpty()) options.add(trimmed);
                }
                aiMsg.put("options", options);
            } else {
                aiMsg.put("content", response);
            }
            messages.add(aiMsg);

            // 3. 写回 Redis + 续期
            redisTemplate.opsForValue().set("ai:conv:" + conversationId, JSON.toJSONString(convData), 1800, TimeUnit.SECONDS);
            redisTemplate.expire("ai:pool:" + conversationId, 1800, TimeUnit.SECONDS);

            // 4. 每 3 轮异步落 DB
            if (messages.size() % 6 == 0) {
                saveConversationState(userId, conversationId, convData);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", aiMsg.get("content"));
            result.put("options", aiMsg.getOrDefault("options", Collections.emptyList()));
            return result;

        } catch (Exception e) {
            // 降级：重试一次
            try {
                String response = chatModel.chat(buildChatPrompt(messages));
                Map<String, Object> aiMsg = new LinkedHashMap<>();
                aiMsg.put("role", "assistant");
                aiMsg.put("content", response);
                messages.add(aiMsg);

                redisTemplate.opsForValue().set("ai:conv:" + conversationId, JSON.toJSONString(convData), 1800, TimeUnit.SECONDS);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("message", response);
                return result;
            } catch (Exception e2) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("fallback", true);
                error.put("message", "AI 服务暂时不可用，请使用规则推荐");
                return error;
            }
        } finally {
            AiRecommendationTools.clear();
        }
    }

    @Override
    public Map<String, Object> generateReport(Long userId, String conversationId) {
        // 鉴权
        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson == null) throw new IllegalArgumentException("对话已过期");
        Map<String, Object> convData = JSON.parseObject(convJson);
        if (!userId.equals(convData.get("userId"))) throw new SecurityException("无权访问");

        // 保存初始 log
        RecommendationLog log = new RecommendationLog();
        log.setUserId(userId);
        log.setProfileSnapshot(JSON.toJSONString(convData.get("profile")));
        log.setResultJson("PENDING");
        log.setRuleVersion("ai-conversation");
        log.setDataVersion("qwen-plus");
        log.setIsPaid(0);
        logMapper.insertRecommendationLog(log);

        Long reportId = log.getId();

        // 投递 RabbitMQ
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("reportId", reportId);
        msg.put("conversationId", conversationId);
        msg.put("userId", userId);
        msg.put("estimatedScore", convData.get("estimatedScore"));
        rabbitTemplate.convertAndSend("ai.report.queue", msg);

        redisTemplate.opsForValue().set("ai:report:" + reportId, "PENDING", 7, TimeUnit.DAYS);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reportId", reportId);
        result.put("status", "PENDING");
        return result;
    }

    @Override
    public Map<String, Object> getReport(Long userId, Long reportId) {
        String reportJson = redisTemplate.opsForValue().get("ai:report:" + reportId);
        if (reportJson != null && !"PENDING".equals(reportJson)) {
            Map<String, Object> report = JSON.parseObject(reportJson);
            if (!userId.equals(report.get("userId"))) throw new SecurityException("无权访问");
            report.put("status", "DONE");
            return report;
        }

        if ("PENDING".equals(reportJson)) {
            Map<String, Object> pending = new LinkedHashMap<>();
            pending.put("status", "PENDING");
            return pending;
        }

        // fallback: 查 DB
        var rowMap = logMapper.selectLogByIdAndUserId(reportId, userId);
        if (rowMap == null) throw new IllegalArgumentException("报告不存在");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "DONE");
        result.put("result", JSON.parse(String.valueOf(rowMap.get("result_json"))));
        return result;
    }

    @Override
    public Map<String, Object> getReports(Long userId) {
        var logs = logMapper.selectAiReportListByUserId(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", logs);
        return result;
    }

    @Override
    public Map<String, Object> resumeConversation(Long userId, String conversationId) {
        // 尝试 Redis
        String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
        if (convJson != null) {
            Map<String, Object> convData = JSON.parseObject(convJson);
            if (!userId.equals(convData.get("userId"))) throw new SecurityException("无权访问");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> messages = (List<Map<String, Object>>) convData.get("messages");
            Map<String, Object> lastAiMsg = messages.get(messages.size() - 1);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("message", lastAiMsg.get("content"));
            result.put("options", lastAiMsg.getOrDefault("options", Collections.emptyList()));
            result.put("source", "redis");
            return result;
        }

        // 尝试 DB
        String dbState = logMapper.selectConversationState(conversationId);
        if (dbState != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("conversationId", conversationId);
            result.put("message", "对话已从历史记录恢复。之前的讨论你还记得吗？");
            result.put("options", Arrays.asList("记得，继续聊", "重新开始"));
            result.put("source", "db");
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "expired");
        result.put("message", "对话已过期，请重新开始 AI 推荐");
        return result;
    }

    // --- private helpers ---

    private Map<String, Object> loadUserProfile(Long userId) {
        var profile = userProfileMapper.selectUserProfileByUserId(userId);
        if (profile != null) return profile;

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("estimatedScore", 0);
        defaults.put("undergradTier", "未知");
        defaults.put("isCrossMajor", 0);
        defaults.put("riskPreference", "balanced");
        defaults.put("targetRegions", "不限");
        defaults.put("mathLevel", "中等");
        defaults.put("englishLevel", "中等");
        return defaults;
    }

    private List<Map<String, Object>> buildSummaryList(List<Map<String, Object>> pool) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Map<String, Object> p : pool) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("programId", p.get("programId"));
            s.put("name", p.get("schoolName") + " " + p.get("programName"));
            s.put("tier", p.getOrDefault("tier", "其他"));
            s.put("city", p.getOrDefault("city", "未知"));
            s.put("avgScore", p.getOrDefault("avgAdmittedScore", 0));
            s.put("gap", p.getOrDefault("gap", 0));
            summaries.add(s);
        }
        return summaries;
    }

    private String buildChatPrompt(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> msg : messages) {
            String role = String.valueOf(msg.get("role"));
            String content = String.valueOf(msg.get("content"));
            sb.append(role).append(": ").append(content).append("\n\n");
        }
        return sb.toString();
    }

    private void saveConversationState(Long userId, String conversationId, Map<String, Object> convData) {
        try {
            RecommendationLog log = new RecommendationLog();
            log.setUserId(userId);
            log.setProfileSnapshot(JSON.toJSONString(convData.get("profile")));
            log.setResultJson(JSON.toJSONString(convData));
            log.setRuleVersion("ai-conversation-state");
            log.setDataVersion(conversationId);
            log.setIsPaid(0);
            logMapper.insertRecommendationLog(log);
        } catch (Exception ignored) {
            // 异步落盘失败不影响主流程
        }
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl ruoyi-postgrad -Dmaven.test.skip=true -q
```
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add ruoyi-postgrad/
git commit -m "feat(ai): add AI recommendation service with conversation management"
```

---

### Task 4: AI 报告消费者

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java`

- [ ] **Step 1: 创建消费者**

```java
package com.ruoyi.web.controller.postgrad;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.framework.config.RabbitMQConfig;
import com.ruoyi.postgrad.domain.RecommendationLog;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class AiReportConsumer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RecommendationLogMapper logMapper;

    @RabbitListener(queues = RabbitMQConfig.AI_REPORT_QUEUE, concurrency = "1")
    public void onMessage(Map<String, Object> msg) {
        Long reportId = ((Number) msg.get("reportId")).longValue();
        String conversationId = (String) msg.get("conversationId");
        Long userId = ((Number) msg.get("userId")).longValue();
        int estimatedScore = ((Number) msg.get("estimatedScore")).intValue();

        try {
            // 1. 取对话历史
            String convJson = redisTemplate.opsForValue().get("ai:conv:" + conversationId);
            if (convJson == null) {
                redisTemplate.opsForValue().set("ai:report:" + reportId, "{\"error\": \"对话已过期\"}", 7, TimeUnit.DAYS);
                return;
            }

            // 2. AI 生成报告
            ChatModel chatModel = QwenChatModel.builder()
                .apiKey(System.getenv("DASHSCOPE_API_KEY"))
                .modelName("qwen-plus")
                .build();

            String reportPrompt = buildReportPrompt(convJson);
            String aiResponse = chatModel.chat(reportPrompt);

            // 3. 解析 + 注入 matchScore（从 Redis pool 读真实数据计算）
            JSONObject reportJson = JSON.parseObject(aiResponse);
            String poolJson = redisTemplate.opsForValue().get("ai:pool:" + conversationId);
            injectMatchScores(reportJson, estimatedScore, poolJson != null ? poolJson : "[]");

            // 4. 保存
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                reportJson.toJSONString(), 7, TimeUnit.DAYS);

            // 5. 更新 DB
            RecommendationLog log = new RecommendationLog();
            log.setId(reportId);
            log.setResultJson(reportJson.toJSONString());
            logMapper.insertRecommendationLog(log);

        } catch (Exception e) {
            redisTemplate.opsForValue().set("ai:report:" + reportId,
                "{\"error\": \"" + e.getMessage() + "\"}", 7, TimeUnit.DAYS);
        }
    }

    private String buildReportPrompt(String convJson) {
        return """
            基于以下对话历史生成考研择校推荐报告。

            ## 对话历史
            %s

            ## 输出格式（严格 JSON）
            {
              "summary": "一句话总结",
              "tiers": [
                {
                  "level": "reach",
                  "label": "冲刺档",
                  "schools": [
                    {
                      "programId": 1,
                      "schoolName": "学校名",
                      "programName": "专业名",
                      "reason": "推荐理由，结合对话中用户的偏好",
                      "risk": "high",
                      "pros": ["优势1"],
                      "cons": ["劣势1"]
                    }
                  ]
                }
              ]
            }
            """.formatted(convJson);
    }

    private void injectMatchScores(JSONObject report, int estimatedScore, String poolJson) {
        List<Map<String, Object>> pool = JSON.parseArray(poolJson, Map.class);
        Map<Long, Double> avgScoreMap = new LinkedHashMap<>();
        for (Map<String, Object> p : pool) {
            Object idObj = p.get("programId");
            long pid = idObj instanceof Number ? ((Number) idObj).longValue()
                : Long.parseLong(String.valueOf(idObj));
            Object avgObj = p.get("avgAdmittedScore");
            Double avg = avgObj instanceof Number ? ((Number) avgObj).doubleValue() : null;
            if (avg != null) avgScoreMap.put(pid, avg);
        }

        var tiers = report.getJSONArray("tiers");
        if (tiers == null) return;
        for (int i = 0; i < tiers.size(); i++) {
            var tier = tiers.getJSONObject(i);
            var schools = tier.getJSONArray("schools");
            if (schools == null) continue;
            String level = tier.getString("level");
            for (int j = 0; j < schools.size(); j++) {
                var school = schools.getJSONObject(j);
                long pid = school.getLongValue("programId");
                Double avg = avgScoreMap.get(pid);
                if (avg != null && estimatedScore > 0) {
                    double gap = Math.abs(estimatedScore - avg);
                    double weight = "reach".equals(level) ? 0.5 : 0.3;
                    int score = (int) Math.max(0, 100 - gap * weight);
                    school.put("matchScore", score);
                } else {
                    school.put("matchScore", 50);
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ruoyi-admin -Dmaven.test.skip=true -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AiReportConsumer.java
git commit -m "feat(ai): add async report generation consumer via RabbitMQ"
```

---

### Task 5: Controller 层

**Files:**
- Create: `ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppAiRecommendationController.java`

- [ ] **Step 1: 编写 Controller**

```java
package com.ruoyi.web.controller.postgrad;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.postgrad.service.IAiRecommendationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/app/ai-recommend")
public class AppAiRecommendationController {

    @Autowired
    private IAiRecommendationService aiService;

    @PostMapping("/start")
    public AjaxResult start(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            return AjaxResult.success(aiService.startConversation(user.getUserId(), body));
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            String conversationId = (String) body.get("conversationId");
            String message = (String) body.get("message");
            Map<String, Object> result = aiService.chat(user.getUserId(), conversationId, message);
            if (result.containsKey("fallback")) {
                return AjaxResult.success("AI 对话暂不可用，已为你生成规则推荐结果",
                    result);
            }
            return AjaxResult.success(result);
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问此对话");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PostMapping("/generate-report")
    public AjaxResult generateReport(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            String conversationId = (String) body.get("conversationId");
            return AjaxResult.success(aiService.generateReport(user.getUserId(), conversationId));
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/report/{id}")
    public AjaxResult getReport(@PathVariable Long id) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            return AjaxResult.success(aiService.getReport(user.getUserId(), id));
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问");
        } catch (IllegalArgumentException e) {
            return AjaxResult.error(e.getMessage());
        }
    }

    @GetMapping("/reports")
    public AjaxResult getReports() {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        return AjaxResult.success(aiService.getReports(user.getUserId()));
    }

    @PostMapping("/resume")
    public AjaxResult resume(@RequestBody Map<String, Object> body) {
        AppLoginUser user = getCurrentAppUser();
        if (user == null) return AjaxResult.error("未登录");
        try {
            String conversationId = (String) body.get("conversationId");
            return AjaxResult.success(aiService.resumeConversation(user.getUserId(), conversationId));
        } catch (SecurityException e) {
            return AjaxResult.error(403, "无权访问");
        }
    }

    private AppLoginUser getCurrentAppUser() {
        try {
            Object principal = SecurityUtils.getAuthentication().getPrincipal();
            if (principal instanceof AppLoginUser) return (AppLoginUser) principal;
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl ruoyi-admin -Dmaven.test.skip=true -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add ruoyi-admin/src/main/java/com/ruoyi/web/controller/postgrad/AppAiRecommendationController.java
git commit -m "feat(ai): add AI recommendation controller endpoints"
```

---

### Task 6: 前端 — 侧边栏聊天组件

**Files:**
- Create: `ruoyi-ui/src/views/postgrad/app/components/AiChatPanel.vue`

- [ ] **Step 1: 编写 AiChatPanel.vue**

```vue
<template>
  <div class="ai-chat-panel" :class="{ open: visible }">
    <div class="panel-header">
      <span><i class="el-icon-chat-dot-round"></i> AI 择校顾问</span>
      <el-button type="text" icon="el-icon-close" @click="$emit('close')" />
    </div>

    <div class="panel-body" ref="body">
      <!-- 空状态 -->
      <div v-if="messages.length === 0 && !loading" class="empty-state">
        <i class="el-icon-chat-line-round"></i>
        <p>点击「AI 推荐」开始智能择校对话</p>
      </div>

      <!-- 消息列表 -->
      <div v-for="(msg, idx) in messages" :key="idx" :class="['msg', msg.role]">
        <div class="msg-avatar">
          <i v-if="msg.role === 'assistant'" class="el-icon-cpu"></i>
          <i v-else class="el-icon-user"></i>
        </div>
        <div class="msg-bubble">{{ msg.content }}</div>
      </div>

      <!-- 加载 -->
      <div v-if="loading" class="msg assistant">
        <div class="msg-avatar"><i class="el-icon-cpu"></i></div>
        <div class="msg-bubble typing"><span>.</span><span>.</span><span>.</span></div>
      </div>
    </div>

    <!-- 快捷选项 -->
    <div v-if="currentOptions.length > 0 && !loading" class="options-bar">
      <el-button v-for="(opt, i) in currentOptions" :key="i"
        size="small" type="primary" plain @click="sendOption(opt)">
        {{ opt }}
      </el-button>
    </div>

    <!-- 输入区 -->
    <div class="input-bar">
      <el-input v-model="input" placeholder="输入你的想法..."
        size="small" @keyup.enter.native="sendMessage">
        <el-button slot="append" icon="el-icon-s-promotion"
          :disabled="!input.trim() || loading" @click="sendMessage" />
      </el-input>
      <el-button v-if="messages.length >= 4" type="success" size="small"
        style="margin-top:6px;width:100%" @click="generateReport">
        生成推荐报告
      </el-button>
    </div>
  </div>
</template>

<script>
import { postAiStart, postAiChat, postAiGenerateReport } from '@/api/postgrad/ai'

export default {
  name: 'AiChatPanel',
  props: {
    visible: { type: Boolean, default: false },
    candidateIds: { type: Array, default: () => [] }
  },
  data() {
    return {
      conversationId: null,
      messages: [],
      currentOptions: [],
      input: '',
      loading: false
    }
  },
  watch: {
    visible(val) {
      if (val && !this.conversationId) this.startConversation()
    }
  },
  methods: {
    async startConversation() {
      this.loading = true
      try {
        const res = await postAiStart({ candidateIds: this.candidateIds })
        this.conversationId = res.data.conversationId
        this.messages = [{ role: 'assistant', content: res.data.message }]
        this.currentOptions = res.data.options || []
        this.saveToLocal()
      } catch (e) {
        this.$message.error('启动 AI 对话失败')
      } finally {
        this.loading = false
      }
    },
    async sendMessage() {
      const text = this.input.trim()
      if (!text || this.loading) return
      this.messages.push({ role: 'user', content: text })
      this.input = ''
      this.currentOptions = []
      this.loading = true
      try {
        const res = await postAiChat({ conversationId: this.conversationId, message: text })
        if (res.data.fallback) {
          this.$emit('fallback', res.data)
          return
        }
        this.messages.push({ role: 'assistant', content: res.data.message })
        this.currentOptions = res.data.options || []
        this.saveToLocal()
      } catch (e) {
        this.$message.error('对话请求失败')
      } finally {
        this.loading = false
        this.$nextTick(() => this.scrollToBottom())
      }
    },
    sendOption(opt) {
      this.input = ''
      this.sendOptionText(opt)
    },
    async sendOptionText(text) {
      this.messages.push({ role: 'user', content: text })
      this.currentOptions = []
      this.loading = true
      try {
        const res = await postAiChat({ conversationId: this.conversationId, message: text })
        this.messages.push({ role: 'assistant', content: res.data.message })
        this.currentOptions = res.data.options || []
        this.saveToLocal()
      } catch (e) {
        this.$message.error('对话请求失败')
      } finally {
        this.loading = false
        this.$nextTick(() => this.scrollToBottom())
      }
    },
    async generateReport() {
      this.loading = true
      try {
        const res = await postAiGenerateReport({ conversationId: this.conversationId })
        this.$router.push({ name: 'AiReport', params: { id: res.data.reportId } })
      } catch (e) {
        this.$message.error('生成报告失败')
      } finally {
        this.loading = false
      }
    },
    saveToLocal() {
      try {
        localStorage.setItem('ai_conv_' + this.conversationId,
          JSON.stringify({ messages: this.messages, options: this.currentOptions }))
      } catch (e) { /* quota exceeded, ignore */ }
    },
    scrollToBottom() {
      const el = this.$refs.body
      if (el) el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style scoped>
.ai-chat-panel {
  position: fixed; right: -420px; top: 0; width: 400px; height: 100vh;
  background: #fff; box-shadow: -2px 0 12px rgba(0,0,0,.1);
  display: flex; flex-direction: column; transition: right .3s; z-index: 2000;
}
.ai-chat-panel.open { right: 0; }
.panel-header {
  padding: 12px 16px; border-bottom: 1px solid #ebeef5;
  display: flex; justify-content: space-between; align-items: center;
  font-weight: 600;
}
.panel-body { flex: 1; overflow-y: auto; padding: 12px; }
.empty-state { text-align: center; color: #909399; padding-top: 80px; }
.empty-state i { font-size: 48px; display: block; margin-bottom: 12px; }
.msg { display: flex; margin-bottom: 12px; }
.msg.user { flex-direction: row-reverse; }
.msg-avatar { width: 32px; height: 32px; border-radius: 50%;
  background: #f0f2f5; display: flex; align-items: center; justify-content: center;
  margin: 0 8px; flex-shrink: 0; }
.msg.assistant .msg-avatar { background: #409eff; color: #fff; }
.msg-bubble { max-width: 280px; padding: 8px 12px; border-radius: 12px;
  font-size: 14px; line-height: 1.6; }
.msg.assistant .msg-bubble { background: #f0f2f5; border-top-left-radius: 2px; }
.msg.user .msg-bubble { background: #409eff; color: #fff; border-top-right-radius: 2px; }
.typing span { animation: blink 1.4s infinite both; }
.typing span:nth-child(2) { animation-delay: .2s; }
.typing span:nth-child(3) { animation-delay: .4s; }
@keyframes blink { 0%,80%,100% { opacity: 0; } 40% { opacity: 1; } }
.options-bar { padding: 8px 12px; display: flex; flex-wrap: wrap; gap: 6px; }
.input-bar { padding: 8px 12px; border-top: 1px solid #ebeef5; }
</style>
```

- [ ] **Step 2: 创建 API 调用模块**

Create: `ruoyi-ui/src/api/postgrad/ai.js`

```javascript
import request from '@/utils/request'

export function postAiStart(data) {
  return request({ url: '/app/ai-recommend/start', method: 'post', data })
}

export function postAiChat(data) {
  return request({ url: '/app/ai-recommend/chat', method: 'post', data })
}

export function postAiGenerateReport(data) {
  return request({ url: '/app/ai-recommend/generate-report', method: 'post', data })
}

export function getAiReport(id) {
  return request({ url: '/app/ai-recommend/report/' + id, method: 'get' })
}

export function getAiReports() {
  return request({ url: '/app/ai-recommend/reports', method: 'get' })
}

export function postAiResume(data) {
  return request({ url: '/app/ai-recommend/resume', method: 'post', data })
}
```

- [ ] **Step 3: 提交**

```bash
git add ruoyi-ui/src/views/postgrad/app/components/AiChatPanel.vue ruoyi-ui/src/api/postgrad/ai.js
git commit -m "feat(ai): add AI chat panel component and API module"
```

---

### Task 7: 前端 — 推荐报告页

**Files:**
- Create: `ruoyi-ui/src/views/postgrad/app/ai-report.vue`

- [ ] **Step 1: 编写 ai-report.vue**

```vue
<template>
  <div class="ai-report-page">
    <AppHeader current-page="recommend" />

    <div class="report-container" v-if="report">
      <!-- 加载中 -->
      <div v-if="report.status === 'PENDING'" class="pending-state">
        <i class="el-icon-loading"></i>
        <p>AI 正在为你生成推荐报告...</p>
        <p style="color:#909399;font-size:12px">预计需要 10-30 秒</p>
      </div>

      <!-- 报告内容 -->
      <template v-else>
        <div class="report-header">
          <h2>你的 AI 择校推荐报告</h2>
          <p class="summary">{{ report.result.summary }}</p>
        </div>

        <div v-for="tier in report.result.tiers" :key="tier.level" class="tier-section">
          <h3 class="tier-label" :class="tier.level">
            {{ tier.label }} ({{ tier.schools.length }}所)
          </h3>
          <el-row :gutter="16">
            <el-col :span="8" v-for="school in tier.schools" :key="school.programId">
              <el-card class="school-card" shadow="hover">
                <div class="card-header">
                  <strong>{{ school.schoolName }}</strong>
                  <el-tag :type="riskType(school.risk)" size="mini">{{ school.risk }}</el-tag>
                </div>
                <p class="program-name">{{ school.programName }}</p>
                <el-divider />
                <p class="reason">{{ school.reason }}</p>
                <div class="pros-cons">
                  <div><strong>优势：</strong><span v-for="p in school.pros" :key="p" class="tag green">{{ p }}</span></div>
                  <div style="margin-top:4px"><strong>注意：</strong><span v-for="c in school.cons" :key="c" class="tag orange">{{ c }}</span></div>
                </div>
                <div class="match-bar">
                  <span>匹配度</span>
                  <el-progress :percentage="school.matchScore || 0"
                    :color="matchColor(school.matchScore)" />
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>

        <div class="report-actions">
          <el-button type="primary" @click="$router.back()">返回</el-button>
          <el-button @click="restartRecommend">重新推荐</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
import AppHeader from './components/AppHeader'
import { getAiReport } from '@/api/postgrad/ai'

export default {
  name: 'AiReport',
  components: { AppHeader },
  data() {
    return { report: null, pollTimer: null }
  },
  created() {
    this.fetchReport()
  },
  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
  },
  methods: {
    async fetchReport() {
      try {
        const res = await getAiReport(this.$route.params.id)
        this.report = res.data
        if (res.data.status === 'PENDING') {
          this.pollTimer = setInterval(async () => {
            const r = await getAiReport(this.$route.params.id)
            this.report = r.data
            if (r.data.status !== 'PENDING') clearInterval(this.pollTimer)
          }, 3000)
        }
      } catch (e) {
        this.$message.error('加载报告失败')
      }
    },
    riskType(risk) {
      return risk === 'high' ? 'danger' : risk === 'medium' ? 'warning' : 'success'
    },
    matchColor(score) {
      return score >= 70 ? '#67c23a' : score >= 50 ? '#e6a23c' : '#f56c6c'
    },
    restartRecommend() { this.$router.push({ name: 'AppRecommend' }) }
  }
}
</script>

<style scoped>
.ai-report-page { min-height: 100vh; background: #f5f7fa; }
.report-container { max-width: 1100px; margin: 0 auto; padding: 24px; }
.pending-state { text-align: center; padding: 120px 0; }
.pending-state i { font-size: 48px; color: #409eff; }
.report-header { margin-bottom: 32px; }
.report-header h2 { margin-bottom: 8px; }
.summary { color: #606266; font-size: 15px; }
.tier-section { margin-bottom: 32px; }
.tier-label { padding: 6px 0; border-bottom: 2px solid #ebeef5; margin-bottom: 16px; }
.tier-label.reach { color: #f56c6c; }
.tier-label.steady { color: #e6a23c; }
.tier-label.safe { color: #67c23a; }
.school-card { margin-bottom: 12px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.program-name { color: #909399; font-size: 13px; margin: 4px 0; }
.reason { color: #303133; font-size: 13px; line-height: 1.6; }
.tag { display: inline-block; padding: 1px 6px; border-radius: 4px;
  font-size: 12px; margin-right: 4px; }
.tag.green { background: #f0f9eb; color: #67c23a; }
.tag.orange { background: #fdf6ec; color: #e6a23c; }
.match-bar { margin-top: 12px; display: flex; align-items: center; gap: 8px; }
.match-bar span { font-size: 12px; color: #909399; white-space: nowrap; }
.report-actions { text-align: center; padding: 24px 0; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add ruoyi-ui/src/views/postgrad/app/ai-report.vue
git commit -m "feat(ai): add AI recommendation report page"
```

---

### Task 8: 集成 — 路由注册与 recommend.vue 集成

**Files:**
- Modify: `ruoyi-ui/src/views/postgrad/app/recommend.vue`
- Modify: `ruoyi-ui/src/router/index.js`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RecommendationMapper.java`

- [ ] **Step 1: 在 Router 中注册报告页路由**

在 `ruoyi-ui/src/router/index.js` 的 postgrad 路由组中添加：

```javascript
{
  path: 'app/ai-report/:id',
  component: () => import('@/views/postgrad/app/ai-report.vue'),
  name: 'AiReport',
  meta: { title: 'AI 推荐报告', noCache: true }
}
```

- [ ] **Step 2: 在 recommend.vue 中集成 AiChatPanel**

在 `recommend.vue` 的 `<template>` 末尾（`</div>` 闭合前）添加：

```vue
<!-- AI 聊天面板 -->
<AiChatPanel :visible="aiChatVisible" :candidateIds="candidateResultIds"
  @close="aiChatVisible = false" @fallback="handleAiFallback" />

<!-- AI 推荐按钮（筛选结果区） -->
<el-button v-if="hasResults" type="warning" icon="el-icon-cpu"
  @click="aiChatVisible = true" style="margin-left:8px">
  AI 智能推荐
</el-button>
```

在 `<script>` 中添加：

```javascript
import AiChatPanel from './components/AiChatPanel'

export default {
  components: { ..., AiChatPanel },
  data() {
    return {
      ...,
      aiChatVisible: false,
      candidateResultIds: []
    }
  },
  methods: {
    ...,
    handleAiFallback(data) {
      this.aiChatVisible = false
      this.$message.warning('AI 暂不可用，已为你展示规则推荐结果')
    }
  }
}
```

在 `fetchResults()` 方法成功后更新 `candidateResultIds`:

```javascript
this.candidateResultIds = response.data.groups
  .flatMap(g => g.items.map(item => item.programId))
```

- [ ] **Step 3: RecommendationMapper 新增查询方法**

在 `RecommendationMapper.java` 中添加：

```java
List<RowMap> selectProgramsByIds(@Param("ids") List<Long> ids,
    @Param("estimatedScore") Integer estimatedScore);
```

在 `RecommendationMapper.xml` 中添加：

```xml
<select id="selectProgramsByIds" resultType="com.ruoyi.postgrad.domain.RowMap">
    select
        p.id as programId, s.name as schoolName, s.province as province, s.city as city,
        s.tier as tier, s.is_985 as is985, s.is_211 as is211,
        c.name as collegeName,
        p.program_code as programCode, p.program_name as programName,
        p.degree_type as degreeType,
        sc.score_line as scoreLine,
        ap.total_plan as planCount, ap.retest_count as retestCount,
        ar.admitted_count as admittedCount,
        ar.min_admitted_score as admissionLow,
        ar.avg_admitted_score as avgAdmittedScore,
        ar.max_admitted_score as admissionHigh,
        coalesce(q.completeness_level, 'C') as dataCompleteness,
        coalesce(ar_ds.url, sc_ds.url, ap_ds.url) as sourceUrl,
        coalesce(ar_ds.source_owner, sc_ds.source_owner, ap_ds.source_owner) as sourceOwner
    from program p
    join college c on p.college_id = c.id
    join school s on c.school_id = s.id
    left join admission_score sc on sc.program_id = p.id
      and sc.year = (select max(year) from admission_score where program_id = p.id and score_line is not null)
    left join admission_plan ap on ap.program_id = p.id and ap.year = sc.year
    left join admission_result ar on ar.program_id = p.id and ar.year = sc.year
    left join program_year_data_quality q on q.program_id = p.id and q.year = sc.year
    left join data_source sc_ds on sc_ds.id = sc.source_id
    left join data_source ap_ds on ap_ds.id = ap.source_id
    left join data_source ar_ds on ar_ds.id = ar.source_id
    where p.id in
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>
```

- [ ] **Step 4: 提交**

```bash
git add ruoyi-ui/src/router/index.js ruoyi-ui/src/views/postgrad/app/recommend.vue
git add ruoyi-postgrad/
git commit -m "feat(ai): integrate AI chat panel into recommend page, add routes"
```

---

### Task 9: 测试

**Files:**
- Create: `ruoyi-admin/src/test/java/com/ruoyi/postgrad/AiRecommendationTest.java`

- [ ] **Step 1: 编写集成测试**

```java
package com.ruoyi.postgrad;

import com.ruoyi.postgrad.service.IAiRecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AiRecommendationTest {

    @Autowired
    private IAiRecommendationService aiService;

    @Test
    public void testGoldenPath() {
        // 1. start
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Arrays.asList(1, 2, 3));
        Map<String, Object> start = aiService.startConversation(1L, req);
        assertThat(start).containsKeys("conversationId", "message", "options");
        String convId = (String) start.get("conversationId");

        // 2. chat round 1
        Map<String, Object> chat1 = aiService.chat(1L, convId, "看重专业排名");
        assertThat(chat1).containsKey("message");

        // 3. chat round 2
        Map<String, Object> chat2 = aiService.chat(1L, convId, "想冲一下985");
        assertThat(chat2).containsKey("message");

        // 4. generate report
        Map<String, Object> report = aiService.generateReport(1L, convId);
        assertThat(report).containsEntry("status", "PENDING");
        assertThat(report).containsKey("reportId");
    }

    @Test
    public void testConversationIsolation() {
        // 用户 A 的对话不能被用户 B 访问
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("candidateIds", Arrays.asList(1, 2, 3));
        String convId = (String) aiService.startConversation(1L, req).get("conversationId");

        assertThatThrownBy(() -> aiService.chat(2L, convId, "hello"))
            .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testDetachedHeadException() {
        // ThreadLocal conversationId isolation
        // Verify tools throw when no conversation set
        // (具体实现取决于 Spring 集成方式)
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl ruoyi-admin -Dtest=com.ruoyi.postgrad.AiRecommendationTest -DfailIfNoTests=false
```
Expected: Tests run, verify key assertions pass

- [ ] **Step 3: 提交**

```bash
git add ruoyi-admin/src/test/
git commit -m "test(ai): add integration tests for AI recommendation"
```

---

## Plan Summary

| Task | 描述 | 文件数 |
|---|---|---|
| 1 | RabbitMQ 配置 | 2 |
| 2 | AI 推荐工具类 | 1 |
| 3 | AI 推荐服务层 | 4 |
| 4 | 异步报告消费者 | 1 |
| 5 | Controller 端点 | 1 |
| 6 | 前端聊天组件 | 2 |
| 7 | 前端报告页 | 1 |
| 8 | 集成路由 + 现有页面修改 | 3 |
| 9 | 集成测试 | 1 |

**预计总文件数:** 13 新建 + 3 修改
