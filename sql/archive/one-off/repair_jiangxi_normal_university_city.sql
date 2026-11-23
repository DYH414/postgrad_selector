-- 江西师范大学学校简介：https://www.jxnu.edu.cn/43/list.htm
UPDATE school
SET city = '南昌'
WHERE id = 691
  AND name = '江西师范大学'
  AND province = '江西'
  AND BINARY city = '';
