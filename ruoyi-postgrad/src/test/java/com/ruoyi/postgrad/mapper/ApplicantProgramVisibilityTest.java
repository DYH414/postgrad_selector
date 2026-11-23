package com.ruoyi.postgrad.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ruoyi.postgrad.domain.UserFavoriteProgram;
import java.io.InputStream;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ApplicantProgramVisibilityTest
{
    @Test
    void searchAndDirectLookupsRequireFullTime()
    {
        assertFullTime("ProgramSearchMapper", "searchByKeyword", Map.of("keyword", "非全日制", "limit", 20, "offset", 0));
        assertFullTime("ProgramSearchMapper", "countByKeyword", Map.of("keyword", "非全日制"));
        assertFullTime("RecommendationMapper", "selectCatalogProgram", Map.of("programId", 2939L));
    }

    @Test
    void shortlistReadsAndWritesRequireFullTime()
    {
        Map<String, Long> ids = Map.of("userId", 1L, "programId", 2939L);
        assertFullTime("UserFavoriteProgramMapper", "selectFavoriteListByUserId", ids);
        assertFullTime("UserFavoriteProgramMapper", "selectCountByUserIdAndProgramId", ids);

        UserFavoriteProgram favorite = new UserFavoriteProgram();
        favorite.setUserId(1L);
        favorite.setProgramId(2939L);
        assertFullTime("UserFavoriteProgramMapper", "insertUserFavoriteProgram", favorite);
    }

    private void assertFullTime(String mapper, String statement, Object parameters)
    {
        Configuration configuration = new Configuration();
        configuration.getTypeAliasRegistry().registerAlias(UserFavoriteProgram.class);
        // ProgramSearchMapper reuses SQL fragments from RecommendationMapper.
        if ("ProgramSearchMapper".equals(mapper))
        {
            parse(configuration, "RecommendationMapper");
        }
        parse(configuration, mapper);
        String sql = configuration.getMappedStatement("com.ruoyi.postgrad.mapper." + mapper + "." + statement)
            .getBoundSql(parameters).getSql();
        assertTrue(sql.contains("p.study_mode = 'full_time'"), sql);
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
