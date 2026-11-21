-- Nhật ký không còn bắt buộc: SV chỉ nộp file báo cáo cuối kỳ cho GVHD.
-- required_logbook_weeks = 0 nghĩa là không đòi nhật ký tuần khi tổng hợp điểm.
ALTER TABLE assessment_schemes DROP CONSTRAINT IF EXISTS assessment_schemes_required_logbook_weeks_check;
ALTER TABLE assessment_schemes
    ADD CONSTRAINT assessment_schemes_required_logbook_weeks_check CHECK (required_logbook_weeks >= 0);

UPDATE assessment_schemes SET required_logbook_weeks = 0 WHERE required_logbook_weeks <> 0;
