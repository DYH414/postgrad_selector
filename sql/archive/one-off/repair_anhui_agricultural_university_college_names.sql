-- 安徽农业大学电子信息 085400 招生院系为信息与人工智能学院。
-- https://yjs.ahau.edu.cn/info/1171/59034.htm
UPDATE college
SET name = '信息与人工智能学院'
WHERE id = 59
  AND school_id = 43
  AND BINARY name = '信息与人工智能学院全日制';

-- 工学院招生计划把全日制列为培养方式，085400 的院系是工学院。
-- https://yjs.ahau.edu.cn/info/1156/41754.htm
UPDATE college
SET name = '工学院'
WHERE id = 217
  AND school_id = 43
  AND BINARY name = '工学院全日制';
