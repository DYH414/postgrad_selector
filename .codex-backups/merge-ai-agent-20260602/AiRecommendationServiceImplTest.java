package com.ruoyi.postgrad.service.impl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiRecommendationServiceImplTest {

    @Test
    void shouldUseNeutralInitialOptionsWithoutSpecificCity() throws Exception {
        Method method = AiRecommendationServiceImpl.class.getDeclaredMethod("initialPreferenceOptions");
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) method.invoke(null);

        assertEquals(List.of("看重上岸率", "学校层次优先", "专业实力最重要", "城市/地区优先"), options);
        assertFalse(options.stream().anyMatch(option -> option.contains("限定上海")));
    }

    @Test
    void shouldNotTeachModelToCopySpecificCityAsQuickOption() throws Exception {
        Field field = AiRecommendationServiceImpl.class.getDeclaredField("SYSTEM_PROMPT");
        field.setAccessible(true);

        String prompt = (String) field.get(null);

        assertFalse(prompt.contains("限定上海"));
        assertTrue(prompt.contains("城市/地区优先"));
    }
}
