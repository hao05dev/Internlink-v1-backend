-- Nhật ký hỏi đáp của trợ lý CapyLink, dùng để đánh giá chất lượng và tìm lỗ hổng tri thức.
-- Ẩn danh: KHÔNG lưu ID người hỏi; câu hỏi đã che MSSV, email, số điện thoại trước khi ghi.
CREATE TABLE IF NOT EXISTS assistant_interactions (
    id              UUID PRIMARY KEY,
    audience        VARCHAR(20)  NOT NULL,
    question        VARCHAR(1000) NOT NULL,
    intents         VARCHAR(200) NOT NULL DEFAULT '',
    status          VARCHAR(30)  NOT NULL,
    mode            VARCHAR(20)  NOT NULL DEFAULT 'extractive',
    citation_count  INT          NOT NULL DEFAULT 0,
    latency_ms      INT          NOT NULL DEFAULT 0,
    helpful         BOOLEAN,
    feedback_note   VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    rated_at        TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_assistant_interactions_created ON assistant_interactions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_assistant_interactions_unhelpful ON assistant_interactions (created_at DESC)
    WHERE helpful = FALSE;
