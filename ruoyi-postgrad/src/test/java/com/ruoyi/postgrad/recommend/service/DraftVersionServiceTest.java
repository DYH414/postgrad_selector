package com.ruoyi.postgrad.recommend.service;

import static org.junit.jupiter.api.Assertions.*;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import org.junit.jupiter.api.Test;

class DraftVersionServiceTest {
    @Test
    void changesHashWhenDraftStateChanges() {
        DraftVersionService service = new DraftVersionService();
        DraftVO first = new DraftVO(); first.setFallbackTiers(java.util.List.of("safe"));
        DraftVO second = new DraftVO(); second.setFallbackTiers(java.util.List.of("reach"));
        assertEquals(service.hash(first), service.hash(first));
        assertNotEquals(service.hash(first), service.hash(second));
    }
}
