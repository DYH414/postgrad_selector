# RAG Document V2 Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first production-shaped database RAG document pipeline with `program-history`, `program-year`, and `data-quality` generated under `ruoyi-postgrad/src/main/resources/rag-generated/`.

**Architecture:** Keep MySQL as the structured source of truth, convert rows into focused RAG document models, render Markdown evidence documents, then generate section-level chunks with `rag-doc-v2` metadata. Reuse the existing Qdrant indexing flow, but change the generated database knowledge from old single-year summaries to the new document families.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML mapper, LangChain4j `TextSegment`, Qdrant embedding store, JUnit 5, Maven.

## Global Constraints

- All generated documents live under `ruoyi-postgrad/src/main/resources/rag-generated/`.
- First implementation includes only `program-history`, `program-year`, and `data-quality`.
- New generated documents use `schema_version: rag-doc-v2`.
- New generated documents use `doc_type` values `program_history`, `program_year`, and `data_quality`.
- Do not use RAG in candidate SQL selection, tier classification, `SelectionValidator`, or exact DB snapshot hydration.
- Keep exact facts from MySQL; RAG is for evidence, explanation, retrieval, and citations.
- Use semantic section chunks, not fixed character chunks, for generated database knowledge.
- Do not commit generated `rag-generated/*.md` bulk output until the user explicitly asks to version generated documents.

---

## File Structure

### Existing files to modify

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RagKnowledgeExportMapper.java`
  - Add `selectProgramHistoryFacts(Integer startYear, Integer endYear, int limit)`.
- `ruoyi-postgrad/src/main/resources/mapper/postgrad/RagKnowledgeExportMapper.xml`
  - Add the matching MyBatis query that returns one row per program per year.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagKnowledgeExportService.java`
  - Group yearly facts by `programId` and expose history, yearly, and quality document data.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagMarkdownDocumentBuilder.java`
  - Render v2 Markdown for the three document families.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagMarkdownDocumentService.java`
  - Write generated Markdown to the new directory layout.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/StructuredRagChunkBuilder.java`
  - Emit v2 section chunks and metadata for all three document families.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagDocumentService.java`
  - Index v2 generated database chunks.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/controller/RagAdminController.java`
  - Replace old single-year generate endpoint with v2 generation parameters.

### New files to create

- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagProgramHistoryDocument.java`
  - Holds a program and all yearly facts for Markdown/chunk generation.
- `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagDataQualityDocument.java`
  - Holds derived missing-year and missing-field information for one program.
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/RagProgramHistoryDocumentTest.java`
  - Verifies grouping and metadata inputs.

### Existing tests to update

- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/RagKnowledgeExportServiceTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/RagMarkdownDocumentServiceTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/StructuredRagChunkBuilderTest.java`
- `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/RagDocumentServiceTest.java`

---

### Task 1: Add Program History Document Model

**Files:**
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagProgramHistoryDocument.java`
- Create: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagDataQualityDocument.java`
- Create: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/RagProgramHistoryDocumentTest.java`

**Interfaces:**
- Consumes: `RagProgramYearFact`
- Produces:
  - `RagProgramHistoryDocument(List<RagProgramYearFact> facts)`
  - `List<RagProgramYearFact> getYearFacts()`
  - `Integer getYearStart()`
  - `Integer getYearEnd()`
  - `String getYearsAvailableCsv()`
  - `RagProgramYearFact getPrimaryFact()`
  - `RagDataQualityDocument(RagProgramHistoryDocument history)`
  - `List<Integer> getMissingYears()`
  - `String getDataCompleteness()`

- [ ] **Step 1: Write failing model tests**

Add this test file:

```java
package com.ruoyi.postgrad.rag.knowledge;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class RagProgramHistoryDocumentTest {

    @Test
    void sortsYearFactsAndExposesYearRange() {
        RagProgramYearFact y2025 = fact(1L, 148L, 2025, "B", null);
        RagProgramYearFact y2023 = fact(1L, 148L, 2023, "A", null);

        RagProgramHistoryDocument doc = new RagProgramHistoryDocument(List.of(y2025, y2023));

        assertEquals(2023, doc.getYearStart());
        assertEquals(2025, doc.getYearEnd());
        assertEquals("2023,2025", doc.getYearsAvailableCsv());
        assertEquals(2023, doc.getYearFacts().get(0).getYear());
        assertEquals(2025, doc.getYearFacts().get(1).getYear());
        assertEquals(148L, doc.getPrimaryFact().getProgramId());
    }

    @Test
    void dataQualityReportsMissingYearsBetweenRange() {
        RagProgramHistoryDocument history = new RagProgramHistoryDocument(List.of(
            fact(1L, 148L, 2023, "A", null),
            fact(1L, 148L, 2025, "B", "[\"score\"]")
        ));

        RagDataQualityDocument quality = new RagDataQualityDocument(history);

        assertEquals(List.of(2024), quality.getMissingYears());
        assertEquals("partial", quality.getDataCompleteness());
        assertTrue(quality.getMergedMissingFields().contains("score"));
    }

    private static RagProgramYearFact fact(Long schoolId, Long programId, Integer year,
                                           String qualityLevel, String missingFields) {
        RagProgramYearFact fact = new RagProgramYearFact();
        fact.setSchoolId(schoolId);
        fact.setSchoolName("广州大学");
        fact.setProvince("广东");
        fact.setCity("广州");
        fact.setSchoolTier("PUBLIC_REGULAR");
        fact.setCollegeId(128L);
        fact.setCollegeName("网络空间安全学院");
        fact.setProgramId(programId);
        fact.setProgramCode("083900");
        fact.setProgramName("网络空间安全");
        fact.setDegreeType("academic");
        fact.setStudyMode("full_time");
        fact.setExamType("GENERAL_500");
        fact.setIs408(true);
        fact.setExamSubjects("101 思想政治理论 / 201 英语（一） / 301 数学（一） / 408 计算机学科专业基础");
        fact.setYear(year);
        fact.setQualityLevel(qualityLevel);
        fact.setMissingFields(missingFields);
        fact.setSourceOwner("N诺考研择校");
        fact.setSourceName("N诺考研择校-广州大学");
        fact.setSourceUrl("https://noobdream.com/schoolinfo/135/");
        return fact;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagProgramHistoryDocumentTest test
```

Expected: compilation fails because `RagProgramHistoryDocument` and `RagDataQualityDocument` do not exist.

- [ ] **Step 3: Implement the minimal document models**

Create `RagProgramHistoryDocument.java`:

```java
package com.ruoyi.postgrad.rag.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RagProgramHistoryDocument {

    private final List<RagProgramYearFact> yearFacts;

    public RagProgramHistoryDocument(List<RagProgramYearFact> facts) {
        if (facts == null || facts.isEmpty()) {
            throw new IllegalArgumentException("program history facts must not be empty");
        }
        this.yearFacts = new ArrayList<>(facts);
        this.yearFacts.sort(Comparator.comparing(RagProgramYearFact::getYear,
            Comparator.nullsLast(Integer::compareTo)));
    }

    public List<RagProgramYearFact> getYearFacts() {
        return List.copyOf(yearFacts);
    }

    public RagProgramYearFact getPrimaryFact() {
        return yearFacts.get(yearFacts.size() - 1);
    }

    public Integer getYearStart() {
        return yearFacts.stream()
            .map(RagProgramYearFact::getYear)
            .filter(year -> year != null)
            .min(Integer::compareTo)
            .orElse(null);
    }

    public Integer getYearEnd() {
        return yearFacts.stream()
            .map(RagProgramYearFact::getYear)
            .filter(year -> year != null)
            .max(Integer::compareTo)
            .orElse(null);
    }

    public String getYearsAvailableCsv() {
        return yearFacts.stream()
            .map(RagProgramYearFact::getYear)
            .filter(year -> year != null)
            .map(String::valueOf)
            .distinct()
            .collect(Collectors.joining(","));
    }
}
```

Create `RagDataQualityDocument.java`:

```java
package com.ruoyi.postgrad.rag.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RagDataQualityDocument {

    private final RagProgramHistoryDocument history;

    public RagDataQualityDocument(RagProgramHistoryDocument history) {
        if (history == null) {
            throw new IllegalArgumentException("history must not be null");
        }
        this.history = history;
    }

    public RagProgramHistoryDocument getHistory() {
        return history;
    }

    public RagProgramYearFact getPrimaryFact() {
        return history.getPrimaryFact();
    }

    public List<Integer> getMissingYears() {
        Integer start = history.getYearStart();
        Integer end = history.getYearEnd();
        if (start == null || end == null || start >= end) {
            return List.of();
        }
        Set<Integer> present = new LinkedHashSet<>();
        for (RagProgramYearFact fact : history.getYearFacts()) {
            if (fact.getYear() != null) {
                present.add(fact.getYear());
            }
        }
        List<Integer> missing = new ArrayList<>();
        for (int year = start; year <= end; year++) {
            if (!present.contains(year)) {
                missing.add(year);
            }
        }
        return missing;
    }

    public String getMergedMissingFields() {
        Set<String> fields = new LinkedHashSet<>();
        for (RagProgramYearFact fact : history.getYearFacts()) {
            String missing = fact.getMissingFields();
            if (missing == null || missing.isBlank() || "[]".equals(missing.trim())) {
                continue;
            }
            String cleaned = missing.replace("[", "").replace("]", "").replace("\"", "");
            for (String item : cleaned.split(",")) {
                String field = item.trim();
                if (!field.isEmpty()) {
                    fields.add(field);
                }
            }
        }
        return String.join(",", fields);
    }

    public String getDataCompleteness() {
        if (!getMissingYears().isEmpty() || !getMergedMissingFields().isBlank()) {
            return "partial";
        }
        return "complete";
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagProgramHistoryDocumentTest test
```

Expected: test passes.

---

### Task 2: Export Program History Facts From MySQL

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/mapper/RagKnowledgeExportMapper.java`
- Modify: `ruoyi-postgrad/src/main/resources/mapper/postgrad/RagKnowledgeExportMapper.xml`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagKnowledgeExportService.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/RagKnowledgeExportServiceTest.java`

**Interfaces:**
- Consumes: `RagProgramYearFact`
- Produces:
  - `List<RowMap> selectProgramHistoryFacts(Integer startYear, Integer endYear, int limit)`
  - `List<RagProgramHistoryDocument> exportProgramHistoryDocuments(Integer startYear, Integer endYear, int limit)`
  - `List<RagDataQualityDocument> exportDataQualityDocuments(Integer startYear, Integer endYear, int limit)`

- [ ] **Step 1: Write failing service test**

Add a test that stubs mapper rows for two years of the same program and asserts one grouped history document.

```java
@Test
void exportProgramHistoryDocumentsGroupsRowsByProgramId() {
    when(mapper.selectProgramHistoryFacts(2023, 2025, 100)).thenReturn(List.of(
        row(1L, 148L, 2025, 320),
        row(1L, 148L, 2023, 305)
    ));

    List<RagProgramHistoryDocument> docs = service.exportProgramHistoryDocuments(2023, 2025, 100);

    assertEquals(1, docs.size());
    assertEquals(2023, docs.get(0).getYearStart());
    assertEquals(2025, docs.get(0).getYearEnd());
    assertEquals(2, docs.get(0).getYearFacts().size());
}
```

The existing `row(...)` helper should populate the same keys used by `toFact`: `schoolId`, `schoolName`, `programId`, `programName`, `year`, `avgAdmittedScore`, and source fields.

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagKnowledgeExportServiceTest test
```

Expected: compilation fails because `exportProgramHistoryDocuments` and `selectProgramHistoryFacts` do not exist.

- [ ] **Step 3: Add mapper interface method**

Add to `RagKnowledgeExportMapper.java`:

```java
List<RowMap> selectProgramHistoryFacts(@Param("startYear") Integer startYear,
                                       @Param("endYear") Integer endYear,
                                       @Param("limit") int limit);
```

- [ ] **Step 4: Add MyBatis query**

Add this select to `RagKnowledgeExportMapper.xml`. It mirrors `selectProgramYearFacts`, but uses a year range and orders by program then year:

```xml
<select id="selectProgramHistoryFacts" resultType="com.ruoyi.postgrad.domain.RowMap">
    select
        s.id schoolId,
        s.name schoolName,
        s.province province,
        s.city city,
        s.tier schoolTier,
        c.id collegeId,
        c.name collegeName,
        p.id programId,
        p.program_code programCode,
        p.program_name programName,
        p.research_direction researchDirection,
        p.degree_type degreeType,
        p.study_mode studyMode,
        p.exam_type examType,
        p.is_408 is408,
        subj.examSubjects examSubjects,
        coalesce(sc.year, ap.year, ar.year, q.year) year,
        sc.score_line scoreLine,
        sc.single_politics singlePolitics,
        sc.single_english singleEnglish,
        sc.single_math singleMath,
        sc.single_professional singleProfessional,
        ap.total_plan totalPlan,
        ap.unified_exam_quota unifiedExamQuota,
        ap.retest_count retestCount,
        ar.admitted_count admittedCount,
        ar.min_admitted_score minAdmittedScore,
        ar.avg_admitted_score avgAdmittedScore,
        ar.max_admitted_score maxAdmittedScore,
        coalesce(q.completeness_level,
            case
                when sc.score_line is not null
                    and ar.min_admitted_score is not null
                    and ar.max_admitted_score is not null
                    and ar.avg_admitted_score is not null
                    and (ap.total_plan is not null or ap.unified_exam_quota is not null or ar.admitted_count is not null)
                    then 'A'
                when sc.score_line is not null
                    and (ar.avg_admitted_score is not null
                        or ar.min_admitted_score is not null
                        or ap.total_plan is not null
                        or ap.unified_exam_quota is not null)
                    then 'B'
                when sc.score_line is not null then 'C'
                else 'D'
            end
        ) qualityLevel,
        q.has_official_source hasOfficialSource,
        q.has_conflict hasConflict,
        q.missing_fields missingFields,
        coalesce(ar_ds.source_owner, sc_ds.source_owner, ap_ds.source_owner, '数据库') sourceOwner,
        coalesce(ar_ds.title, sc_ds.title, ap_ds.title, concat(s.name, ' ', coalesce(sc.year, ap.year, ar.year, q.year), ' ', p.program_name, ' 数据')) sourceName,
        coalesce(ar_ds.url, sc_ds.url, ap_ds.url) sourceUrl
    from program p
        join college c on c.id = p.college_id
        join school s on s.id = c.school_id
        left join (
            select ps.program_id,
                   group_concat(concat(sub.code, ' ', sub.name) order by ps.subject_order separator ' / ') examSubjects
            from program_subject ps
                left join subject sub on sub.id = ps.subject_id
            group by ps.program_id
        ) subj on subj.program_id = p.id
        left join admission_score sc on sc.program_id = p.id and sc.year between #{startYear} and #{endYear}
        left join admission_plan ap on ap.program_id = p.id and ap.year = sc.year
        left join admission_result ar on ar.program_id = p.id and ar.year = coalesce(sc.year, ap.year)
        left join program_year_data_quality q on q.program_id = p.id and q.year = coalesce(sc.year, ap.year, ar.year)
        left join data_source sc_ds on sc_ds.id = sc.source_id
        left join data_source ap_ds on ap_ds.id = ap.source_id
        left join data_source ar_ds on ar_ds.id = ar.source_id
    where p.status = 'active'
      and s.status = 'active'
      and coalesce(sc.year, ap.year, ar.year, q.year) between #{startYear} and #{endYear}
    order by s.name, c.name, p.program_code, p.program_name, coalesce(sc.year, ap.year, ar.year, q.year)
    limit #{limit}
</select>
```

- [ ] **Step 5: Add grouping methods to service**

Add methods to `RagKnowledgeExportService`:

```java
public List<RagProgramHistoryDocument> exportProgramHistoryDocuments(Integer startYear, Integer endYear, int limit) {
    List<RowMap> rows = mapper.selectProgramHistoryFacts(startYear, endYear, limit);
    Map<Long, List<RagProgramYearFact>> grouped = new LinkedHashMap<>();
    for (RowMap row : rows) {
        RagProgramYearFact fact = toFact(row);
        if (fact.getProgramId() == null) continue;
        grouped.computeIfAbsent(fact.getProgramId(), ignored -> new ArrayList<>()).add(fact);
    }
    List<RagProgramHistoryDocument> documents = new ArrayList<>();
    for (List<RagProgramYearFact> facts : grouped.values()) {
        documents.add(new RagProgramHistoryDocument(facts));
    }
    return documents;
}

public List<RagDataQualityDocument> exportDataQualityDocuments(Integer startYear, Integer endYear, int limit) {
    List<RagDataQualityDocument> documents = new ArrayList<>();
    for (RagProgramHistoryDocument history : exportProgramHistoryDocuments(startYear, endYear, limit)) {
        documents.add(new RagDataQualityDocument(history));
    }
    return documents;
}
```

Also import `java.util.LinkedHashMap` and `java.util.Map`.

- [ ] **Step 6: Run test to verify it passes**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagKnowledgeExportServiceTest test
```

Expected: test passes.

---

### Task 3: Render V2 Markdown Directory Layout

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagMarkdownDocumentBuilder.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/RagMarkdownDocumentService.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/RagMarkdownDocumentServiceTest.java`

**Interfaces:**
- Consumes:
  - `RagProgramHistoryDocument`
  - `RagProgramYearFact`
  - `RagDataQualityDocument`
- Produces:
  - `String buildProgramHistoryDocument(RagProgramHistoryDocument document)`
  - `String buildProgramYearDocument(RagProgramYearFact fact)` with `doc_type: "program_year"`
  - `String buildDataQualityDocument(RagDataQualityDocument document)`
  - `RagMarkdownExportResult generateV2Documents(Integer startYear, Integer endYear, int limit)`

- [ ] **Step 1: Write failing Markdown test**

Add test assertions:

```java
@Test
void generateV2DocumentsWritesHistoryYearAndQualityDirectories() {
    when(exportService.exportProgramHistoryDocuments(2023, 2025, 100)).thenReturn(List.of(history()));
    when(exportService.exportProgramYearFacts(2025, 100)).thenReturn(List.of(fact(2025)));
    when(exportService.exportDataQualityDocuments(2023, 2025, 100)).thenReturn(List.of(new RagDataQualityDocument(history())));

    RagMarkdownExportResult result = service.generateV2Documents(2023, 2025, 100);

    assertEquals(3, result.getDocumentCount());
    assertTrue(Files.exists(tempDir.resolve("program-history/program_history_1_148.md")));
    assertTrue(Files.exists(tempDir.resolve("program-year/2025/program_year_1_148_2025.md")));
    assertTrue(Files.exists(tempDir.resolve("data-quality/program_quality_1_148.md")));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagMarkdownDocumentServiceTest test
```

Expected: compilation fails because `generateV2Documents` does not exist.

- [ ] **Step 3: Update builder metadata**

Change program-year front matter from:

```yaml
doc_type: "program_year_summary"
```

to:

```yaml
schema_version: "rag-doc-v2"
doc_type: "program_year"
doc_id: "program_year_{schoolId}_{programId}_{year}"
embedding_version: "bge-m3-v1"
```

Add `buildProgramHistoryDocument` and `buildDataQualityDocument` using the sections from the design spec.

- [ ] **Step 4: Update service writer**

Add `generateV2Documents` to write:

```text
program-history/program_history_{schoolId}_{programId}.md
program-year/{year}/program_year_{schoolId}_{programId}_{year}.md
data-quality/program_quality_{schoolId}_{programId}.md
```

The method must create directories with `Files.createDirectories(...)` before writing files.

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagMarkdownDocumentServiceTest test
```

Expected: test passes and no files are written outside the test temp directory.

---

### Task 4: Generate V2 Structured Chunks

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/knowledge/StructuredRagChunkBuilder.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/knowledge/StructuredRagChunkBuilderTest.java`

**Interfaces:**
- Consumes:
  - `RagProgramHistoryDocument`
  - `RagProgramYearFact`
  - `RagDataQualityDocument`
- Produces:
  - `List<StructuredRagChunk> buildProgramHistoryChunks(RagProgramHistoryDocument document)`
  - `List<StructuredRagChunk> buildProgramYearChunks(RagProgramYearFact fact)` with v2 metadata
  - `List<StructuredRagChunk> buildDataQualityChunks(RagDataQualityDocument document)`

- [ ] **Step 1: Write failing chunk metadata test**

Add assertions:

```java
@Test
void programHistoryChunksUseV2MetadataAndSemanticSections() {
    List<StructuredRagChunk> chunks = builder.buildProgramHistoryChunks(history());

    assertTrue(chunks.stream().anyMatch(c -> "score_trend".equals(c.getMetadata().get("section"))));
    StructuredRagChunk first = chunks.get(0);
    assertEquals("rag-doc-v2", first.getMetadata().get("schema_version"));
    assertEquals("program_history", first.getMetadata().get("doc_type"));
    assertEquals("148", first.getMetadata().get("program_id"));
    assertEquals("1", first.getMetadata().get("school_id"));
    assertNotNull(first.getMetadata().get("content_hash"));
    assertEquals("bge-m3-v1", first.getMetadata().get("embedding_version"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=StructuredRagChunkBuilderTest test
```

Expected: compilation fails because `buildProgramHistoryChunks` does not exist or assertions fail against old metadata.

- [ ] **Step 3: Implement semantic v2 chunks**

Use these section lists:

```java
program_history: basic_info, exam_subjects, score_trend, admission_trend, plan_trend, risk_factors, source_quality
program_year: basic_info, exam_subjects, admission_score, admission_plan, admission_result, source_quality
data_quality: coverage_summary, missing_years, missing_fields, source_conflicts, recommendation_warning
```

Every chunk metadata map must include:

```text
schema_version
doc_type
doc_id
chunk_id
chunk_index
section
section_title
school_id
school_name
program_id
program_name
year
year_start
year_end
years_available
quality_level
missing_fields
content_hash
generated_at
embedding_version
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=StructuredRagChunkBuilderTest test
```

Expected: test passes.

---

### Task 5: Wire V2 Generation And Indexing

**Files:**
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/RagDocumentService.java`
- Modify: `ruoyi-postgrad/src/main/java/com/ruoyi/postgrad/rag/controller/RagAdminController.java`
- Modify: `ruoyi-postgrad/src/test/java/com/ruoyi/postgrad/rag/RagDocumentServiceTest.java`

**Interfaces:**
- Consumes:
  - `RagKnowledgeExportService.exportProgramHistoryDocuments`
  - `RagKnowledgeExportService.exportProgramYearFacts`
  - `RagKnowledgeExportService.exportDataQualityDocuments`
  - `StructuredRagChunkBuilder` v2 methods
- Produces:
  - `RagIndexResult reindexDatabaseKnowledge(Integer startYear, Integer endYear, int limit)`
  - `POST /postgrad/rag/reindex-database?startYear=2023&endYear=2025&limit=1000`
  - `POST /postgrad/rag/generate-documents?startYear=2023&endYear=2025&limit=1000`

- [ ] **Step 1: Write failing indexing test**

Update `RagDocumentServiceTest` so database reindex expects chunks from all three document families:

```java
@Test
void reindexDatabaseKnowledgeIndexesProgramHistoryYearAndQualityChunks() {
    when(exportService.exportProgramHistoryDocuments(2023, 2025, 100)).thenReturn(List.of(history()));
    when(exportService.exportProgramYearFacts(2025, 100)).thenReturn(List.of(fact(2025)));
    when(exportService.exportDataQualityDocuments(2023, 2025, 100)).thenReturn(List.of(new RagDataQualityDocument(history())));
    when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);
    when(embeddingModel.embedAll(anyList())).thenReturn(embeddingResponseFor(3));

    RagIndexResult result = service.reindexDatabaseKnowledge(2023, 2025, 100);

    assertTrue(result.getChunkCount() >= 3);
    verify(embeddingStore).addAll(anyList(), anyList());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagDocumentServiceTest test
```

Expected: compilation fails because `reindexDatabaseKnowledge(Integer,Integer,int)` does not exist.

- [ ] **Step 3: Implement new service signature**

Replace old single-year database reindex body with:

```java
List<StructuredRagChunk> chunks = new ArrayList<>();
for (RagProgramHistoryDocument history : ragKnowledgeExportService.exportProgramHistoryDocuments(startYear, endYear, limit)) {
    chunks.addAll(chunkBuilder.buildProgramHistoryChunks(history));
}
for (RagProgramYearFact fact : ragKnowledgeExportService.exportProgramYearFacts(endYear, limit)) {
    chunks.addAll(chunkBuilder.buildProgramYearChunks(fact));
}
for (RagDataQualityDocument quality : ragKnowledgeExportService.exportDataQualityDocuments(startYear, endYear, limit)) {
    chunks.addAll(chunkBuilder.buildDataQualityChunks(quality));
}
```

Keep the existing embedding and Qdrant upsert flow.

- [ ] **Step 4: Update admin endpoints**

`RagAdminController` should accept `startYear`, `endYear`, and `limit`:

```java
public AjaxResult reindexDatabase(@RequestParam(defaultValue = "2023") Integer startYear,
                                  @RequestParam(defaultValue = "2025") Integer endYear,
                                  @RequestParam(defaultValue = "1000") int limit)
```

Do the same for document generation.

- [ ] **Step 5: Run test to verify it passes**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagDocumentServiceTest test
```

Expected: test passes.

---

### Task 6: Verify Full RAG Document V2 Slice

**Files:**
- Verify only; no new production files.

**Interfaces:**
- Consumes all previous tasks.
- Produces a verified implementation ready for review.

- [ ] **Step 1: Run focused RAG tests**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagProgramHistoryDocumentTest,RagKnowledgeExportServiceTest,RagMarkdownDocumentServiceTest,StructuredRagChunkBuilderTest,RagDocumentServiceTest test
```

Expected: all listed tests pass.

- [ ] **Step 2: Run DTO/search tests affected by metadata**

Run:

```bash
mvn -pl ruoyi-postgrad -Dtest=RagDtoTest,RagMetadataFilterBuilderTest,RagSearchTestServiceTest test
```

Expected: all listed tests pass.

- [ ] **Step 3: Inspect Git status**

Run:

```bash
git status --short
```

Expected:

```text
modified/new Java and test files for the RAG v2 pipeline
no ruoyi-postgrad/src/main/resources/rag-generated bulk markdown files unless explicitly generated for manual inspection
```

- [ ] **Step 4: Manual smoke command for document generation**

Run only if the app context and database are available:

```bash
curl -X POST "http://localhost:8080/postgrad/rag/generate-documents?startYear=2023&endYear=2025&limit=20"
```

Expected: response reports generated documents under `program-history`, `program-year/{endYear}`, and `data-quality`.

Do not commit the generated Markdown files unless the user explicitly asks to version generated RAG documents.

