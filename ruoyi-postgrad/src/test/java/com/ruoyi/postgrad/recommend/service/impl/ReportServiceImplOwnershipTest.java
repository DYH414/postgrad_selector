package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.mapper.RecommendationLogMapper;
import com.ruoyi.postgrad.recommend.domain.ReportVO;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class ReportServiceImplOwnershipTest {

    @Test
    void getReportRejectsLegacyCachedReportWhenCurrentUserDoesNotOwnIt() throws Exception {
        long currentUserId = 7L;
        long anotherUsersReportId = 42L;

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        RecommendationLogMapper logMapper = mock(RecommendationLogMapper.class);

        ReportVO anotherUsersReport = new ReportVO();
        anotherUsersReport.setSummary("another user's report");
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("ai:v2:report:" + anotherUsersReportId))
            .thenReturn(JSON.toJSONString(anotherUsersReport));
        when(logMapper.selectLogByIdAndUserId(anotherUsersReportId, currentUserId)).thenReturn(null);

        ReportServiceImpl service = new ReportServiceImpl();
        setField(service, "redisTemplate", redis);
        setField(service, "logMapper", logMapper);

        assertThrows(IllegalArgumentException.class,
            () -> service.getReport(currentUserId, anotherUsersReportId));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
