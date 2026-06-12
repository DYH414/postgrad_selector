package com.ruoyi.postgrad.recommend.service.impl;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.ruoyi.postgrad.recommend.domain.AiSelectionResult;
import com.ruoyi.postgrad.recommend.domain.CandidateCardVO;
import com.ruoyi.postgrad.recommend.service.IAiSelectorService;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;

/**
 * AI 选校服务实现 —— 在给定候选事实卡内由 AI 挑选。
 * <p>每档一次单轮 completion，非对话。AI 只选不给规则判断。</p>
 *
 * <p>TODO: 实现 select</p>
 */
@Service
public class AiSelectorServiceImpl implements IAiSelectorService {

    private static final Logger log = LoggerFactory.getLogger(AiSelectorServiceImpl.class);

    /** 每档最多选几所 */
    private static final int REACH_LIMIT = 3;
    private static final int STEADY_LIMIT = 4;
    private static final int SAFE_LIMIT = 3;

    @Value("classpath:prompts/v2/select-reach.txt")
    private org.springframework.core.io.Resource reachPromptResource;

    @Value("classpath:prompts/v2/select-steady.txt")
    private org.springframework.core.io.Resource steadyPromptResource;

    @Value("classpath:prompts/v2/select-safe.txt")
    private org.springframework.core.io.Resource safePromptResource;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private SelectionValidator validator;

    @Override
    public AiSelectionResult select(String tier, List<CandidateCardVO> candidates, int estimatedScore) {
        // TODO: 实现 AI 选校
        // 1. candidates 为空 → 返回空结果
        // 2. 如果 candidates.size() <= 该档上限 → 跳过 AI，全部选中
        // 3. 构建事实卡文本列表
        // 4. 加载对应档位的提示词
        // 5. 调用 chatModel.chat(SystemMessage + UserMessage(事实卡))
        // 6. 解析 AI 返回的 JSON（多层防御：提取代码块 → 提取花括号 → 兜底）
        // 7. 调用 SelectionValidator 校验
        // 8. 返回 AiSelectionResult
        throw new UnsupportedOperationException("TODO: implement select");
    }

    // ── private helpers (to be added) ──

    /** 将候选转为单行事实卡文本（供 AI 选择） */
    private String buildFactCardLine(CandidateCardVO c) {
        // TODO: 实现事实卡文本构建
        // 格式: "ID:123 | XX大学 | 计算机技术 | 985 | 北京 | 均分345 | 差距+10 | 招生15人 | 名额正常 | 可保底"
        return "";
    }

    /** 加载指定档位的提示词 */
    private String loadTierPrompt(String tier) {
        // TODO: 根据档位加载对应提示词资源
        return "";
    }

    /** 解析 AI 返回的 JSON（多层防御） */
    private List<AiSelectionResult.SelectedItem> parseAiResponse(String rawResponse) {
        // TODO: 实现 JSON 解析
        return Collections.emptyList();
    }
}
