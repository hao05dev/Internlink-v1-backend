-- GVHD duyệt từng file trong hồ sơ nộp Khoa (Mẫu 04, M01, M02, M03).
-- M05 dùng luôn kết quả duyệt báo cáo cuối kỳ (internship_reports).
ALTER TABLE documents
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN review_note TEXT,
    ADD COLUMN reviewed_by_user_id UUID REFERENCES users(id),
    ADD COLUMN reviewed_at TIMESTAMPTZ;

ALTER TABLE documents
    ADD CONSTRAINT chk_documents_review_status
    CHECK (review_status IN ('PENDING', 'APPROVED', 'REVISION_REQUIRED'));

-- File nộp trước khi có bước duyệt coi như đã đạt để các đợt đang chạy không bị kẹt.
UPDATE documents SET review_status = 'APPROVED', reviewed_at = now();
