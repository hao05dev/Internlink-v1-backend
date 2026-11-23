-- Bổ sung các field của Google Form "Thực tập thực tế" vào Phiếu tiếp nhận để SV chỉ nhập một lần.
ALTER TABLE student_found_applications
    ADD COLUMN host_province VARCHAR(100),
    ADD COLUMN host_phone VARCHAR(30),
    ADD COLUMN contact_phone VARCHAR(30),
    ADD COLUMN sessions_per_week SMALLINT,
    ADD COLUMN has_workspace BOOLEAN,
    ADD COLUMN has_computer BOOLEAN,
    ADD COLUMN weekly_plan JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN other_support TEXT;

ALTER TABLE student_found_applications
    ADD CONSTRAINT chk_sfa_sessions_per_week CHECK (sessions_per_week IS NULL OR sessions_per_week BETWEEN 1 AND 14);
