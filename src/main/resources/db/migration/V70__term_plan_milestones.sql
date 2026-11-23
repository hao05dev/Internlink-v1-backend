-- Kế hoạch TTTT của kỳ do Văn phòng Trường ban hành (vd. CV số 349/CNTT&TT-GV).
-- Mỗi dòng là một mốc trong bảng "Nội dung / Thời gian"; mốc có thể chỉ có "Từ", chỉ có "Đến" hoặc cả khoảng.
ALTER TABLE internship_terms
    ADD COLUMN plan_document_ref VARCHAR(100),
    ADD COLUMN plan_published_at TIMESTAMPTZ,
    ADD COLUMN plan_published_by_user_id UUID REFERENCES users(id);

CREATE TABLE term_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    term_id UUID NOT NULL REFERENCES internship_terms(id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL CHECK (kind IN (
        'INTRO_LETTER_PICKUP', 'PROCESS_BRIEFING', 'ACCEPTANCE_FORM_DUE', 'FACULTY_REVIEW_DUE',
        'LIST_PUBLISH', 'INTERNSHIP_PERIOD', 'REPORT_SUBMISSION', 'GRADING', 'OTHER')),
    label VARCHAR(500) NOT NULL,
    note VARCHAR(1000),
    start_at TIMESTAMPTZ,
    end_at TIMESTAMPTZ,
    -- 8 = học phần 2–3 TC, 12 = học phần 5 TC; NULL = áp dụng cho mọi SV.
    required_weeks SMALLINT CHECK (required_weeks IN (8, 12)),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INTEGER NOT NULL DEFAULT 0,
    CHECK (start_at IS NOT NULL OR end_at IS NOT NULL),
    CHECK (start_at IS NULL OR end_at IS NULL OR start_at <= end_at)
);

CREATE INDEX idx_term_milestones_term ON term_milestones(term_id, sort_order);
CREATE INDEX idx_term_milestones_kind ON term_milestones(kind, end_at);
