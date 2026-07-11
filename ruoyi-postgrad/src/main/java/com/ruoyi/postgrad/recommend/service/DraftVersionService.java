package com.ruoyi.postgrad.recommend.service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.DraftVO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class DraftVersionService {
    public String hash(DraftVO draft) {
        try {
            byte[] value = MessageDigest.getInstance("SHA-256")
                .digest(JSON.toJSONString(draft).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(value).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to version draft", e);
        }
    }
}
