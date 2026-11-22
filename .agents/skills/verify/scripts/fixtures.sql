INSERT INTO school (id, name, province, city)
VALUES (900001, '验证大学', '验证省', '验证市');

INSERT INTO college (id, school_id, name)
VALUES (900001, 900001, '验证计算机学院');

INSERT INTO program (id, college_id, program_code, program_name, study_mode, degree_type, is_408)
VALUES (900001, 900001, '081200', '验证计算机科学', 'full_time', 'academic', 1),
       (900002, 900001, '085404', '验证计算机技术', 'full_time', 'professional', 1);
