package com.ruoyi.postgrad.recommend.tool;

import dev.langchain4j.agent.tool.Tool;

/**
 * Per-chat wrapper that makes V2ChatToolContext available even when
 * LangChain4j executes tools on a worker thread.
 */
public class V2BoundChatTools {

    private final V2ChatTools delegate;
    private final V2ChatToolContext.Context context;

    public V2BoundChatTools(V2ChatTools delegate, V2ChatToolContext.Context context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Tool("查询指定 programId 对应学校的完整详细数据，包括学校层次、录取均分、招生人数、复试线等")
    public String getProgramDetail(long programId) {
        return V2ChatToolContext.callWith(context, () -> delegate.getProgramDetail(programId));
    }

    @Tool("检索指定 programId 的 RAG 证据。回答某个学校为什么推荐、风险是什么、历年趋势如何时使用。参数 question 是用户具体问题。")
    public String getProgramEvidence(long programId, String question) {
        return V2ChatToolContext.callWith(context, () -> delegate.getProgramEvidence(programId, question));
    }

    @Tool("在候选池中搜索学校，支持按关键词、档位(tier)、地区(region)过滤。tier 可选值: reach/steady/safe，region 为省份或城市名。")
    public String searchPrograms(String keyword, String tier, String region) {
        return V2ChatToolContext.callWith(context, () -> delegate.searchPrograms(keyword, tier, region));
    }

    @Tool("并排对比两所候选学校的核心指标，包括学校层次、录取均分、分数差距、招生名额、城市")
    public String comparePrograms(long programId1, long programId2) {
        return V2ChatToolContext.callWith(context, () -> delegate.comparePrograms(programId1, programId2));
    }

    @Tool("不限定学校的 RAG 知识库检索，用于回答考研通识问题。参数 question 是用户的具体问题。")
    public String searchKnowledgeBase(String question) {
        return V2ChatToolContext.callWith(context, () -> delegate.searchKnowledgeBase(question));
    }

    @Tool("获取当前用户的择校画像信息，包括预估分数、目标地区、学校层次偏好。")
    public String getUserProfile() {
        return V2ChatToolContext.callWith(context, delegate::getUserProfile);
    }

    @Tool("分析当前草稿的健康度：档位均衡性、风险分布、地区集中度、名额告警。在用户询问方案合理性时调用。")
    public String analyzeDraftHealth() {
        return V2ChatToolContext.callWith(context, delegate::analyzeDraftHealth);
    }

    @Tool("查看当前报告草稿的状态——已选了几所、分别在哪些档位、每所学校的关键信息。")
    public String getDraftContext() {
        return V2ChatToolContext.callWith(context, delegate::getDraftContext);
    }

    @Tool("获取指定档位的候选填充列表。在移除学校后如需手动选择替代候选时使用。tier 可选值: reach/steady/safe, count 默认为 3。")
    public String getRefillCandidates(String tier, int count) {
        return V2ChatToolContext.callWith(context, () -> delegate.getRefillCandidates(tier, count));
    }
}
