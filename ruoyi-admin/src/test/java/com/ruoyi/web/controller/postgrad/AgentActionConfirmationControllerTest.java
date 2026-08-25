package com.ruoyi.web.controller.postgrad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.postgrad.recommend.service.AgentActionConfirmationService;
import com.ruoyi.postgrad.recommend.service.AgentActionProposalService;
import com.ruoyi.postgrad.recommend.domain.AgentActionProposal;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AgentActionConfirmationControllerTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void confirmsActionForAuthenticatedOwner() throws Exception {
        AgentActionConfirmationService service = mock(AgentActionConfirmationService.class);
        when(service.confirm(7L, "token")).thenReturn(Map.of("ok", true, "action", "remove"));
        AppV2RecommendController controller = new AppV2RecommendController();
        Field field = AppV2RecommendController.class.getDeclaredField("agentActionConfirmationService");
        field.setAccessible(true); field.set(controller, service);
        AppLoginUser principal = new AppLoginUser(7L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        AjaxResult result = controller.confirmAgentAction(Map.of("confirmationToken", "token"));

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        verify(service).confirm(7L, "token");
    }

    @Test
    void returnsPendingActionForAuthenticatedOwner() throws Exception {
        AgentActionProposalService service = mock(AgentActionProposalService.class);
        AgentActionProposal proposal = new AgentActionProposal();
        proposal.setUserId(7L);
        proposal.setToken("token");
        when(service.pending(7L)).thenReturn(proposal);
        AppV2RecommendController controller = new AppV2RecommendController();
        Field field = AppV2RecommendController.class.getDeclaredField("agentActionProposalService");
        field.setAccessible(true); field.set(controller, service);
        AppLoginUser principal = new AppLoginUser(7L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        AjaxResult result = controller.pendingAgentAction();

        assertEquals(200, result.get(AjaxResult.CODE_TAG));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
        assertEquals("token", data.get("confirmationToken"));
        assertEquals(true, data.get("confirmationRequired"));
        verify(service).pending(7L);
    }
}
