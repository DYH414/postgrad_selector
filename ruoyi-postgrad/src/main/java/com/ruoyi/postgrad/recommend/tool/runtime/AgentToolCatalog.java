package com.ruoyi.postgrad.recommend.tool.runtime;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ruoyi.postgrad.domain.ai.AiToolBudget.BudgetGroup;
import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2BoundDraftActionTools;
import dev.langchain4j.agent.tool.Tool;

import static com.ruoyi.postgrad.recommend.tool.runtime.AgentToolDefinition.ToolCategory.READ;
import static com.ruoyi.postgrad.recommend.tool.runtime.AgentToolDefinition.ToolCategory.WRITE;

/** Catalog validates that every LangChain4j-bound tool has explicit governance metadata. */
public class AgentToolCatalog {

    private final Map<String, AgentToolDefinition> definitions;

    public AgentToolCatalog() {
        Map<String, AgentToolDefinition> registered = new LinkedHashMap<>();
        register(registered, "getProgramDetail", READ, BudgetGroup.DETAIL, 500, false, true);
        register(registered, "getProgramEvidence", READ, BudgetGroup.RAG, 800, false, true);
        register(registered, "searchPrograms", READ, BudgetGroup.SEARCH, 700, false, true);
        register(registered, "comparePrograms", READ, BudgetGroup.DETAIL, 700, false, true);
        register(registered, "searchKnowledgeBase", READ, BudgetGroup.RAG, 800, false, true);
        register(registered, "getUserProfile", READ, BudgetGroup.CONTEXT, 300, false, true);
        register(registered, "analyzeDraftHealth", READ, BudgetGroup.CONTEXT, 500, false, true);
        register(registered, "getDraftContext", READ, BudgetGroup.CONTEXT, 500, false, true);
        register(registered, "getRefillCandidates", READ, BudgetGroup.SEARCH, 500, false, true);
        register(registered, "removeDraftCandidate", WRITE, BudgetGroup.WRITE, 300, true, false);
        register(registered, "addDraftCandidate", WRITE, BudgetGroup.WRITE, 300, true, false);
        register(registered, "confirmRefillCandidate", WRITE, BudgetGroup.WRITE, 300, true, false);
        register(registered, "replaceDraftCandidate", WRITE, BudgetGroup.WRITE, 400, true, false);
        register(registered, "fillTier", WRITE, BudgetGroup.WRITE, 400, true, false);
        register(registered, "batchRemoveDraftCandidates", WRITE, BudgetGroup.WRITE, 500, true, false);
        register(registered, "batchReplaceDraftCandidates", WRITE, BudgetGroup.WRITE, 600, true, false);
        validateBoundTools(registered.keySet());
        definitions = Collections.unmodifiableMap(registered);
    }

    public AgentToolDefinition require(String name) {
        AgentToolDefinition definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Agent tool: " + name);
        }
        return definition;
    }

    public Collection<AgentToolDefinition> definitions() {
        return definitions.values();
    }

    private static void register(Map<String, AgentToolDefinition> definitions, String name,
        AgentToolDefinition.ToolCategory category, BudgetGroup budgetGroup,
        int estimatedResultTokens, boolean confirmationRequired, boolean concurrencySafe) {
        AgentToolDefinition previous = definitions.put(name, new AgentToolDefinition(name, category,
            budgetGroup, estimatedResultTokens, confirmationRequired, concurrencySafe));
        if (previous != null) {
            throw new IllegalStateException("Duplicate Agent tool definition: " + name);
        }
    }

    private static void validateBoundTools(Set<String> registeredNames) {
        Set<String> boundNames = Stream.of(V2BoundChatTools.class, V2BoundDraftActionTools.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .map(Method::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!boundNames.equals(registeredNames)) {
            throw new IllegalStateException("Bound Agent tools and catalog definitions must match: bound="
                + boundNames + ", registered=" + registeredNames);
        }
    }
}
