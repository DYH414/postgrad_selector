package com.ruoyi.web.controller.postgrad;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ruoyi.common.core.domain.model.AppLoginUser;
import com.ruoyi.postgrad.domain.UserProfile;
import com.ruoyi.postgrad.service.IUserProfileService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AppProfileControllerTest
{
    private IUserProfileService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() throws Exception
    {
        service = mock(IUserProfileService.class);
        AppProfileController controller = new AppProfileController();
        Field field = AppProfileController.class.getDeclaredField("profileService");
        field.setAccessible(true);
        field.set(controller, service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        AppLoginUser principal = new AppLoginUser(7L);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clear()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsInvalidRegionShapes() throws Exception
    {
        for (String value : new String[] {"\"Beijing\"", "{}", "[1]", "[null]", "\"[1]\"", "\"[\\\"浙江\\\"] true\"", "\"{\\\"a\\\":1}\""})
        {
            mvc.perform(post("/app/profile").contentType("application/json").content("{\"targetRegions\":" + value + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("目标地区必须是字符串数组"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void acceptsRegionsAndSerializesArrays() throws Exception
    {
        for (String value : new String[] {"null", "\"\"", "[]", "[\"北京\",\"上海\"]", "\"[\\\"北京\\\"]\""})
        {
            mvc.perform(post("/app/profile").contentType("application/json").content("{\"targetRegions\":" + value + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        }
        mvc.perform(post("/app/profile").contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).saveOrUpdateUserProfile(eq(7L), argThat((UserProfile p) -> "[\"北京\",\"上海\"]".equals(p.getTargetRegions())));
    }

    @Test
    void rejectsInvalidEnumAndAcceptsAliases() throws Exception
    {
        for (String field : new String[] {"riskPreference", "schoolTierPreference", "regionStrategy", "undergradTier", "mathLevel", "englishLevel", "csLevel"})
        {
            mvc.perform(post("/app/profile").contentType("application/json").content("{\"" + field + "\":\"invalid\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(service);
        mvc.perform(post("/app/profile").contentType("application/json").content("{\"riskPreference\":\"conservative\",\"schoolTierPreference\":\"must_211_or_better\",\"regionStrategy\":\"developed_balanced\"}"))
            .andExpect(status().isOk());
        verify(service).saveOrUpdateUserProfile(eq(7L), argThat((UserProfile p) -> "safe_first".equals(p.getRiskPreference()) && "tier_priority".equals(p.getSchoolTierPreference()) && "developed_priority".equals(p.getRegionStrategy())));
    }
}
