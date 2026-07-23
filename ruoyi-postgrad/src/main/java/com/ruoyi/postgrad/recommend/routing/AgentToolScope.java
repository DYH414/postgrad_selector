package com.ruoyi.postgrad.recommend.routing;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds the concrete LangChain4j tool list for one routed invocation. */
public final class AgentToolScope {

    private static final Set<String> KNOWLEDGE = Set.of("searchKnowledgeBase");
    private static final Set<String> PROGRAM = Set.of(
        "searchPrograms", "getProgramDetail", "comparePrograms");
    private static final Set<String> HYBRID = union(PROGRAM,
        Set.of("searchKnowledgeBase", "getProgramEvidence"));
    private static final Set<String> DRAFT_READ = Set.of(
        "getDraftContext", "analyzeDraftHealth", "getUserProfile",
        "getProgramDetail", "comparePrograms");
    private static final Set<String> DRAFT_WRITE = union(DRAFT_READ, Set.of(
        "searchPrograms", "getRefillCandidates",
        "removeDraftCandidate", "addDraftCandidate", "confirmRefillCandidate",
        "replaceDraftCandidate", "fillTier", "batchRemoveDraftCandidates",
        "batchReplaceDraftCandidates"));
    private static final Set<String> SAFE_FALLBACK = union(HYBRID,
        union(DRAFT_READ, Set.of("getUserProfile", "getRefillCandidates")));

    private AgentToolScope() {
    }

    public static List<AiServiceTool> select(AgentIntentRoute route, Object... toolObjects) {
        AgentIntentRoute effective = route == null ? AgentIntentRoute.FALLBACK : route;
        Set<String> allowed = switch (effective) {
            case KNOWLEDGE_RAG -> KNOWLEDGE;
            case PROGRAM_FACTS -> PROGRAM;
            case HYBRID -> HYBRID;
            case DRAFT_READ -> DRAFT_READ;
            case DRAFT_WRITE -> DRAFT_WRITE;
            case CLARIFY -> Set.of();
            case FALLBACK -> SAFE_FALLBACK;
        };

        List<AiServiceTool> tools = new ArrayList<>();
        for (Object object : toolObjects) {
            if (object == null) continue;
            Arrays.stream(object.getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .filter(method -> allowed.contains(method.getName()))
                .map(method -> toAiServiceTool(object, method))
                .forEach(tools::add);
        }
        return tools;
    }

    private static AiServiceTool toAiServiceTool(Object object, Method method) {
        ToolSpecification specification = ToolSpecifications.toolSpecificationFrom(method);
        Tool annotation = method.getAnnotation(Tool.class);
        return AiServiceTool.builder()
            .toolSpecification(specification)
            .toolExecutor(new DefaultToolExecutor(object, method))
            .returnBehavior(annotation.returnBehavior())
            .build();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }
}
