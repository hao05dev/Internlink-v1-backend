-- V43__saved_jobs.sql
-- Sinh viên lưu tin tuyển dụng yêu thích để xem lại sau

CREATE TABLE IF NOT EXISTS saved_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES job_positions(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_saved_jobs_student_job UNIQUE (student_id, job_id)
);

CREATE INDEX IF NOT EXISTS idx_saved_jobs_student_created
    ON saved_jobs (student_id, created_at DESC);
