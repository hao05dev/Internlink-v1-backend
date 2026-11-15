-- Cuộc họp trực tuyến (Google Meet). Chế độ MANUAL: người tổ chức dán link Meet có sẵn.
-- GOOGLE_OAUTH / GOOGLE_WORKSPACE dành cho giai đoạn tự tạo link qua Calendar API.
CREATE TABLE IF NOT EXISTS meetings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organizer_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    context_type VARCHAR(20) NOT NULL DEFAULT 'SUPERVISION'
        CHECK (context_type IN ('SUPERVISION', 'INTERVIEW')),
    context_id UUID,
    title VARCHAR(200) NOT NULL,
    agenda TEXT,
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    meet_url VARCHAR(255) NOT NULL,
    link_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (link_mode IN ('MANUAL', 'GOOGLE_OAUTH', 'GOOGLE_WORKSPACE')),
    external_event_id VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
        CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED')),
    cancel_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_meetings_time CHECK (end_at > start_at)
);

CREATE INDEX IF NOT EXISTS idx_meetings_organizer_start ON meetings(organizer_id, start_at);
CREATE INDEX IF NOT EXISTS idx_meetings_scheduled_start ON meetings(start_at) WHERE status = 'SCHEDULED';

CREATE TABLE IF NOT EXISTS meeting_attendees (
    meeting_id UUID NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attendance VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (attendance IN ('UNKNOWN', 'PRESENT', 'ABSENT')),
    notified_at TIMESTAMPTZ,
    PRIMARY KEY (meeting_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_meeting_attendees_user ON meeting_attendees(user_id);
