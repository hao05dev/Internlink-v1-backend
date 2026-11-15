-- Tiến độ xem tour hướng dẫn (mascot CapyLink) của từng người dùng.
-- Lưu ở server để đổi máy vẫn nhớ; tăng tour_version khi nội dung tour đổi để tour hiện lại một lần.
CREATE TABLE user_onboarding_progress (
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tour_key     VARCHAR(60) NOT NULL,
    tour_version INT         NOT NULL CHECK (tour_version > 0),
    status       VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED', 'DISMISSED')),
    last_step    INT         CHECK (last_step >= 0),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, tour_key)
);
