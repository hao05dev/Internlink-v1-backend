-- Văn phòng Trường CNTT-TT (SCHOOL_OFFICE) thay cho BLOG_MANAGER.
-- Trường: đăng bài/tin tuyển dụng, duyệt doanh nghiệp, giấy giới thiệu, tiếp nhận phiếu, phát giấy tờ.
-- Khoa (FACULTY_ADMIN): học kỳ, danh sách SV, tài khoản + phân công GVHD, duyệt phiếu, công bố danh sách.

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;

UPDATE users
SET role = 'SCHOOL_OFFICE',
    department_id = NULL,
    full_name = 'Văn phòng Trường CNTT-TT'
WHERE role = 'BLOG_MANAGER';

ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('STUDENT', 'COMPANY_REP', 'COMPANY_MENTOR', 'LECTURER', 'FACULTY_ADMIN', 'ADMIN', 'SCHOOL_OFFICE'));

UPDATE notifications
SET action_url = REPLACE(action_url, '/blog-manager/', '/school-office/')
WHERE action_url LIKE '/blog-manager/%';

UPDATE notifications
SET action_url = REPLACE(action_url, '/faculty/', '/school-office/')
WHERE action_url LIKE '/faculty/job-approvals%'
   OR action_url LIKE '/faculty/companies%'
   OR action_url LIKE '/faculty/introduction-letters%';
