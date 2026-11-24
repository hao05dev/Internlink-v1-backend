-- Kỳ thực tập không còn nhập "Bắt đầu đăng ký" / "Hạn chót đăng ký".
-- Việc SV đăng ký học phần TTTT do hệ thống đăng ký học phần của Trường lo (Khoa import roster),
-- còn hạn nộp Phiếu tiếp nhận lấy từ Kế hoạch TTTT (term_milestones, mốc ACCEPTANCE_FORM_DUE).
-- Giữ hai cột (cho phép NULL) để dữ liệu và script seed cũ vẫn chạy; ứng dụng không đọc/ghi nữa.
ALTER TABLE internship_terms ALTER COLUMN registration_open_at DROP NOT NULL;
ALTER TABLE internship_terms ALTER COLUMN registration_close_at DROP NOT NULL;

COMMENT ON COLUMN internship_terms.registration_open_at IS 'Không còn dùng (V73). Mốc thật nằm trong term_milestones.';
COMMENT ON COLUMN internship_terms.registration_close_at IS 'Không còn dùng (V73). Mốc thật nằm trong term_milestones.';
