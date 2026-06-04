alter table user_profile
    add column priority_preference varchar(32) null comment '择校最看重: success_rate/school_tier/developed_region/major_strength' after risk_preference,
    add column school_tier_preference varchar(32) null comment '学校层次倾向: must_211_or_better/prefer_211_or_better/no_strict_requirement' after priority_preference,
    add column region_strategy varchar(32) null comment '地区策略: no_limit/developed_regions/specific_regions/near_home' after school_tier_preference,
    add column data_reliability_preference varchar(32) null comment '数据可靠性偏好: strict/medium/loose' after region_strategy;
