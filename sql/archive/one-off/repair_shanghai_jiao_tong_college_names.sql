-- 2025 电院复试方案区分 033 计算机系与 036 网络空间安全学院。
-- https://www.seiee.sjtu.edu.cn/via/yjspy_zsgz_sszs/11765.html
-- 1080 人是 N诺 2026 年计算机学院的总额，不是这些 2025 年项目的院系名称。
-- https://noobdream.com/schoolinfo/16/
INSERT INTO college (school_id, name)
SELECT source.school_id, '计算机系'
FROM college source
JOIN program p ON p.college_id = source.id
WHERE source.id = 2286
  AND source.school_id = 91
  AND BINARY source.name = '总人数：1080（全日制），其中学硕人数：331，专硕人数：749，其中推免人数：569'
  AND p.id = 2872
  AND BINARY p.program_name = '计算机科学与技术'
  AND NOT EXISTS (
    SELECT 1 FROM college target WHERE target.school_id = 91 AND target.name = '计算机系'
  );

UPDATE program p
JOIN college source ON source.id = p.college_id
JOIN college target ON target.school_id = source.school_id AND target.name = '计算机系'
SET p.college_id = target.id
WHERE source.id = 2286
  AND source.school_id = 91
  AND BINARY source.name = '总人数：1080（全日制），其中学硕人数：331，专硕人数：749，其中推免人数：569'
  AND (p.id, BINARY p.program_name) IN (
    (2872, '计算机科学与技术'),
    (2873, '电子信息-计算机 与大数据技术 （计算机系招生）'),
    (2874, '电子信息-人工智能 （清源研究院）'),
    (2875, '电子信息-计算机 与大数据技术 （四川研究院）'),
    (2876, '电子信息-计算机 与大数据技术 （海南专项）'),
    (2877, '电子信息-计算机 与大数据技术 （苏州智研院）'),
    (2878, '电子信息-计算机系 （安徽高研院）'),
    (2879, '电子信息-计算机系 （贵州联培基地）')
  );

UPDATE college c
SET c.name = '网络空间安全学院'
WHERE c.id = 2286
  AND c.school_id = 91
  AND BINARY c.name = '总人数：1080（全日制），其中学硕人数：331，专硕人数：749，其中推免人数：569'
  AND (SELECT COUNT(*) FROM program p WHERE p.college_id = c.id) = 6
  AND (SELECT COUNT(*) FROM program p WHERE p.college_id = c.id AND p.id BETWEEN 2880 AND 2885) = 6;

-- 085400 电子信息 62 方向由网络安全技术研究院招生，依托网络空间安全学院。
-- https://www.seiee.sjtu.edu.cn/index_news/8800.html
UPDATE program p
JOIN college c ON c.id = p.college_id
SET p.program_name = '电子信息-网络安全技术（网络安全技术研究院招生）'
WHERE p.id = 2881
  AND c.id = 2286
  AND c.school_id = 91
  AND c.name = '网络空间安全学院'
  AND p.program_code = '085400'
  AND BINARY p.program_name = '电子信息-网安方向 （网络安全技术研究院）';

-- N诺 2025 年原表将两条全日制名额分别列在生物医学工程学院和人工智能学院下。
-- 交大 2025 年联培简章使用院系全称「生物医学工程学院（含 Med-X 研究院）」。
-- https://yzb.sjtu.edu.cn/storage/gs/web/yzbcn/article/2024/10/a1dcdc5f49712a24a1bdbffc0ff977b6.pdf
UPDATE college
SET name = '生物医学工程学院（含 Med-X 研究院）'
WHERE id = 2014
  AND school_id = 91
  AND BINARY name = '全日制：65人，学硕22人，专硕43人，其中推免30人';

-- https://soai.sjtu.edu.cn/cn/show/256
UPDATE college
SET name = '人工智能学院'
WHERE id = 2061
  AND school_id = 91
  AND BINARY name = '全日制：40人，学硕15人，专硕25人，其中推免20人';
