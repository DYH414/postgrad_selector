alter table user_profile
    add column school_tier_preference varchar(32) null comment '院校层次取舍: must_211_or_better/prefer_211_or_better/no_strict_requirement' after risk_preference,
    add column region_strategy varchar(32) null comment '地区取舍: developed_priority/developed_balanced/no_strict_requirement/target_regions_only' after school_tier_preference;
