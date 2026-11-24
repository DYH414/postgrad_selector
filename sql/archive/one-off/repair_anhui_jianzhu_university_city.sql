-- 安徽建筑大学学校简介：https://www.ahjzu.edu.cn/14647/list.htm
UPDATE school
SET city = '合肥'
WHERE id = 526
  AND name = '安徽建筑大学'
  AND province = '安徽'
  AND BINARY city = '安徽';
