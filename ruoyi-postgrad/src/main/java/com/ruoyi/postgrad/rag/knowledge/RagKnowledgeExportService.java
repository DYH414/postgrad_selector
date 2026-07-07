package com.ruoyi.postgrad.rag.knowledge;

import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.mapper.RagKnowledgeExportMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RagKnowledgeExportService {

    private final RagKnowledgeExportMapper mapper;
    private final StructuredRagChunkBuilder chunkBuilder;

    public RagKnowledgeExportService(RagKnowledgeExportMapper mapper, StructuredRagChunkBuilder chunkBuilder) {
        this.mapper = mapper;
        this.chunkBuilder = chunkBuilder;
    }

    public List<StructuredRagChunk> exportProgramYearChunks(Integer year, int limit) {
        List<RagProgramYearFact> facts = exportProgramYearFacts(year, limit);
        List<StructuredRagChunk> chunks = new ArrayList<>();
        for (RagProgramYearFact fact : facts) {
            chunks.addAll(chunkBuilder.buildProgramYearChunks(fact));
        }
        return chunks;
    }

    public List<RagProgramYearFact> exportProgramYearFacts(Integer year, int limit) {
        List<RowMap> rows = mapper.selectProgramYearFacts(year, limit);
        List<RagProgramYearFact> facts = new ArrayList<>();
        for (RowMap row : rows) {
            facts.add(toFact(row));
        }
        return facts;
    }

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

    private RagProgramYearFact toFact(RowMap row) {
        RagProgramYearFact fact = new RagProgramYearFact();
        fact.setSchoolId(asLong(row.get("schoolId")));
        fact.setSchoolName(asString(row.get("schoolName")));
        fact.setProvince(asString(row.get("province")));
        fact.setCity(asString(row.get("city")));
        fact.setSchoolTier(asString(row.get("schoolTier")));
        fact.setCollegeId(asLong(row.get("collegeId")));
        fact.setCollegeName(asString(row.get("collegeName")));
        fact.setProgramId(asLong(row.get("programId")));
        fact.setProgramCode(asString(row.get("programCode")));
        fact.setProgramName(asString(row.get("programName")));
        fact.setResearchDirection(asString(row.get("researchDirection")));
        fact.setDegreeType(asString(row.get("degreeType")));
        fact.setStudyMode(asString(row.get("studyMode")));
        fact.setExamType(asString(row.get("examType")));
        fact.setIs408(asBoolean(row.get("is408")));
        fact.setExamSubjects(asString(row.get("examSubjects")));
        fact.setYear(asInteger(row.get("year")));
        fact.setScoreLine(asInteger(row.get("scoreLine")));
        fact.setSinglePolitics(asInteger(row.get("singlePolitics")));
        fact.setSingleEnglish(asInteger(row.get("singleEnglish")));
        fact.setSingleMath(asInteger(row.get("singleMath")));
        fact.setSingleProfessional(asInteger(row.get("singleProfessional")));
        fact.setTotalPlan(asInteger(row.get("totalPlan")));
        fact.setUnifiedExamQuota(asInteger(row.get("unifiedExamQuota")));
        fact.setRetestCount(asInteger(row.get("retestCount")));
        fact.setAdmittedCount(asInteger(row.get("admittedCount")));
        fact.setMinAdmittedScore(asInteger(row.get("minAdmittedScore")));
        fact.setAvgAdmittedScore(asInteger(row.get("avgAdmittedScore")));
        fact.setMaxAdmittedScore(asInteger(row.get("maxAdmittedScore")));
        fact.setQualityLevel(asString(row.get("qualityLevel")));
        fact.setHasOfficialSource(asBoolean(row.get("hasOfficialSource")));
        fact.setHasConflict(asBoolean(row.get("hasConflict")));
        fact.setMissingFields(asString(row.get("missingFields")));
        fact.setSourceOwner(asString(row.get("sourceOwner")));
        fact.setSourceName(asString(row.get("sourceName")));
        fact.setSourceUrl(asString(row.get("sourceUrl")));
        return fact;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private static Integer asInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        String text = String.valueOf(value);
        return text.isBlank() ? null : Integer.valueOf(text);
    }

    private static Boolean asBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "是".equals(text);
    }
}
