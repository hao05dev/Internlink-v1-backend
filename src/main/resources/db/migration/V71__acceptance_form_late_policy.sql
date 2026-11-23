-- Hạn nộp Phiếu tiếp nhận mặc định "mềm": quá hạn vẫn nộp được nhưng phiếu bị đánh dấu nộp trễ.
-- VP Trường có thể chọn LOCK để khóa nộp sau hạn (chỉ có ý nghĩa với mốc ACCEPTANCE_FORM_DUE).
ALTER TABLE term_milestones
    ADD COLUMN late_policy VARCHAR(20) NOT NULL DEFAULT 'ALLOW_LATE'
        CHECK (late_policy IN ('ALLOW_LATE', 'LOCK'));

-- Lần nộp đầu tiên của SV; nộp lại sau khi Khoa yêu cầu bổ sung không đổi hai cột này.
ALTER TABLE student_found_applications
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN submitted_late BOOLEAN NOT NULL DEFAULT false;

-- Phiếu đã nộp trước khi có cột: lấy thời điểm cập nhật gần nhất làm mốc nộp (không suy ra trễ).
UPDATE student_found_applications SET submitted_at = updated_at
WHERE status <> 'DRAFT' AND submitted_at IS NULL;
