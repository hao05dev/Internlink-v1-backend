-- Email template được gán cứng trong resources/templates/email (Thymeleaf).
-- Bỏ tính năng tự custom template: xóa bảng template và các cột tham chiếu.

ALTER TABLE email_outbox
    ADD COLUMN html_body TEXT,
    ADD COLUMN template_key VARCHAR(60);

DROP INDEX IF EXISTS idx_email_outbox_template_version;

ALTER TABLE email_outbox DROP COLUMN IF EXISTS template_version_id;
ALTER TABLE notification_campaigns DROP COLUMN IF EXISTS template_version_id;

DROP TABLE IF EXISTS email_template_versions;
DROP TABLE IF EXISTS email_templates;

CREATE INDEX idx_email_outbox_template_key ON email_outbox(template_key);
