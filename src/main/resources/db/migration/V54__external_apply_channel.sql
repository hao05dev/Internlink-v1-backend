-- Kênh ứng tuyển ngoài hệ thống (docs/de-xuat-tin-truong-dang-ung-tuyen-ngoai.md)
--
--  * job_positions.application_method:
--      IN_APP   = SV nộp đơn trong InternLink, DN đối tác xử lý (luồng ATS hiện có).
--      EXTERNAL = Tin Trường đăng lại cho DN không có tài khoản; SV nộp qua email/link/hướng dẫn của DN.
--    Tin EXTERNAL phải có ít nhất một kênh: link, email hoặc hướng dẫn (vd: nộp trực tiếp tại công ty).
--  * external_apply_clicks: mỗi SV một dòng mỗi tin, đếm số lần bấm "Ứng tuyển qua kênh của DN"
--    để Trường thống kê mức quan tâm.
--  * student_found_applications.source_job_id: Phiếu tiếp nhận SELF_FOUND ghi nhận tin nguồn.

ALTER TABLE job_positions
    ADD COLUMN application_method     VARCHAR(20) NOT NULL DEFAULT 'IN_APP',
    ADD COLUMN external_apply_url     TEXT,
    ADD COLUMN external_apply_email   VARCHAR(255),
    ADD COLUMN external_apply_subject VARCHAR(255),
    ADD COLUMN external_apply_note    TEXT;

ALTER TABLE job_positions ADD CONSTRAINT chk_job_application_method
    CHECK (application_method IN ('IN_APP', 'EXTERNAL'));

ALTER TABLE job_positions ADD CONSTRAINT chk_job_external_channel
    CHECK (application_method = 'IN_APP'
        OR external_apply_url IS NOT NULL
        OR external_apply_email IS NOT NULL
        OR external_apply_note IS NOT NULL);

CREATE TABLE external_apply_clicks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id       UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id           UUID NOT NULL REFERENCES job_positions(id) ON DELETE CASCADE,
    click_count      INT NOT NULL DEFAULT 1,
    last_clicked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_external_apply_clicks_student_job UNIQUE (student_id, job_id)
);
CREATE INDEX idx_external_apply_clicks_job ON external_apply_clicks(job_id);

ALTER TABLE student_found_applications
    ADD COLUMN source_job_id UUID REFERENCES job_positions(id) ON DELETE SET NULL;
ALTER TABLE student_found_applications ADD CONSTRAINT chk_acceptance_form_source_job
    CHECK (source_job_id IS NULL OR source = 'SELF_FOUND');
CREATE INDEX idx_acceptance_form_source_job
    ON student_found_applications(source_job_id) WHERE source_job_id IS NOT NULL;
