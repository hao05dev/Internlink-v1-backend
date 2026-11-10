-- Mô hình tổ chức đúng thực tế: Trường CNTT-TT (cấp Trường) -> các Khoa trực thuộc.
-- Trước đây bảng departments chỉ có 1 bản ghi "Trường CNTT-TT" nên "Quản lý Khoa" thực chất là cả Trường.
--
--  * departments          = Khoa (CNPM, CNTT, HTTT, KHMT, MMT&TT, TTĐPT). Ngành thuộc Khoa.
--  * internship_terms     = Kỳ thực tập CẤP TRƯỜNG do Văn phòng Trường tạo (bỏ department_id).
--                            Khoa của một hồ sơ = khoa của sinh viên (ngành -> khoa).
--  * job_positions        = Tin tuyển dụng cấp Trường, mở cho SV mọi khoa (bỏ department_id).
--  * learning_agreements  = giữ department_id = khoa của sinh viên.

-- 1. Khoa trực thuộc Trường CNTT-TT
UPDATE departments
SET code = 'CNPM', name = 'Khoa Công nghệ phần mềm', contact_email = 'kcnpm@cit.ctu.edu.vn'
WHERE id = '11111111-1111-1111-1111-111111111111' AND code = 'CICT';

INSERT INTO departments (id, code, name, contact_email, is_active) VALUES
    ('11111111-1111-1111-1111-111111111112', 'CNTT', 'Khoa Công nghệ thông tin', 'kcntt@cit.ctu.edu.vn', TRUE),
    ('11111111-1111-1111-1111-111111111113', 'HTTT', 'Khoa Hệ thống thông tin', 'khttt@cit.ctu.edu.vn', TRUE),
    ('11111111-1111-1111-1111-111111111114', 'KHMT', 'Khoa Khoa học máy tính', 'kkhmt@cit.ctu.edu.vn', TRUE),
    ('11111111-1111-1111-1111-111111111115', 'MMT', 'Khoa Mạng máy tính và Truyền thông', 'kmmt@cit.ctu.edu.vn', TRUE),
    ('11111111-1111-1111-1111-111111111116', 'TTDPT', 'Khoa Truyền thông đa phương tiện', 'kttdpt@cit.ctu.edu.vn', TRUE)
ON CONFLICT (code) DO NOTHING;

-- 2. Ngành -> Khoa quản lý ngành
INSERT INTO academic_programs (id, department_id, code, name, degree_level, track, is_active)
SELECT v.id::uuid, d.id, v.code, v.name, 'UNDERGRADUATE', 'REGULAR', TRUE
FROM (VALUES
    ('22222222-2222-2222-2222-222222222225', 'CNTT', 'IT', 'Công nghệ thông tin'),
    ('22222222-2222-2222-2222-222222222226', 'HTTT', 'IS', 'Hệ thống thông tin'),
    ('22222222-2222-2222-2222-222222222227', 'MMT', 'IA', 'An toàn thông tin'),
    ('22222222-2222-2222-2222-222222222228', 'TTDPT', 'MC', 'Truyền thông đa phương tiện')
) AS v(id, dept_code, code, name)
JOIN departments d ON d.code = v.dept_code
ON CONFLICT (code) DO NOTHING;

UPDATE academic_programs SET department_id = (SELECT id FROM departments WHERE code = 'KHMT') WHERE code = 'CS';
UPDATE academic_programs SET department_id = (SELECT id FROM departments WHERE code = 'MMT') WHERE code = 'NE';

-- 3. Sinh viên thuộc khoa quản lý ngành của mình
UPDATE users u SET department_id = ap.department_id
FROM student_profiles sp JOIN academic_programs ap ON ap.id = sp.program_id
WHERE sp.user_id = u.id AND u.role = 'STUDENT';

UPDATE users u SET department_id = x.department_id
FROM (
    SELECT DISTINCT ON (r.claimed_user_id) r.claimed_user_id AS user_id, ap.department_id
    FROM student_rosters r JOIN academic_programs ap ON ap.id = r.program_id
    WHERE r.claimed_user_id IS NOT NULL
    ORDER BY r.claimed_user_id, r.created_at DESC
) x
WHERE x.user_id = u.id AND u.role = 'STUDENT'
  AND NOT EXISTS (SELECT 1 FROM student_profiles sp WHERE sp.user_id = u.id AND sp.program_id IS NOT NULL);

-- 4. GVHD thuộc khoa có nhiều SV mình hướng dẫn nhất (dữ liệu demo cũ dồn hết vào 1 khoa)
UPDATE users u SET department_id = x.department_id
FROM (
    SELECT DISTINCT ON (lecturer_id) lecturer_id, department_id
    FROM (
        SELECT r.assigned_lecturer_id AS lecturer_id, ap.department_id, COUNT(*) AS n
        FROM student_rosters r JOIN academic_programs ap ON ap.id = r.program_id
        WHERE r.assigned_lecturer_id IS NOT NULL
        GROUP BY r.assigned_lecturer_id, ap.department_id
    ) c
    ORDER BY lecturer_id, n DESC
) x
WHERE x.lecturer_id = u.id AND u.role = 'LECTURER';

-- 5. Mỗi Khoa một tài khoản Quản lý Khoa (demo, cùng mật khẩu với tài khoản QLTT seed)
UPDATE users SET full_name = 'Quản lý Khoa Công nghệ phần mềm'
WHERE email = 'qltt.cict@ctu.edu.vn' AND role = 'FACULTY_ADMIN';

INSERT INTO users (id, department_id, company_id, email, password_hash, full_name, phone_number, role, must_change_password, is_active)
SELECT gen_random_uuid(), d.id, NULL, 'qltt.' || lower(d.code) || '@ctu.edu.vn', seed.password_hash,
       'Quản lý ' || d.name, NULL, 'FACULTY_ADMIN', FALSE, TRUE
FROM departments d
CROSS JOIN (SELECT password_hash FROM users WHERE email = 'qltt.cict@ctu.edu.vn') seed
WHERE d.code IN ('CNTT', 'HTTT', 'KHMT', 'MMT', 'TTDPT')
ON CONFLICT (email) DO NOTHING;

-- 6. Thỏa thuận học tập: khoa = khoa của sinh viên
UPDATE learning_agreements la SET department_id = u.department_id
FROM users u WHERE u.id = la.student_id AND u.department_id IS NOT NULL;

-- 7. Kỳ thực tập cấp Trường, tin tuyển dụng cấp Trường
ALTER TABLE internship_terms DROP COLUMN department_id;
ALTER TABLE job_positions DROP COLUMN department_id;

-- 8. Liên kết thông báo của Khoa về trang kỳ thực tập chuyển sang VP Trường
UPDATE notifications SET action_url = REPLACE(action_url, '/faculty/terms', '/school-office/terms')
WHERE action_url LIKE '/faculty/terms%';
