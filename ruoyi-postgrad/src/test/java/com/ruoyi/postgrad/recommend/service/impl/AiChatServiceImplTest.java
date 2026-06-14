package com.ruoyi.postgrad.recommend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftAction;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;

class AiChatServiceImplTest {

    @Test
    void parseDraftActionUsesFirstObjectWhenModelReturnsMultipleActions() {
        DraftAction action = AiChatServiceImpl.parseDraftAction("""
            {"type":"remove","programId":2357,"tier":"steady"}
            {"type":"remove","programId":3082,"tier":"steady"}
            {"type":"replace","programId":42,"tier":"safe","preference":"safer"}
            """);

        assertNotNull(action);
        assertEquals("remove", action.getType());
        assertEquals(2357L, action.getProgramId());
        assertEquals("steady", action.getTier());
    }

    @Test
    void parseDraftActionAcceptsJsonFencedBlock() {
        DraftAction action = AiChatServiceImpl.parseDraftAction("""
            ```json
            {"type":"replace","programId":42,"tier":"safe","preference":"safer"}
            ```
            """);

        assertNotNull(action);
        assertEquals("replace", action.getType());
        assertEquals(42L, action.getProgramId());
        assertEquals("safer", action.getPreference());
    }

    @Test
    void parseDraftActionReturnsNullWhenNoJsonExists() {
        assertNull(AiChatServiceImpl.parseDraftAction("继续分析，不执行操作"));
    }

    @Test
    void parseDraftActionRejectsInventedStringProgramId() {
        assertNull(AiChatServiceImpl.parseDraftAction("""
            {"type":"remove","programId":"xiangtan_university_cs"}
            """));
    }

    @Test
    void inferDraftActionRemovesMentionedCurrentDraftSchoolWhenActionJsonMissing() {
        SchoolFact safeFact = new SchoolFact();
        safeFact.setProgramId(3188L);
        safeFact.setSchoolName("宁夏大学");
        safeFact.setProgramName("电子信息");

        TierCandidates safeTier = new TierCandidates();
        safeTier.setLabel("保底档");
        safeTier.setTargetCount(3);
        safeTier.setCandidates(List.of(CandidateCardVO.fromFact(safeFact)));

        DraftVO draft = new DraftVO();
        draft.setTiers(List.of(safeTier));

        DraftAction action = AiChatServiceImpl.inferDraftActionFromDisplayText("""
            ## 当前状态

            湘潭大学 **已不在草稿中**，不用管了。

            但 **宁夏大学还在保底档**：

            现在移除它：
            """, draft);

        assertNotNull(action);
        assertEquals("remove", action.getType());
        assertEquals(3188L, action.getProgramId());
    }

    @Test
    void draftContextIncludesHiddenOperationIdForActions() {
        SchoolFact fact = new SchoolFact();
        fact.setProgramId(2357L);
        fact.setSchoolName("湘潭大学");
        fact.setProgramName("计算机科学与技术");
        fact.setAvgAdmittedScore(290);
        fact.setScoreGap(10);

        CandidateCardVO card = CandidateCardVO.fromFact(fact);

        TierCandidates tier = new TierCandidates();
        tier.setLabel("稳妥档");
        tier.setTargetCount(4);
        tier.setCandidates(List.of(card));

        DraftVO draft = new DraftVO();
        draft.setTiers(List.of(tier));

        String context = AiChatServiceImpl.buildDraftContextText(draft);

        assertTrue(context.contains("湘潭大学 计算机科学与技术 【操作ID:2357】"));
        assertTrue(context.contains("稳妥档（1/4）"));
    }
}
