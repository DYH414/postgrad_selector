package com.ruoyi.postgrad.rag.knowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StructuredRagChunkBuilder {

    private static final String SCHEMA_VERSION = "rag-doc-v2";
    private static final String DOC_TYPE_PROGRAM_HISTORY = "program_history";
    private static final String DOC_TYPE_PROGRAM_YEAR = "program_year";
    private static final String DOC_TYPE_DATA_QUALITY = "data_quality";
    private static final String EMBEDDING_VERSION = "bge-m3-v1";

    public List<StructuredRagChunk> buildProgramYearChunks(RagProgramYearFact fact) {
        List<StructuredRagChunk> chunks = new ArrayList<>();
        chunks.add(chunk(fact, "basic_info", "基本信息", basicInfo(fact)));
        chunks.add(chunk(fact, "exam_subjects", "考试科目", examSubjects(fact)));
        chunks.add(chunk(fact, "admission_score", "复试线", score(fact)));
        chunks.add(chunk(fact, "admission_plan", "招生计划", plan(fact)));
        chunks.add(chunk(fact, "admission_result", "录取结果", result(fact)));
        chunks.add(chunk(fact, "source_quality", "来源与质量", sourceQuality(fact)));
        return chunks;
    }

    public List<StructuredRagChunk> buildProgramHistoryChunks(RagProgramHistoryDocument document) {
        List<StructuredRagChunk> chunks = new ArrayList<>();
        chunks.add(historyChunk(document, "basic_info", "基本信息", basicInfo(document.getPrimaryFact())));
        chunks.add(historyChunk(document, "exam_subjects", "考试科目", examSubjects(document.getPrimaryFact())));
        chunks.add(historyChunk(document, "score_trend", "历年分数趋势", scoreTrend(document)));
        chunks.add(historyChunk(document, "admission_trend", "历年录取趋势", admissionTrend(document)));
        chunks.add(historyChunk(document, "plan_trend", "历年招生计划", planTrend(document)));
        chunks.add(historyChunk(document, "risk_factors", "推荐风险提示", riskFactors(document)));
        chunks.add(historyChunk(document, "source_quality", "来源与质量", historySourceQuality(document)));
        return chunks;
    }

    public List<StructuredRagChunk> buildDataQualityChunks(RagDataQualityDocument document) {
        List<StructuredRagChunk> chunks = new ArrayList<>();
        chunks.add(qualityChunk(document, "coverage_summary", "覆盖概览",
            "数据完整度: " + document.getDataCompleteness()
                + "\n可用年份: " + document.getHistory().getYearsAvailableCsv()));
        chunks.add(qualityChunk(document, "missing_years", "缺失年份",
            document.getMissingYears().isEmpty() ? "暂无年份断档。" : "缺失年份: " + joinList(document.getMissingYears())));
        chunks.add(qualityChunk(document, "missing_fields", "缺失字段",
            isBlank(document.getMergedMissingFields()) ? "暂无明确缺失字段。" : "缺失字段: " + document.getMergedMissingFields()));
        chunks.add(qualityChunk(document, "source_conflicts", "来源冲突",
            hasConflict(document.getHistory()) ? "存在来源冲突记录。" : "暂无来源冲突记录。"));
        chunks.add(qualityChunk(document, "recommendation_warning", "推荐提示",
            document.getDataCompleteness().equals("complete")
                ? "该专业历年数据较完整，可作为推荐报告证据。"
                : "该专业存在数据缺失，推荐报告应提示证据完整度有限。"));
        return chunks;
    }

    private StructuredRagChunk chunk(RagProgramYearFact fact, String section, String sectionTitle, String body) {
        String title = titlePrefix(fact) + " - " + sectionTitle;
        String text = "# " + title + "\n" + body;
        String hash = sha256(text);
        String chunkId = DOC_TYPE_PROGRAM_YEAR + ":" + value(fact.getSchoolId()) + ":"
            + value(fact.getProgramId()) + ":" + value(fact.getYear()) + ":" + section + ":" + hash.substring(0, 12);
        Map<String, String> metadata = baseMetadata(fact, section, title, hash);
        metadata.put("chunk_id", chunkId);
        return new StructuredRagChunk(chunkId, title, text, metadata);
    }

    private StructuredRagChunk historyChunk(RagProgramHistoryDocument document, String section,
                                            String sectionTitle, String body) {
        RagProgramYearFact fact = document.getPrimaryFact();
        String title = value(fact.getSchoolName()) + " " + value(fact.getProgramName()) + " 历年 - " + sectionTitle;
        String text = "# " + title + "\n" + body;
        String hash = sha256(text);
        String chunkId = DOC_TYPE_PROGRAM_HISTORY + ":" + value(fact.getSchoolId()) + ":"
            + value(fact.getProgramId()) + ":" + section + ":" + hash.substring(0, 12);
        Map<String, String> metadata = baseMetadata(fact, DOC_TYPE_PROGRAM_HISTORY,
            "program_history_" + value(fact.getSchoolId()) + "_" + value(fact.getProgramId()),
            section, title, hash);
        metadata.put("year", "");
        metadata.put("year_start", value(document.getYearStart()));
        metadata.put("year_end", value(document.getYearEnd()));
        metadata.put("years_available", document.getYearsAvailableCsv());
        metadata.put("chunk_id", chunkId);
        return new StructuredRagChunk(chunkId, title, text, metadata);
    }

    private StructuredRagChunk qualityChunk(RagDataQualityDocument document, String section,
                                            String sectionTitle, String body) {
        RagProgramYearFact fact = document.getPrimaryFact();
        String title = value(fact.getSchoolName()) + " " + value(fact.getProgramName()) + " 数据质量 - " + sectionTitle;
        String text = "# " + title + "\n" + body;
        String hash = sha256(text);
        String chunkId = DOC_TYPE_DATA_QUALITY + ":" + value(fact.getSchoolId()) + ":"
            + value(fact.getProgramId()) + ":" + section + ":" + hash.substring(0, 12);
        Map<String, String> metadata = baseMetadata(fact, DOC_TYPE_DATA_QUALITY,
            "program_quality_" + value(fact.getSchoolId()) + "_" + value(fact.getProgramId()),
            section, title, hash);
        metadata.put("year", "");
        metadata.put("year_start", value(document.getHistory().getYearStart()));
        metadata.put("year_end", value(document.getHistory().getYearEnd()));
        metadata.put("years_available", document.getHistory().getYearsAvailableCsv());
        metadata.put("data_completeness", document.getDataCompleteness());
        metadata.put("chunk_id", chunkId);
        return new StructuredRagChunk(chunkId, title, text, metadata);
    }

    private Map<String, String> baseMetadata(RagProgramYearFact fact, String section, String title, String hash) {
        return baseMetadata(fact, DOC_TYPE_PROGRAM_YEAR,
            "program_year_" + value(fact.getSchoolId()) + "_" + value(fact.getProgramId()) + "_" + value(fact.getYear()),
            section, title, hash);
    }

    private Map<String, String> baseMetadata(RagProgramYearFact fact, String docType, String docId,
                                             String section, String title, String hash) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("schema_version", SCHEMA_VERSION);
        metadata.put("doc_type", docType);
        metadata.put("doc_id", docId);
        metadata.put("school_id", value(fact.getSchoolId()));
        metadata.put("school_name", value(fact.getSchoolName()));
        metadata.put("province", value(fact.getProvince()));
        metadata.put("city", value(fact.getCity()));
        metadata.put("school_tier", value(fact.getSchoolTier()));
        metadata.put("college_id", value(fact.getCollegeId()));
        metadata.put("college_name", value(fact.getCollegeName()));
        metadata.put("program_id", value(fact.getProgramId()));
        metadata.put("program_code", value(fact.getProgramCode()));
        metadata.put("program_name", value(fact.getProgramName()));
        metadata.put("year", value(fact.getYear()));
        metadata.put("section", section);
        metadata.put("chunk_title", title);
        metadata.put("source_owner", value(fact.getSourceOwner()));
        metadata.put("source_name", value(fact.getSourceName()));
        metadata.put("source_url", value(fact.getSourceUrl()));
        metadata.put("quality_level", value(fact.getQualityLevel()));
        metadata.put("missing_fields", value(fact.getMissingFields()));
        metadata.put("has_official_source", value(fact.getHasOfficialSource()));
        metadata.put("has_conflict", value(fact.getHasConflict()));
        metadata.put("content_hash", hash);
        metadata.put("generated_at", LocalDateTime.now().toString());
        metadata.put("embedding_version", EMBEDDING_VERSION);
        return metadata;
    }

    private String basicInfo(RagProgramYearFact fact) {
        return lines(
            "学校: " + value(fact.getSchoolName()),
            "地区: " + join(" / ", fact.getProvince(), fact.getCity()),
            "层次: " + value(fact.getSchoolTier()),
            "院系: " + value(fact.getCollegeName()),
            "专业: " + value(fact.getProgramCode()) + " " + value(fact.getProgramName()),
            "研究方向: " + value(fact.getResearchDirection()),
            "培养方式: " + value(fact.getDegreeType()) + " / " + value(fact.getStudyMode()),
            "考试类型: " + value(fact.getExamType()),
            "是否 408: " + (Boolean.TRUE.equals(fact.getIs408()) ? "是" : "否")
        );
    }

    private String examSubjects(RagProgramYearFact fact) {
        if (isBlank(fact.getExamSubjects())) {
            return "暂无考试科目数据。";
        }
        return "考试科目: " + fact.getExamSubjects();
    }

    private String score(RagProgramYearFact fact) {
        if (fact.getScoreLine() == null) {
            return "暂无复试线数据。";
        }
        return lines(
            "复试线: " + fact.getScoreLine(),
            "单科线: 政治 " + value(fact.getSinglePolitics())
                + "，英语 " + value(fact.getSingleEnglish())
                + "，数学 " + value(fact.getSingleMath())
                + "，专业课 " + value(fact.getSingleProfessional())
        );
    }

    private String plan(RagProgramYearFact fact) {
        if (fact.getTotalPlan() == null) {
            return "暂无招生计划数据。";
        }
        return lines(
            "招生计划总数: " + value(fact.getTotalPlan()),
            "统考名额: " + value(fact.getUnifiedExamQuota()),
            "复试人数: " + value(fact.getRetestCount())
        );
    }

    private String result(RagProgramYearFact fact) {
        if (fact.getAdmittedCount() == null) {
            return "暂无录取结果数据。";
        }
        return lines(
            "录取人数: " + value(fact.getAdmittedCount()),
            "最低录取分: " + value(fact.getMinAdmittedScore()),
            "平均录取分: " + value(fact.getAvgAdmittedScore()),
            "最高录取分: " + value(fact.getMaxAdmittedScore())
        );
    }

    private String sourceQuality(RagProgramYearFact fact) {
        return lines(
            "数据质量等级: " + value(fact.getQualityLevel()),
            "缺失字段: " + (isBlank(fact.getMissingFields()) ? "无" : fact.getMissingFields()),
            "是否有官方来源: " + (Boolean.TRUE.equals(fact.getHasOfficialSource()) ? "是" : "否"),
            "是否存在冲突: " + (Boolean.TRUE.equals(fact.getHasConflict()) ? "是" : "否"),
            "来源: " + value(fact.getSourceOwner()) + " - " + value(fact.getSourceName()),
            "来源链接: " + value(fact.getSourceUrl())
        );
    }

    private String scoreTrend(RagProgramHistoryDocument document) {
        StringBuilder builder = new StringBuilder();
        builder.append("| 年份 | 复试线 | 最低录取分 | 平均录取分 | 最高录取分 |\n");
        builder.append("|---|---:|---:|---:|---:|\n");
        for (RagProgramYearFact fact : document.getYearFacts()) {
            builder.append("| ").append(value(fact.getYear()))
                .append(" | ").append(value(fact.getScoreLine()))
                .append(" | ").append(value(fact.getMinAdmittedScore()))
                .append(" | ").append(value(fact.getAvgAdmittedScore()))
                .append(" | ").append(value(fact.getMaxAdmittedScore()))
                .append(" |\n");
        }
        return builder.toString().trim();
    }

    private String admissionTrend(RagProgramHistoryDocument document) {
        StringBuilder builder = new StringBuilder();
        builder.append("| 年份 | 录取人数 | 最低录取分 | 平均录取分 | 最高录取分 |\n");
        builder.append("|---|---:|---:|---:|---:|\n");
        for (RagProgramYearFact fact : document.getYearFacts()) {
            builder.append("| ").append(value(fact.getYear()))
                .append(" | ").append(value(fact.getAdmittedCount()))
                .append(" | ").append(value(fact.getMinAdmittedScore()))
                .append(" | ").append(value(fact.getAvgAdmittedScore()))
                .append(" | ").append(value(fact.getMaxAdmittedScore()))
                .append(" |\n");
        }
        return builder.toString().trim();
    }

    private String planTrend(RagProgramHistoryDocument document) {
        StringBuilder builder = new StringBuilder();
        builder.append("| 年份 | 招生计划 | 统考名额 | 复试人数 |\n");
        builder.append("|---|---:|---:|---:|\n");
        for (RagProgramYearFact fact : document.getYearFacts()) {
            builder.append("| ").append(value(fact.getYear()))
                .append(" | ").append(value(fact.getTotalPlan()))
                .append(" | ").append(value(fact.getUnifiedExamQuota()))
                .append(" | ").append(value(fact.getRetestCount()))
                .append(" |\n");
        }
        return builder.toString().trim();
    }

    private String riskFactors(RagProgramHistoryDocument document) {
        RagProgramYearFact primary = document.getPrimaryFact();
        return lines(
            "最近年份: " + value(primary.getYear()),
            "最近平均录取分: " + value(primary.getAvgAdmittedScore()),
            "最近统考名额: " + value(primary.getUnifiedExamQuota()),
            "如果录取均分高于用户估分较多，说明存在分数风险。",
            "如果统考名额较少，说明波动风险较高。",
            "如果年份数据缺失，报告中应提示数据可信度有限。"
        );
    }

    private String historySourceQuality(RagProgramHistoryDocument document) {
        RagProgramYearFact primary = document.getPrimaryFact();
        return lines(
            "可用年份: " + document.getYearsAvailableCsv(),
            "数据质量等级: " + value(primary.getQualityLevel()),
            "来源: " + value(primary.getSourceOwner()) + " - " + value(primary.getSourceName()),
            "来源链接: " + value(primary.getSourceUrl())
        );
    }

    private String titlePrefix(RagProgramYearFact fact) {
        return value(fact.getSchoolName()) + " " + value(fact.getProgramName()) + " " + value(fact.getYear());
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    private static String join(String separator, Object first, Object second) {
        if (isBlank(value(first))) return value(second);
        if (isBlank(value(second))) return value(first);
        return value(first) + separator + value(second);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String joinList(List<?> values) {
        StringBuilder builder = new StringBuilder();
        for (Object item : values) {
            if (!builder.isEmpty()) {
                builder.append(",");
            }
            builder.append(value(item));
        }
        return builder.toString();
    }

    private static boolean hasConflict(RagProgramHistoryDocument document) {
        for (RagProgramYearFact fact : document.getYearFacts()) {
            if (Boolean.TRUE.equals(fact.getHasConflict())) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
