ALTER TABLE user_profile
  ADD COLUMN exam_combo VARCHAR(16) NULL COMMENT '考试科目组合' AFTER estimated_score,
  MODIFY COLUMN risk_preference
    ENUM('conservative','balanced','aggressive','safe_first','reach_first') NOT NULL DEFAULT 'balanced' COMMENT '风险偏好',
  MODIFY COLUMN school_tier_preference
    ENUM('must_211_or_better','prefer_211_or_better','no_strict_requirement',
         'developed_region_priority','school_tier_priority','safe_admission_priority','tier_priority')
    NULL COMMENT '院校层次取舍';
