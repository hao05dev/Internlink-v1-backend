-- Tài khoản demo cho luồng Phiếu tiếp nhận (mật khẩu chung: Password@123).

-- 1. V23/V13 seed sau V9 nên vẫn mang hash cũ không khớp Password@123.
UPDATE users
SET password_hash = '$2a$10$yn83n6F7678fdKd/Zdf4y.rcBqBFz3Yld9TTebnGizDaV9a9Al8V.'
WHERE password_hash = '$2a$10$V04qPky6yVn5nff2zFkWveXlJ/z46L.G9v72xT74z7rK9n0W1WzK6';

-- 2. Tài khoản Văn phòng Trường mang email đúng vai trò.
UPDATE users
SET email = 'vanphong.cict@ctu.edu.vn', full_name = 'Văn phòng Trường CNTT-TT'
WHERE email = 'editor.blog@ctu.edu.vn'
  AND NOT EXISTS (SELECT 1 FROM users WHERE email = 'vanphong.cict@ctu.edu.vn');

-- 3. Học phần thực tập 3 TC (8 tuần) cho SV demo cũ chưa có số tín chỉ.
UPDATE student_rosters
SET internship_credits = 3
WHERE term_id = '55555555-5555-5555-5555-555555555555' AND internship_credits IS NULL;

-- 4. Kỳ demo riêng đang mở ứng tuyển (SV tự tìm nơi chỉ nộp phiếu được khi kỳ mở đăng ký/ứng tuyển),
--    để demo không phụ thuộc kỳ thật đã đóng hay đang chạy.
INSERT INTO internship_terms (id, department_id, code, term_name, academic_year, semester, registration_open_at, registration_close_at,
                              start_date, end_date, application_deadline, evaluation_deadline, status, settings)
VALUES (
    '55555555-5555-5555-5555-000000000049', '11111111-1111-1111-1111-111111111111',
    'HE_2026_2027_DEMO', 'Học kỳ Hè 2026-2027 (Demo TTTT)', '2026-2027', 'HE',
    NOW() - INTERVAL '7 days', NOW() + INTERVAL '30 days',
    CURRENT_DATE + INTERVAL '14 days', CURRENT_DATE + INTERVAL '104 days',
    NOW() + INTERVAL '30 days', CURRENT_DATE + INTERVAL '114 days',
    'APPLICATION_OPEN',
    '{"requiredHours": 320, "weeklyLogFrequency": "WEEKLY", "appealWindowDays": 7, "mentorWeight": 0.6, "lecturerWeight": 0.4}'::jsonb
)
ON CONFLICT (code) DO NOTHING;

-- 5. Bốn SV demo trong kỳ demo, mỗi SV dừng ở một bước của Phiếu tiếp nhận.
INSERT INTO users (id, department_id, company_id, email, password_hash, full_name, phone_number, role, must_change_password, is_active)
VALUES
    ('10000000-0000-0000-0000-000000000051', '11111111-1111-1111-1111-111111111111', NULL, 'b2110951@student.ctu.edu.vn',
     '$2a$10$yn83n6F7678fdKd/Zdf4y.rcBqBFz3Yld9TTebnGizDaV9a9Al8V.', 'Trần Minh Khoa', '0901000051', 'STUDENT', FALSE, TRUE),
    ('10000000-0000-0000-0000-000000000052', '11111111-1111-1111-1111-111111111111', NULL, 'b2110952@student.ctu.edu.vn',
     '$2a$10$yn83n6F7678fdKd/Zdf4y.rcBqBFz3Yld9TTebnGizDaV9a9Al8V.', 'Phạm Thu Hà', '0901000052', 'STUDENT', FALSE, TRUE),
    ('10000000-0000-0000-0000-000000000053', '11111111-1111-1111-1111-111111111111', NULL, 'b2110953@student.ctu.edu.vn',
     '$2a$10$yn83n6F7678fdKd/Zdf4y.rcBqBFz3Yld9TTebnGizDaV9a9Al8V.', 'Nguyễn Quốc Bảo', '0901000053', 'STUDENT', FALSE, TRUE),
    ('10000000-0000-0000-0000-000000000054', '11111111-1111-1111-1111-111111111111', NULL, 'b2110954@student.ctu.edu.vn',
     '$2a$10$yn83n6F7678fdKd/Zdf4y.rcBqBFz3Yld9TTebnGizDaV9a9Al8V.', 'Lê Ngọc Trâm', '0901000054', 'STUDENT', FALSE, TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO student_profiles (user_id, program_id, student_code, gpa)
SELECT u.id, '22222222-2222-2222-2222-222222222222', v.code, v.gpa
FROM (VALUES
    ('b2110951@student.ctu.edu.vn', 'B2110951', 3.25),
    ('b2110952@student.ctu.edu.vn', 'B2110952', 3.40),
    ('b2110953@student.ctu.edu.vn', 'B2110953', 3.10),
    ('b2110954@student.ctu.edu.vn', 'B2110954', 3.55)
) AS v(email, code, gpa)
JOIN users u ON u.email = v.email
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO student_rosters (id, term_id, program_id, student_code, official_email, full_name, academic_year, class_code,
                             internship_course_code, internship_credits, claimed_user_id, claimed_at,
                             assigned_lecturer_id, lecturer_name, lecturer_email, letter_received, letter_received_at)
SELECT v.id::uuid, '55555555-5555-5555-5555-000000000049', '22222222-2222-2222-2222-222222222222', v.code, u.email, u.full_name,
       'Khóa 47', 'DI2196A2', 'CT471', 3, u.id, NOW(),
       l.id, l.full_name, l.email, TRUE, NOW()
FROM (VALUES
    ('20000000-0000-0000-0000-000000000051', 'b2110951@student.ctu.edu.vn', 'B2110951'),
    ('20000000-0000-0000-0000-000000000052', 'b2110952@student.ctu.edu.vn', 'B2110952'),
    ('20000000-0000-0000-0000-000000000053', 'b2110953@student.ctu.edu.vn', 'B2110953'),
    ('20000000-0000-0000-0000-000000000054', 'b2110954@student.ctu.edu.vn', 'B2110954')
) AS v(id, email, code)
JOIN users u ON u.email = v.email
LEFT JOIN users l ON l.email = 'gvhd.son@ctu.edu.vn'
WHERE NOT EXISTS (
    SELECT 1 FROM student_rosters r
    WHERE r.term_id = '55555555-5555-5555-5555-000000000049' AND (r.claimed_user_id = u.id OR r.student_code = v.code)
);

-- Phiếu: b2110952 đã nộp Form (chờ VP Trường nhận bản giấy), b2110953 VP đã nhận (chờ Khoa duyệt),
-- b2110954 Khoa đã duyệt (chờ công bố). b2110951 chưa có phiếu để demo SV nộp.
INSERT INTO student_found_applications (id, term_id, student_id, host_name, host_address, contact_name, contact_email,
                                        work_description, start_date, end_date, status, source,
                                        received_at, received_by_user_id, reviewed_at, reviewed_by_user_id, created_at, updated_at)
SELECT v.id::uuid, t.id, u.id, v.host, v.address, v.contact, v.contact_email, v.work,
       t.start_date, t.start_date + 56, v.status, 'SELF_FOUND',
       CASE WHEN v.status IN ('RECEIVED', 'APPROVED') THEN NOW() END,
       CASE WHEN v.status IN ('RECEIVED', 'APPROVED') THEN office.id END,
       CASE WHEN v.status = 'APPROVED' THEN NOW() END,
       CASE WHEN v.status = 'APPROVED' THEN faculty.id END,
       NOW(), NOW()
FROM (VALUES
    ('70000000-0000-0000-0000-000000000052', 'b2110952@student.ctu.edu.vn', 'SUBMITTED',
     'Công ty TNHH Phần mềm Mekong Soft', '12 Đường 3/2, Ninh Kiều, Cần Thơ', 'Nguyễn Văn Hùng', 'hr@mekongsoft.vn',
     'Phát triển giao diện web với React, viết kiểm thử tự động.'),
    ('70000000-0000-0000-0000-000000000053', 'b2110953@student.ctu.edu.vn', 'RECEIVED',
     'VNPT Cần Thơ', '11 Phan Đình Phùng, Ninh Kiều, Cần Thơ', 'Trần Thị Mai', 'tuyendung@vnpt-cantho.vn',
     'Hỗ trợ vận hành hệ thống mạng và xây dựng công cụ giám sát.'),
    ('70000000-0000-0000-0000-000000000054', 'b2110954@student.ctu.edu.vn', 'APPROVED',
     'Công ty CP Công nghệ TMA Cần Thơ', 'Khu Công nghệ Phần mềm ĐHCT, Cần Thơ', 'Lê Quốc Việt', 'intern@tma.com.vn',
     'Phát triển API Spring Boot cho hệ thống quản lý nội bộ.')
) AS v(id, email, status, host, address, contact, contact_email, work)
JOIN users u ON u.email = v.email
JOIN internship_terms t ON t.id = '55555555-5555-5555-5555-000000000049'
LEFT JOIN users office ON office.email = 'vanphong.cict@ctu.edu.vn'
LEFT JOIN users faculty ON faculty.email = 'qltt.cict@ctu.edu.vn'
ON CONFLICT DO NOTHING;

-- Khoa duyệt phiếu thì tạo lần thực tập (chờ bắt đầu) gắn với phiếu, như AcceptanceFormReviewService.
INSERT INTO internship_placements (id, student_id, lecturer_id, term_id, work_schedule, start_date, end_date,
                                   total_hours_worked, status, source, student_found_application_id)
SELECT '66666666-6666-6666-6666-000000000054', a.student_id, l.id, a.term_id, '{}'::jsonb, a.start_date, a.end_date,
       0, 'PREPARING', 'STUDENT_FOUND', a.id
FROM student_found_applications a
LEFT JOIN users l ON l.email = 'gvhd.son@ctu.edu.vn'
WHERE a.id = '70000000-0000-0000-0000-000000000054'
  AND NOT EXISTS (SELECT 1 FROM internship_placements p WHERE p.term_id = a.term_id AND p.student_id = a.student_id)
ON CONFLICT DO NOTHING;
