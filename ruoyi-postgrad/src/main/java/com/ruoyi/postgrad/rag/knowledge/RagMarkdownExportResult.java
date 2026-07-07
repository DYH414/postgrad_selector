package com.ruoyi.postgrad.rag.knowledge;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RagMarkdownExportResult {

    private final int documentCount;
    private final Path outputDirectory;
    private final List<Path> writtenFiles;

    public RagMarkdownExportResult(int documentCount, Path outputDirectory, List<Path> writtenFiles) {
        this.documentCount = documentCount;
        this.outputDirectory = outputDirectory;
        this.writtenFiles = new ArrayList<>(writtenFiles);
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public List<Path> getWrittenFiles() {
        return new ArrayList<>(writtenFiles);
    }
}
