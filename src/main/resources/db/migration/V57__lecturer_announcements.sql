-- Thông báo GVHD gửi cho sinh viên mình hướng dẫn (in-app + email tùy chọn).
CREATE TABLE IF NOT EXISTS lecturer_announcements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    send_email BOOLEAN NOT NULL DEFAULT FALSE,
    recipient_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_lecturer_announcements_sender
    ON lecturer_announcements(sender_id, created_at DESC);

CREATE TABLE IF NOT EXISTS lecturer_announcement_recipients (
    announcement_id UUID NOT NULL REFERENCES lecturer_announcements(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (announcement_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_lecturer_announcement_recipients_user
    ON lecturer_announcement_recipients(user_id);
