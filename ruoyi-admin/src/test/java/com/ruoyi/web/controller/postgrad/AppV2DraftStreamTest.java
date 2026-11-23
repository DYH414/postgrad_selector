package com.ruoyi.web.controller.postgrad;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.postgrad.mapper.RecommendationMapper;
import com.ruoyi.postgrad.recommend.domain.DraftGenerationTaskState;
import com.ruoyi.postgrad.recommend.service.IDraftGenerationTaskService;
import com.ruoyi.postgrad.recommend.service.impl.DraftSummaryMessageServiceImpl;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppV2DraftStreamTest {
    private static final String CARDS = "[{\"fact\":{\"programId\":13}},{\"fact\":{\"programId\":169}},{\"fact\":{\"programId\":207}},{\"fact\":{\"programId\":3134}},{\"fact\":{\"programId\":99999999}},{\"fact\":{\"programId\":2091}}]";
    private static final String TIER = "{\"level\":\"reach\",\"label\":\"Reach\",\"candidates\":" + CARDS + "}";
    private static final String DRAFT = "{\"tiers\":[" + TIER + "],\"removedCandidates\":" + CARDS
        + ",\"blockedCandidates\":[{\"programId\":13},{\"programId\":169}],\"pendingRefill\":{\"programId\":2091},\"fallbackTiers\":[\"Reach\"]}";

    @Test
    void filtersCachedEventsAndDone() throws Exception {
        DraftGenerationTaskState done = done();
        done.setStreamEventsJson("[{\"phase\":\"selecting\",\"tierData\":" + TIER + "}]");
        assertFiltered(run(done, done));
        assertEquals(DRAFT, done.getDraftJson());
    }

    @Test
    void filtersLegacyTierProgressAndDone() throws Exception {
        DraftGenerationTaskState running = new DraftGenerationTaskState();
        running.setStatus(DraftGenerationTaskState.STATUS_RUNNING);
        running.setProgressJson("{\"phase\":\"selecting\"}");
        running.setTierJson(TIER);
        DraftGenerationTaskState done = done();
        assertFiltered(run(running, done));
        assertEquals(TIER, running.getTierJson());
    }

    private static DraftGenerationTaskState done() {
        DraftGenerationTaskState state = new DraftGenerationTaskState();
        state.setStatus(DraftGenerationTaskState.STATUS_DONE);
        state.setDraftJson(DRAFT);
        state.setSummaryMessage("这份方案共 6 所");
        return state;
    }

    private static String run(DraftGenerationTaskState first, DraftGenerationTaskState last) throws Exception {
        IDraftGenerationTaskService service = mock(IDraftGenerationTaskService.class);
        RecommendationMapper mapper = mock(RecommendationMapper.class);
        when(service.validateStreamToken("task", "token")).thenReturn(true);
        when(service.getState("task")).thenReturn(first, last);
        when(mapper.selectApplicantProgramIds(anyList())).thenAnswer(inv -> {
            List<Long> ids = inv.getArgument(0);
            return ids.contains(13L) ? List.of(13L) : List.of();
        });
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.initialize();
        try {
            AppV2RecommendController controller = new AppV2RecommendController();
            ReflectionTestUtils.setField(controller, "draftGenerationTaskService", service);
            ReflectionTestUtils.setField(controller, "threadPoolTaskExecutor", executor);
            ReflectionTestUtils.setField(controller, "recommendationMapper", mapper);
            ReflectionTestUtils.setField(controller, "draftSummaryMessageService", new DraftSummaryMessageServiceImpl());
            MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8))
                .build();
            MvcResult result = mvc.perform(get("/app/ai-recommend-v2/draft/generate/stream")
                .param("taskId", "task").param("streamToken", "token"))
                .andExpect(request().asyncStarted()).andReturn();
            result.getAsyncResult(5000);
            byte[] body = mvc.perform(asyncDispatch(result)).andReturn().getResponse().getContentAsByteArray();
            return new String(body, StandardCharsets.UTF_8);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void filtersStringTierAndEmbeddedLegacyTier() throws Exception {
        DraftGenerationTaskState done = done();
        done.setStreamEventsJson("[{\"phase\":\"selecting\",\"tierData\":" + JSON.toJSONString(TIER) + "}]");
        assertFiltered(run(done, done));

        DraftGenerationTaskState running = new DraftGenerationTaskState();
        running.setStatus(DraftGenerationTaskState.STATUS_RUNNING);
        running.setProgressJson("{\"phase\":\"selecting\",\"tierData\":" + TIER + "}");
        assertFiltered(run(running, done()));
    }

    private static void assertFiltered(String events) {
        List<String> data = Arrays.stream(events.split("\n"))
            .filter(line -> line.startsWith("data:")).map(line -> line.substring(5)).toList();
        assertEquals(2, data.size(), events);
        JSONObject progress = JSON.parseObject(data.get(0));
        JSONObject tier = assertInstanceOf(JSONObject.class, progress.get("tierData"));
        assertCards(tier.getJSONArray("candidates"));
        JSONObject envelope = JSON.parseObject(data.get(1));
        JSONObject draft = envelope.getJSONObject("draft");
        assertCards(draft.getJSONArray("tiers").getJSONObject(0).getJSONArray("candidates"));
        assertCards(draft.getJSONArray("removedCandidates"));
        assertEquals(13, draft.getJSONArray("blockedCandidates").getJSONObject(0).getLongValue("programId"));
        assertEquals(1, draft.getJSONArray("blockedCandidates").size());
        assertNull(draft.get("pendingRefill"));
        assertEquals("Reach", envelope.getJSONArray("fallbackTiers").getString(0));
        String summary = envelope.getString("summaryMessage");
        assertTrue(summary.contains("这份方案共 1 所"), summary);
    }

    private static void assertCards(JSONArray cards) {
        assertEquals(1, cards.size());
        assertEquals(13, cards.getJSONObject(0).getJSONObject("fact").getLongValue("programId"));
    }
}
