package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskVO;
import com.ruoyi.postgrad.recommend.service.IDraftService;

class DraftGenerationTaskServiceImplTest {

    @Test
    void startShouldReturnTaskIdAndPersistRunningState() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        IDraftService draftService = mock(IDraftService.class);
        DraftGenerationTaskServiceImpl service = new DraftGenerationTaskServiceImpl();
        service.setRedisTemplateForTest(redis);
        service.setDraftServiceForTest(draftService);

        DraftGenerationTaskVO vo = service.start(1L);

        assertNotNull(vo.getTaskId());
        assertNotNull(vo.getStreamToken());
        assertEquals("running", vo.getStatus());
        verify(ops, atLeastOnce()).set(
            startsWith("ai:v2:draft:task:"),
            contains("\"status\":\"running\""),
            any(Duration.class));
        verify(draftService).generateDraft(any(), any());
    }

    @Test
    void validateStreamTokenShouldRejectWrongToken() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        IDraftService draftService = mock(IDraftService.class);
        DraftGenerationTaskServiceImpl service = new DraftGenerationTaskServiceImpl();
        service.setRedisTemplateForTest(redis);
        service.setDraftServiceForTest(draftService);
        DraftGenerationTaskVO vo = service.start(1L);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(ops, atLeastOnce()).set(
            startsWith("ai:v2:draft:task:"),
            jsonCaptor.capture(),
            any(Duration.class));
        String persisted = jsonCaptor.getAllValues().get(0);
        when(ops.get("ai:v2:draft:task:" + vo.getTaskId())).thenReturn(persisted);

        assertEquals(false, service.validateStreamToken(vo.getTaskId(), "wrong-token"));
    }
}
