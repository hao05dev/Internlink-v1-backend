-- Students reach the roster only after registering the internship course, which is
-- where the faculty already checks the requirements, so the roster no longer tracks eligibility.
ALTER TABLE student_rosters
    DROP COLUMN IF EXISTS eligibility_status,
    DROP COLUMN IF EXISTS eligibility_note;

-- Campaign audiences/purposes that were built on eligibility now target the whole roster.
UPDATE notification_campaigns SET audience = 'ALL_STUDENTS' WHERE audience IN ('ELIGIBLE', 'INELIGIBLE');
UPDATE notification_campaigns SET purpose = 'GENERAL' WHERE purpose = 'INELIGIBLE';
UPDATE email_templates SET purpose = 'FACULTY_NOTICE' WHERE purpose = 'INELIGIBLE';
