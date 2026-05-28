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
