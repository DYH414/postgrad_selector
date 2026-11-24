package com.ruoyi.postgrad.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.postgrad.domain.Program;
import com.ruoyi.postgrad.domain.UserFavoriteProgram;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApplicantProgramVisibilityTest
{
    private static final String FULL_TIME = "p.study_mode = 'full_time'";
    private static final String SUBJECT_408 = "sub408.code = '408'";

    @Test
    void searchAndDirectLookupsRequireFullTimeAnd408()
    {
        assertApplicantVisible("ProgramSearchMapper", "searchByKeyword",
            Map.of("keyword", "非全日制", "limit", 20, "offset", 0));
        assertApplicantVisible("ProgramSearchMapper", "countByKeyword", Map.of("keyword", "非全日制"));
        assertApplicantVisible("RecommendationMapper", "selectCatalogProgram", Map.of("programId", 2939L));
        assertApplicantVisible("RecommendationMapper", "selectProgramForRecommendation", Map.of("programId", 2939L));
    }

    @Test
    void recommendationAndRagSelectorsRequireFullTimeAnd408()
    {
        assertApplicantVisible("RecommendationMapper", "selectCandidates",
            Map.of("subjectCodes", "101,201,301,408", "estimatedScore", 300));
        assertApplicantVisible("RecommendationMapper", "selectProgramsByIds",
            Map.of("ids", java.util.List.of(2939L), "estimatedScore", 300));
        assertApplicantVisible("RecommendationMapper", "selectApplicantProgramIds",
            Map.of("ids", java.util.List.of(2939L)));
        assertApplicantVisible("RecommendationMapper", "selectForAnalysis",
            Map.of("estimatedScore", 300, "minScore", 250, "maxScore", 350));
        assertApplicantVisible("RecommendationMapper", "selectForAgentPool",
            Map.of("estimatedScore", 300, "minScore", 250, "maxScore", 350, "limit", 50));
        assertApplicantVisible("RagKnowledgeExportMapper", "selectProgramYearFacts",
            Map.of("year", 2025, "limit", 50));
        assertApplicantVisible("RagKnowledgeExportMapper", "selectProgramHistoryFacts",
            Map.of("startYear", 2023, "endYear", 2025, "limit", 50));
        assertApplicantVisible("AiDatabaseToolMapper", "querySchools",
            Map.of("keyword", "非全日制", "limit", 20));
    }

    @Test
    void shortlistReadsAndWritesRequireFullTimeAnd408()
    {
        Map<String, Long> ids = Map.of("userId", 1L, "programId", 2939L);
        assertApplicantVisible("UserFavoriteProgramMapper", "selectFavoriteListByUserId", ids);
        assertApplicantVisible("UserFavoriteProgramMapper", "selectCountByUserIdAndProgramId", ids);

        UserFavoriteProgram favorite = new UserFavoriteProgram();
        favorite.setUserId(1L);
        favorite.setProgramId(2939L);
        assertApplicantVisible("UserFavoriteProgramMapper", "insertUserFavoriteProgram", favorite);
    }

    @Test
    void adminCatalogStatementsKeepPartTimeAndNon408Programs()
    {
        assertAdminUnfiltered("ProgramMapper", "selectProgramList", new Program());
        assertAdminUnfiltered("WorkspaceMapper", "selectWorkspaceStats", Map.of("year", 2025));
        assertAdminUnfiltered("WorkspaceMapper", "selectSchoolPrograms",
            Map.of("schoolId", 1L, "year", 2025));
        assertAdminUnfiltered("SchoolMapper", "selectSchoolOverviewPrograms", Map.of("id", 1L));
    }

    private void assertApplicantVisible(String mapper, String statement, Object parameters)
    {
        String sql = boundSql(mapper, statement, parameters);
        assertTrue(sql.contains(FULL_TIME), mapper + "." + statement + " must require full-time:\n" + sql);
        assertTrue(sql.contains(SUBJECT_408), mapper + "." + statement + " must require subject 408:\n" + sql);
    }

    private void assertAdminUnfiltered(String mapper, String statement, Object parameters)
    {
        String sql = boundSql(mapper, statement, parameters);
        assertFalse(sql.contains(FULL_TIME), mapper + "." + statement + " must keep part-time rows:\n" + sql);
        assertFalse(sql.contains(SUBJECT_408), mapper + "." + statement + " must keep non-408 rows:\n" + sql);
    }

    private String boundSql(String mapper, String statement, Object parameters)
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(UserFavoriteProgram.class);
        configuration.getTypeAliasRegistry().registerAlias(Program.class);
        configuration.getTypeAliasRegistry().registerAlias(com.ruoyi.postgrad.domain.School.class);
        // These mappers reuse the ApplicantVisibleProgram fragment from RecommendationMapper.
        parse(configuration, "RecommendationMapper");
        if (!"RecommendationMapper".equals(mapper))
        {
            parse(configuration, mapper);
        }
        return configuration.getMappedStatement("com.ruoyi.postgrad.mapper." + mapper + "." + statement)
            .getBoundSql(parameters).getSql();
    }

    private void parse(Configuration configuration, String mapper)
    {
        String resource = "mapper/postgrad/" + mapper + ".xml";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource))
        {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        catch (java.io.IOException e)
        {
            throw new AssertionError(e);
        }
    }
}
