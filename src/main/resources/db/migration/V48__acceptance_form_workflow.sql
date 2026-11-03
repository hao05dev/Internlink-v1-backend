-- Phiếu tiếp nhận thực tập theo quy trình TTTT của Trường CNTT-TT:
-- SV nộp Form + bản scan (SUBMITTED) -> VP Trường xác nhận bản giấy (RECEIVED)
-- -> Khoa duyệt phiếu + đơn vị (APPROVED) -> Khoa công bố danh sách (PUBLISHED)
-- -> VP Trường phát giấy tờ (documents_collected_at).
-- Mọi SV đều nộp phiếu: tự tìm (SELF_FOUND) hoặc trúng tuyển qua hệ thống (SYSTEM_OFFER).

ALTER TABLE student_found_applications
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SELF_FOUND',
    ADD COLUMN agreement_id UUID REFERENCES learning_agreements(id) ON DELETE RESTRICT,
    ADD COLUMN received_at TIMESTAMPTZ,
    ADD COLUMN received_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN published_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN documents_collected_at TIMESTAMPTZ,
    ADD COLUMN documents_collected_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE student_found_applications DROP CONSTRAINT IF EXISTS student_found_applications_status_check;
ALTER TABLE student_found_applications ADD CONSTRAINT student_found_applications_status_check
    CHECK (status IN ('DRAFT', 'SUBMITTED', 'RECEIVED', 'REVISION_REQUIRED', 'APPROVED', 'PUBLISHED', 'REJECTED'));

ALTER TABLE student_found_applications ADD CONSTRAINT chk_acceptance_form_source
    CHECK ((source = 'SELF_FOUND' AND agreement_id IS NULL)
        OR (source = 'SYSTEM_OFFER' AND agreement_id IS NOT NULL));

CREATE UNIQUE INDEX uq_acceptance_form_agreement
    ON student_found_applications(agreement_id) WHERE agreement_id IS NOT NULL;
CREATE INDEX idx_acceptance_form_status ON student_found_applications(status);

-- Phiếu đã duyệt trước khi có bước VP Trường: coi như đã tiếp nhận bản giấy lúc duyệt.
UPDATE student_found_applications
SET received_at = reviewed_at
WHERE status = 'APPROVED' AND received_at IS NULL;

-- Số tín chỉ học phần thực tập: 2-3 TC = 8 tuần (TTTT), 5 TC = 12 tuần (Thực tập doanh nghiệp).
ALTER TABLE student_rosters ADD COLUMN internship_credits SMALLINT
    CHECK (internship_credits IS NULL OR internship_credits IN (2, 3, 5));
