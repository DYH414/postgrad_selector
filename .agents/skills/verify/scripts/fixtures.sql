INSERT INTO school (id, name, province, city, tier, is_985, is_211, is_double_first)
VALUES (900001, '验证大学', '验证省', '验证市', '985', 1, 1, 1);

INSERT INTO college (id, school_id, name)
VALUES (900001, 900001, '验证计算机学院');

INSERT INTO program (id, college_id, program_code, program_name, study_mode, degree_type, is_408)
VALUES (900001, 900001, '081200', '验证计算机科学', 'full_time', 'academic', 1),
       (900002, 900001, '085404', '验证计算机技术', 'full_time', 'professional', 1);

INSERT INTO program_subject (program_id, subject_id, subject_order)
SELECT program.id, subject.id, FIELD(subject.code, '101', '204', '302', '408')
FROM program CROSS JOIN subject
WHERE program.id IN (900001, 900002) AND subject.code IN ('101', '204', '302', '408');

INSERT INTO admission_score (program_id, year, score_line, verify_status)
VALUES (900001, 2025, 310, 'MANUAL_VERIFIED'),
       (900002, 2025, 315, 'MANUAL_VERIFIED');

INSERT INTO admission_result
  (program_id, year, admitted_count, min_admitted_score, avg_admitted_score, max_admitted_score, verify_status)
VALUES (900001, 2025, 35, 315, 326, 345, 'MANUAL_VERIFIED'),
       (900002, 2025, 42, 320, 333, 355, 'MANUAL_VERIFIED');
