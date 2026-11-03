-- The roster now owns the supervising lecturer (GVHD) and live placements follow it.
-- Carry over lecturers that were only recorded on placements.
UPDATE student_rosters r
SET assigned_lecturer_id = p.lecturer_id
FROM internship_placements p
WHERE p.term_id = r.term_id
  AND p.student_id = r.claimed_user_id
  AND p.lecturer_id IS NOT NULL
  AND p.status NOT IN ('TERMINATED', 'TRANSFERRED')
  AND r.assigned_lecturer_id IS DISTINCT FROM p.lecturer_id;

-- Link imported rows that named a lecturer by email who already has an account in the same faculty.
UPDATE student_rosters r
SET assigned_lecturer_id = u.id
FROM users u, internship_terms t
WHERE r.assigned_lecturer_id IS NULL
  AND r.lecturer_email IS NOT NULL
  AND LOWER(u.email) = LOWER(r.lecturer_email)
  AND u.role = 'LECTURER'
  AND t.id = r.term_id
  AND u.department_id = t.department_id;
