package com.ruoyi.postgrad.rag.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RagMarkdownDocumentService {

    private static final Path DEFAULT_OUTPUT_ROOT = Path.of(
        "ruoyi-postgrad", "src", "main", "resources", "rag-generated");

    private final RagKnowledgeExportService exportService;
    private final RagMarkdownDocumentBuilder documentBuilder;
    private final Path outputRoot;

    @Autowired
    public RagMarkdownDocumentService(RagKnowledgeExportService exportService,
                                      RagMarkdownDocumentBuilder documentBuilder) {
        this(exportService, documentBuilder, DEFAULT_OUTPUT_ROOT);
    }

    RagMarkdownDocumentService(RagKnowledgeExportService exportService,
                               RagMarkdownDocumentBuilder documentBuilder,
                               Path outputRoot) {
        this.exportService = exportService;
        this.documentBuilder = documentBuilder;
        this.outputRoot = outputRoot;
    }

    public RagMarkdownExportResult generateProgramYearDocuments(Integer year, int limit) {
        List<RagProgramYearFact> facts = exportService.exportProgramYearFacts(year, limit);
        Path yearDirectory = outputRoot.resolve(String.valueOf(year));
        List<Path> writtenFiles = new ArrayList<>();
        try {
            Files.createDirectories(yearDirectory);
            for (RagProgramYearFact fact : facts) {
                Path documentPath = yearDirectory.resolve(documentBuilder.fileName(fact));
                Files.writeString(documentPath, documentBuilder.buildProgramYearDocument(fact),
                    StandardCharsets.UTF_8);
                writtenFiles.add(documentPath);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write RAG markdown documents: " + ex.getMessage(), ex);
        }
        return new RagMarkdownExportResult(writtenFiles.size(), yearDirectory, writtenFiles);
    }

    public RagMarkdownExportResult generateV2Documents(Integer startYear, Integer endYear, int limit) {
        List<Path> writtenFiles = new ArrayList<>();
        try {
            Path historyDirectory = outputRoot.resolve("program-history");
            Files.createDirectories(historyDirectory);
            for (RagProgramHistoryDocument document : exportService.exportProgramHistoryDocuments(startYear, endYear, limit)) {
                Path documentPath = historyDirectory.resolve(documentBuilder.historyFileName(document));
                Files.writeString(documentPath, documentBuilder.buildProgramHistoryDocument(document),
                    StandardCharsets.UTF_8);
                writtenFiles.add(documentPath);
            }

            Path yearDirectory = outputRoot.resolve("program-year").resolve(String.valueOf(endYear));
            Files.createDirectories(yearDirectory);
            for (RagProgramYearFact fact : exportService.exportProgramYearFacts(endYear, limit)) {
                Path documentPath = yearDirectory.resolve(documentBuilder.fileName(fact));
                Files.writeString(documentPath, documentBuilder.buildProgramYearDocument(fact),
                    StandardCharsets.UTF_8);
                writtenFiles.add(documentPath);
            }

            Path qualityDirectory = outputRoot.resolve("data-quality");
            Files.createDirectories(qualityDirectory);
            for (RagDataQualityDocument document : exportService.exportDataQualityDocuments(startYear, endYear, limit)) {
                Path documentPath = qualityDirectory.resolve(documentBuilder.qualityFileName(document));
                Files.writeString(documentPath, documentBuilder.buildDataQualityDocument(document),
                    StandardCharsets.UTF_8);
                writtenFiles.add(documentPath);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write RAG markdown documents: " + ex.getMessage(), ex);
        }
        return new RagMarkdownExportResult(writtenFiles.size(), outputRoot, writtenFiles);
    }
}
