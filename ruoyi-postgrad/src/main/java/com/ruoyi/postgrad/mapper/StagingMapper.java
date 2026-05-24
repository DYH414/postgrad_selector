package com.ruoyi.postgrad.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.postgrad.domain.RowMap;
import com.ruoyi.postgrad.domain.Staging;

public interface StagingMapper
{
    List<RowMap> selectStagingList(Staging staging);
    Long selectStagingCount(Staging staging);
    RowMap selectStagingById(Long id);
    int updateStagingStatus(@Param("id") Long id, @Param("status") String status,
        @Param("reviewNote") String reviewNote, @Param("reviewerId") Long reviewerId);
    long countByStatus(@Param("status") String status);
    int autoApproveDirectory(@Param("reviewNote") String reviewNote, @Param("reviewerId") Long reviewerId);
    int migrateAdmissionScore(@Param("programId") Long programId, @Param("year") int year,
        @Param("scoreLine") int scoreLine, @Param("singlePolitics") Integer singlePolitics,
        @Param("singleEnglish") Integer singleEnglish, @Param("singleMath") Integer singleMath,
        @Param("singleProfessional") Integer singleProfessional, @Param("sourceId") Long sourceId);
    int migrateAdmissionPlan(@Param("programId") Long programId, @Param("year") int year,
        @Param("planCount") int planCount, @Param("retestCount") Integer retestCount,
        @Param("sourceId") Long sourceId);
    int migrateAdmissionResult(@Param("programId") Long programId, @Param("year") int year,
        @Param("admittedCount") Integer admittedCount, @Param("minAdmitted") Integer minAdmitted,
        @Param("avgAdmitted") java.math.BigDecimal avgAdmitted, @Param("maxAdmitted") Integer maxAdmitted,
        @Param("sourceId") Long sourceId);
}
