package com.ruoyi.postgrad.rag;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagRuntimeRetirementTest {

    @Test
    void productionSourcesMustNotRetainLegacyRagRuntimeTypes() throws IOException {
        List<Path> sources;
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).toList();
        }

        for (Path source : sources) {
            String text = Files.readString(source);
            assertFalse(text.contains("RagSearchTestService"), source + " retains duplicate retrieval facade");
            assertFalse(text.contains("RagDocumentService"), source + " retains retired writer");
            assertFalse(text.contains("QdrantCollectionService"), source + " retains retired collection manager");
        }
    }
}
