-- Mỗi lần GVHD chụp Phiếu đánh giá của cơ quan (M-TT-03) để AI đọc điểm.
-- Giữ cả bản AI đọc lẫn bản GVHD chốt để đo độ chính xác mà không cần gán nhãn tay.
CREATE TABLE paper_evaluation_scans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    placement_id UUID NOT NULL REFERENCES internship_placements(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES documents(id),
    scanned_by_user_id UUID NOT NULL REFERENCES users(id),
    form_code VARCHAR(20) NOT NULL DEFAULT 'M03',
    model VARCHAR(100),
    reading JSONB NOT NULL,
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    suggested_score NUMERIC(4,1),
    confirmed JSONB,
    confirmed_score NUMERIC(4,1),
    edited_fields INTEGER,
    confirmed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_paper_evaluation_scans_placement ON paper_evaluation_scans(placement_id, created_at DESC);
