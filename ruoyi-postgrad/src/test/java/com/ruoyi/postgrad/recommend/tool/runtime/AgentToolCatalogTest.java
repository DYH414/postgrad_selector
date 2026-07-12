package com.ruoyi.postgrad.recommend.tool.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.ruoyi.postgrad.recommend.tool.V2BoundChatTools;
import com.ruoyi.postgrad.recommend.tool.V2BoundDraftActionTools;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.Test;

class AgentToolCatalogTest {

    @Test
    void catalogCoversEveryBoundToolExactlyOnce() {
        AgentToolCatalog catalog = new AgentToolCatalog();
        Set<String> annotated = Stream.of(V2BoundChatTools.class, V2BoundDraftActionTools.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
            .filter(method -> method.isAnnotationPresent(Tool.class))
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertEquals(16, annotated.size());
        assertEquals(annotated, catalog.definitions().stream()
            .map(AgentToolDefinition::name)
            .collect(Collectors.toSet()));
    }
}
