package com.ruoyi.postgrad.recommend.tool;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import com.ruoyi.postgrad.recommend.domain.SchoolFact;
import com.ruoyi.postgrad.recommend.domain.TierCandidates;
import com.ruoyi.postgrad.recommend.service.IDraftService;

import dev.langchain4j.agent.tool.Tool;

@Component
public class V2DraftActionTools {

    @Autowired
    private IDraftService draftService;

    @Tool("Remove one school from the current report draft. Only one write action is allowed per turn, and programId must come from the current draft.")
    public String removeDraftCandidate(long programId) {
        V2ChatToolContext.Context ctx = V2ChatToolContext.current();
        if (ctx == null) {
            return error("no_tool_context", "Tool context is not initialized; draft cannot be modified.");
        }
        if (V2ChatToolContext.writeExecuted()) {
            return error("write_already_executed", "A draft write action has already been executed in this turn.");
        }

        DraftVO before = draftService.getDraft(ctx.userId());
        CandidateCardVO target = findCandidate(before, programId);
        if (target == null) {
            return error("program_not_in_draft", "The requested school is not in the current draft.");
        }

        DraftVO after = draftService.removeCandidate(ctx.userId(), programId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("action", "remove");
        result.put("programId", programId);
        result.put("schoolName", schoolName(target));
        result.put("draftCount", countCandidates(after));
        result.put("message", "Removed " + schoolName(target));

        String json = JSON.toJSONString(result);
        V2ChatToolContext.markWriteExecuted(json);
        return json;
    }

    void setDraftServiceForTest(IDraftService draftService) {
        this.draftService = draftService;
    }

    private String error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("error", code);
        result.put("message", message);
        return JSON.toJSONString(result);
    }

    private CandidateCardVO findCandidate(DraftVO draft, long programId) {
        if (draft == null || draft.getTiers() == null) {
            return null;
        }
        for (TierCandidates tier : draft.getTiers()) {
            if (tier.getCandidates() == null) {
                continue;
            }
            for (CandidateCardVO candidate : tier.getCandidates()) {
                SchoolFact fact = candidate.getFact();
                if (fact != null && fact.getProgramId() != null && fact.getProgramId() == programId) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private int countCandidates(DraftVO draft) {
        if (draft == null || draft.getTiers() == null) {
            return 0;
        }
        int total = 0;
        for (TierCandidates tier : draft.getTiers()) {
            if (tier.getCandidates() != null) {
                total += tier.getCandidates().size();
            }
        }
        return total;
    }

    private String schoolName(CandidateCardVO candidate) {
        if (candidate == null || candidate.getFact() == null) {
            return "";
        }
        return candidate.getFact().getSchoolName();
    }
}
