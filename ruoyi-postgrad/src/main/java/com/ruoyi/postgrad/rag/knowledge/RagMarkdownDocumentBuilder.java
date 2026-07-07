package com.ruoyi.postgrad.rag.knowledge;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RagMarkdownDocumentBuilder {

    private static final String SCHEMA_VERSION = "rag-doc-v2";
    private static final String EMBEDDING_VERSION = "bge-m3-v1";

    public String buildProgramYearDocument(RagProgramYearFact fact) {
        StringBuilder builder = new StringBuilder();
        appendFrontMatter(builder, fact);
        builder.append("# ")
            .append(value(fact.getSchoolName())).append(" ")
            .append(value(fact.getProgramName())).append(" ")
            .append(value(fact.getYear())).append(" 考研信息\n\n");
        appendSection(builder, "基本信息", basicInfo(fact));
        appendSection(builder, "考试科目", examSubjects(fact));
        appendSection(builder, "复试线", score(fact));
        appendSection(builder, "招生计划", plan(fact));
        appendSection(builder, "录取结果", result(fact));
        appendSection(builder, "来源与质量", sourceQuality(fact));
        return builder.toString();
    }

    public String buildProgramHistoryDocument(RagProgramHistoryDocument document) {
        RagProgramYearFact primary = document.getPrimaryFact();
        StringBuilder builder = new StringBuilder();
        appendHistoryFrontMatter(builder, document);
        builder.append("# ")
            .append(value(primary.getSchoolName())).append(" ")
            .append(value(primary.getProgramName())).append(" 历年招生录取事实\n\n");
        appendSection(builder, "基本信息", basicInfo(primary));
        appendSection(builder, "考试科目", examSubjects(primary));
        appendSection(builder, "历年分数趋势", scoreTrend(document));
        appendSection(builder, "历年招生与录取", admissionTrend(document));
        appendSection(builder, "推荐风险提示", riskFactors(document));
        appendSection(builder, "来源与质量", historySourceQuality(document));
        return builder.toString();
    }

    public String buildDataQualityDocument(RagDataQualityDocument document) {
        RagProgramYearFact primary = document.getPrimaryFact();
        StringBuilder builder = new StringBuilder();
        appendQualityFrontMatter(builder, document);
        builder.append("# ")
            .append(value(primary.getSchoolName())).append(" ")
            .append(value(primary.getProgramName())).append(" 数据质量说明\n\n");
        appendSection(builder, "覆盖概览", "数据完整度: " + document.getDataCompleteness()
            + "\n可用年份: " + document.getHistory().getYearsAvailableCsv());
        appendSection(builder, "缺失年份", document.getMissingYears().isEmpty()
            ? "暂无年份断档。" : "缺失年份: " + joinList(document.getMissingYears()));
        appendSection(builder, "缺失字段", isBlank(document.getMergedMissingFields())
            ? "暂无明确缺失字段。" : "缺失字段: " + document.getMergedMissingFields());
        appendSection(builder, "来源冲突", hasConflict(document.getHistory()) ? "存在来源冲突记录。" : "暂无来源冲突记录。");
        appendSection(builder, "推荐提示", document.getDataCompleteness().equals("complete")
            ? "该专业历年数据较完整，可作为推荐报告证据。"
            : "该专业存在数据缺失，推荐报告应提示证据完整度有限。");
        return builder.toString();
    }

    String fileName(RagProgramYearFact fact) {
        return "program_year_" + value(fact.getSchoolId()) + "_"
            + value(fact.getProgramId()) + "_" + value(fact.getYear()) + ".md";
    }

    String historyFileName(RagProgramHistoryDocument document) {
        RagProgramYearFact fact = document.getPrimaryFact();
        return "program_history_" + value(fact.getSchoolId()) + "_"
            + value(fact.getProgramId()) + ".md";
    }

    String qualityFileName(RagDataQualityDocument document) {
        RagProgramYearFact fact = document.getPrimaryFact();
        return "program_quality_" + value(fact.getSchoolId()) + "_"
            + value(fact.getProgramId()) + ".md";
    }

    private void appendFrontMatter(StringBuilder builder, RagProgramYearFact fact) {
        builder.append("---\n");
        appendYaml(builder, "schema_version", SCHEMA_VERSION);
        appendYaml(builder, "doc_type", "program_year");
        appendYaml(builder, "doc_id", "program_year_" + value(fact.getSchoolId()) + "_"
            + value(fact.getProgramId()) + "_" + value(fact.getYear()));
        appendYaml(builder, "school_id", fact.getSchoolId());
        appendYaml(builder, "school_name", fact.getSchoolName());
        appendYaml(builder, "province", fact.getProvince());
        appendYaml(builder, "city", fact.getCity());
        appendYaml(builder, "school_tier", fact.getSchoolTier());
        appendYaml(builder, "college_id", fact.getCollegeId());
        appendYaml(builder, "college_name", fact.getCollegeName());
        appendYaml(builder, "program_id", fact.getProgramId());
        appendYaml(builder, "program_code", fact.getProgramCode());
        appendYaml(builder, "program_name", fact.getProgramName());
        appendYaml(builder, "year", fact.getYear());
        appendYaml(builder, "source_owner", fact.getSourceOwner());
        appendYaml(builder, "source_name", fact.getSourceName());
        appendYaml(builder, "source_url", fact.getSourceUrl());
        appendYaml(builder, "quality_level", fact.getQualityLevel());
        appendYaml(builder, "has_official_source", fact.getHasOfficialSource());
        appendYaml(builder, "has_conflict", fact.getHasConflict());
        appendYaml(builder, "missing_fields", fact.getMissingFields());
        appendYaml(builder, "embedding_version", EMBEDDING_VERSION);
        builder.append("---\n\n");
    }

    private void appendHistoryFrontMatter(StringBuilder builder, RagProgramHistoryDocument document) {
        RagProgramYearFact fact = document.getPrimaryFact();
        builder.append("---\n");
        appendYaml(builder, "schema_version", SCHEMA_VERSION);
        appendYaml(builder, "doc_type", "program_history");
        appendYaml(builder, "doc_id", "program_history_" + value(fact.getSchoolId()) + "_" + value(fact.getProgramId()));
        appendYaml(builder, "school_id", fact.getSchoolId());
        appendYaml(builder, "school_name", fact.getSchoolName());
        appendYaml(builder, "province", fact.getProvince());
        appendYaml(builder, "city", fact.getCity());
        appendYaml(builder, "school_tier", fact.getSchoolTier());
        appendYaml(builder, "college_id", fact.getCollegeId());
        appendYaml(builder, "college_name", fact.getCollegeName());
        appendYaml(builder, "program_id", fact.getProgramId());
        appendYaml(builder, "program_code", fact.getProgramCode());
        appendYaml(builder, "program_name", fact.getProgramName());
        appendYaml(builder, "year_start", document.getYearStart());
        appendYaml(builder, "year_end", document.getYearEnd());
        appendYaml(builder, "years_available", document.getYearsAvailableCsv());
        appendYaml(builder, "source_owner", fact.getSourceOwner());
        appendYaml(builder, "source_name", fact.getSourceName());
        appendYaml(builder, "source_url", fact.getSourceUrl());
        appendYaml(builder, "quality_level", fact.getQualityLevel());
        appendYaml(builder, "embedding_version", EMBEDDING_VERSION);
        builder.append("---\n\n");
    }

    private void appendQualityFrontMatter(StringBuilder builder, RagDataQualityDocument document) {
        RagProgramYearFact fact = document.getPrimaryFact();
        builder.append("---\n");
        appendYaml(builder, "schema_version", SCHEMA_VERSION);
        appendYaml(builder, "doc_type", "data_quality");
        appendYaml(builder, "doc_id", "program_quality_" + value(fact.getSchoolId()) + "_" + value(fact.getProgramId()));
        appendYaml(builder, "school_id", fact.getSchoolId());
        appendYaml(builder, "school_name", fact.getSchoolName());
        appendYaml(builder, "program_id", fact.getProgramId());
        appendYaml(builder, "program_name", fact.getProgramName());
        appendYaml(builder, "year_start", document.getHistory().getYearStart());
        appendYaml(builder, "year_end", document.getHistory().getYearEnd());
        appendYaml(builder, "years_available", document.getHistory().getYearsAvailableCsv());
        appendYaml(builder, "data_completeness", document.getDataCompleteness());
        appendYaml(builder, "missing_fields", document.getMergedMissingFields());
        appendYaml(builder, "source_owner", fact.getSourceOwner());
        appendYaml(builder, "source_name", fact.getSourceName());
        appendYaml(builder, "source_url", fact.getSourceUrl());
        appendYaml(builder, "embedding_version", EMBEDDING_VERSION);
        builder.append("---\n\n");
    }

    private void appendSection(StringBuilder builder, String title, String body) {
        builder.append("## ").append(title).append("\n");
        builder.append(body).append("\n\n");
    }

    private void appendYaml(StringBuilder builder, String key, Object value) {
        builder.append(key).append(": ");
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
        } else {
            builder.append("\"").append(escape(value(value))).append("\"");
        }
        builder.append("\n");
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
        builder.append("| 年份 | 招生计划 | 统考名额 | 复试人数 | 录取人数 |\n");
        builder.append("|---|---:|---:|---:|---:|\n");
        for (RagProgramYearFact fact : document.getYearFacts()) {
            builder.append("| ").append(value(fact.getYear()))
                .append(" | ").append(value(fact.getTotalPlan()))
                .append(" | ").append(value(fact.getUnifiedExamQuota()))
                .append(" | ").append(value(fact.getRetestCount()))
                .append(" | ").append(value(fact.getAdmittedCount()))
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

    private static boolean hasConflict(RagProgramHistoryDocument document) {
        for (RagProgramYearFact fact : document.getYearFacts()) {
            if (Boolean.TRUE.equals(fact.getHasConflict())) {
                return true;
            }
        }
        return false;
    }

    private static String lines(String... lines) {
        return String.join("\n", lines);
    }

    private static String joinList(List<?> values) {
        StringBuilder builder = new StringBuilder();
        for (Object value : values) {
            if (!builder.isEmpty()) {
                builder.append(",");
            }
            builder.append(value(value));
        }
        return builder.toString();
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
