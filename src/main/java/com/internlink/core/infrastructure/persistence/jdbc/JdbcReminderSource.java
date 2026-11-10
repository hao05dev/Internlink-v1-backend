package com.internlink.core.infrastructure.persistence.jdbc;

import com.internlink.core.application.system.notification.*;

import lombok.RequiredArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class JdbcReminderSource implements ReminderSource {
    private final JdbcTemplate jdbc;
    private final NotificationEventRepository events;
    private static final String SOURCES =
            """
WITH placements AS (
    SELECT p.*,st.department_id,t.evaluation_deadline,t.status AS term_status,
        coalesce(internlink_try_timestamp(p.work_schedule #>> '{extension,reportDueAt}'),s.final_report_due_at,t.evaluation_deadline) AS report_due,
        coalesce(s.weekly_grace_days,0) AS grace_days, s.midterm_report_due_at, coalesce(s.require_midterm_report,false) AS require_midterm_report
    FROM internship_placements p JOIN internship_terms t ON t.id=p.term_id JOIN users st ON st.id=p.student_id
    LEFT JOIN assessment_schemes s ON s.id=p.assessment_scheme_id
    WHERE t.status NOT IN ('DRAFT','CLOSED') AND p.status IN ('PREPARING','ACTIVE','PAUSED','COMPLETED')
), due AS (
    SELECT r.claimed_user_id AS recipient_id,t.id::text AS entity,'APPLICATION_DEADLINE'::text AS kind,
        'Nhắc hoàn thành hồ sơ đăng ký thực tập'::text AS title,'/student/applications'::text AS path,
        CASE WHEN t.status='REGISTRATION_OPEN' THEN t.registration_close_at ELSE t.application_deadline END AS due_at
    FROM student_rosters r JOIN internship_terms t ON t.id=r.term_id
    WHERE r.claimed_user_id IS NOT NULL
        AND t.status IN ('REGISTRATION_OPEN','APPLICATION_OPEN')
        AND NOT EXISTS(SELECT 1 FROM internship_placements p WHERE p.term_id=t.id AND p.student_id=r.claimed_user_id
            AND p.status NOT IN ('TERMINATED','TRANSFERRED'))
        AND NOT EXISTS(SELECT 1 FROM job_applications a JOIN job_positions j ON j.id=a.job_id
            WHERE a.student_id=r.claimed_user_id AND j.term_id=t.id AND a.status NOT IN ('WITHDRAWN','REJECTED'))
        AND NOT EXISTS(SELECT 1 FROM student_found_applications a WHERE a.student_id=r.claimed_user_id AND a.term_id=t.id
            AND a.status NOT IN ('DRAFT','REJECTED'))
    UNION ALL
    SELECT a.student_id,o.id::text,'OFFER_DEADLINE','Lời mời thực tập cần phản hồi','/student/applications',o.expires_at
    FROM placement_offers o JOIN job_applications a ON a.id=o.application_id
    JOIN job_positions j ON j.id=a.job_id JOIN internship_terms t ON t.id=j.term_id
    WHERE o.status IN ('ACCEPTED','DELIVERED') AND t.status NOT IN ('DRAFT','CLOSED')
    UNION ALL
    SELECT g.student_id,g.id::text,'AGREEMENT_DEADLINE','Thỏa thuận cần chữ ký của bạn','/student/learning-agreement',t.application_deadline
    FROM learning_agreements g JOIN placement_offers o ON o.id=g.offer_id
    JOIN job_applications a ON a.id=o.application_id JOIN job_positions j ON j.id=a.job_id
    JOIN internship_terms t ON t.id=j.term_id WHERE g.status IN ('DRAFT','PENDING_SIGNATURES','REVISION_REQUESTED')
        AND g.student_signature IS NULL AND t.status NOT IN ('DRAFT','CLOSED')
    UNION ALL
    SELECT u.id,g.id::text,'AGREEMENT_DEADLINE','Thỏa thuận cần doanh nghiệp xác nhận','/company/interns',t.application_deadline
    FROM learning_agreements g JOIN users u ON u.company_id=g.company_id AND u.role='COMPANY_REP'
    JOIN placement_offers o ON o.id=g.offer_id JOIN job_applications a ON a.id=o.application_id
    JOIN job_positions j ON j.id=a.job_id JOIN internship_terms t ON t.id=j.term_id
    WHERE g.status IN ('DRAFT','PENDING_SIGNATURES') AND g.company_signature IS NULL AND t.status NOT IN ('DRAFT','CLOSED')
    UNION ALL
    SELECT p.student_id,p.id::text||'/'||w.week_start::date::text,'LOGBOOK_DEADLINE','Nhắc tổng hợp nhật ký tuần','/student/daily-logs',
        (least(w.week_start::date+6,p.end_date)+(p.grace_days+1)*INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM placements p CROSS JOIN LATERAL generate_series(p.start_date::timestamp,p.end_date::timestamp,INTERVAL '7 days') w(week_start)
    WHERE p.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM weekly_logbooks l WHERE l.placement_id=p.id
        AND l.period_start=w.week_start::date AND l.status NOT IN ('DRAFT','REVISION_REQUESTED'))
    UNION ALL
    SELECT p.mentor_id,l.id::text,'LOGBOOK_REVIEW_REMINDER','Nhật ký tuần chờ mentor phản hồi','/mentor/weekly-evaluations',
        (l.period_end+(p.grace_days+2)*INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM placements p JOIN weekly_logbooks l ON l.placement_id=p.id WHERE l.status='SUBMITTED' AND p.mentor_id IS NOT NULL
    UNION ALL
    SELECT p.lecturer_id,l.id::text,'LOGBOOK_REVIEW_REMINDER','Nhật ký tuần chờ giảng viên nhận xét','/lecturer/supervision',
        (l.period_end+(p.grace_days+3)*INTERVAL '1 day')::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM placements p JOIN weekly_logbooks l ON l.placement_id=p.id WHERE l.status='APPROVED_BY_MENTOR' AND l.lecturer_commented_at IS NULL
    UNION ALL
    SELECT p.mentor_id,d.id::text,'DAILY_REVIEW_REMINDER','Nhật ký ngày chờ xác nhận','/mentor/internship-record',
        (d.work_date+INTERVAL '2 days')::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM placements p JOIN daily_journals d ON d.placement_id=p.id WHERE d.status='SUBMITTED' AND p.mentor_id IS NOT NULL
    UNION ALL
    SELECT p.lecturer_id,d.id::text,'DAILY_REVIEW_REMINDER','Nhật ký ngày chờ giảng viên xác nhận','/lecturer/supervision',
        (d.work_date+INTERVAL '2 days')::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM placements p JOIN daily_journals d ON d.placement_id=p.id WHERE d.status='SUBMITTED' AND p.mentor_id IS NULL
    UNION ALL
    SELECT p.student_id,p.id::text,'REPORT_DEADLINE','Nhắc nộp báo cáo cuối kỳ','/student/final-report',p.report_due
    FROM placements p WHERE p.status='ACTIVE' AND NOT EXISTS(SELECT 1 FROM internship_reports r WHERE r.placement_id=p.id
        AND r.report_type='FINAL' AND r.status NOT IN ('DRAFT','REVISION_REQUIRED'))
        AND NOT EXISTS(SELECT 1 FROM portfolio_forms f WHERE f.placement_id=p.id AND f.kind='M05'
            AND f.status IN ('SUBMITTED','COMPLETED','APPROVED'))
    UNION ALL
    SELECT p.student_id,p.id::text,'MIDTERM_REPORT_DEADLINE','Nhắc nộp báo cáo giữa kỳ','/student/internship-record',p.midterm_report_due_at
    FROM placements p WHERE p.status='ACTIVE' AND p.require_midterm_report AND NOT EXISTS(SELECT 1 FROM internship_reports r WHERE r.placement_id=p.id
        AND r.report_type='MIDTERM' AND r.status NOT IN ('DRAFT','REVISION_REQUIRED'))
    UNION ALL
    SELECT p.student_id,k.id::text,'TASK_DEADLINE','Nhắc hoàn thành nhiệm vụ thực tập','/student/internship-record',k.due_at
    FROM placements p JOIN placement_tasks k ON k.placement_id=p.id WHERE k.status IN('ASSIGNED','IN_PROGRESS','REVISION_REQUIRED')
    UNION ALL
    SELECT p.mentor_id,k.id::text,'TASK_REVIEW_REMINDER','Nhiệm vụ chờ mentor phản hồi','/mentor/internship-record',k.submitted_at+INTERVAL '2 days'
    FROM placements p JOIN placement_tasks k ON k.placement_id=p.id WHERE k.status='SUBMITTED' AND p.mentor_id IS NOT NULL
    UNION ALL
    SELECT p.lecturer_id,s.id::text,'EVIDENCE_REVIEW_REMINDER','Minh chứng chờ xác minh','/lecturer/internship-record',s.created_at+INTERVAL '2 days'
    FROM placements p JOIN assessment_component_scores s ON s.placement_id=p.id WHERE s.status='PENDING_VERIFICATION'
    UNION ALL
    SELECT p.lecturer_id,r.id::text,'REPORT_REVIEW_REMINDER','Báo cáo chờ giảng viên phản hồi','/lecturer/grading',p.evaluation_deadline
    FROM placements p JOIN internship_reports r ON r.placement_id=p.id WHERE r.status='SUBMITTED'
    UNION ALL
    SELECT p.lecturer_id,p.id::text,'EVALUATION_DEADLINE','Nhắc hoàn thành đánh giá thực tập','/lecturer/grading',p.evaluation_deadline
    FROM placements p WHERE NOT EXISTS(SELECT 1 FROM rubric_evaluations r WHERE r.placement_id=p.id
        AND r.evaluator_id=p.lecturer_id AND r.evaluation_stage='FINAL' AND r.status='SUBMITTED')
    UNION ALL
    SELECT p.mentor_id,p.id::text,'EVALUATION_DEADLINE','Nhắc hoàn thành đánh giá sinh viên','/mentor/final-assessment',p.evaluation_deadline
    FROM placements p WHERE p.mentor_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM rubric_evaluations r WHERE r.placement_id=p.id
        AND r.evaluator_id=p.mentor_id AND r.evaluation_stage='FINAL' AND r.status='SUBMITTED')
    UNION ALL
    SELECT u.id,p.id::text,'MENTOR_ASSIGNMENT_REMINDER','Thực tập chưa có mentor','/company/interns',
        p.start_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM placements p JOIN users u ON u.company_id=p.company_id AND u.role='COMPANY_REP'
    WHERE p.mentor_id IS NULL AND p.status IN ('PREPARING','ACTIVE')
    UNION ALL
    SELECT u.id,a.id::text,'CANDIDATE_REVIEW_REMINDER','Đơn ứng tuyển chờ xử lý','/company/candidates',a.submitted_at+INTERVAL '3 days'
    FROM job_applications a JOIN job_positions j ON j.id=a.job_id
    JOIN internship_terms t ON t.id=j.term_id JOIN users u ON u.company_id=j.company_id AND u.role='COMPANY_REP'
    WHERE a.status IN ('SUBMITTED','REVIEWING') AND t.status NOT IN ('DRAFT','CLOSED')
    UNION ALL
    SELECT u.id,j.id::text,'JOB_REVIEW_REMINDER','Tin thực tập chờ Văn phòng Trường duyệt','/school-office/job-approvals',j.updated_at+INTERVAL '3 days'
    FROM job_positions j JOIN internship_terms t ON t.id=j.term_id JOIN users u ON u.role='SCHOOL_OFFICE' AND u.is_active
    WHERE j.status='PENDING_APPROVAL' AND t.status NOT IN ('DRAFT','CLOSED')
    UNION ALL
    SELECT u.id,p.id::text,'FINALIZATION_DEADLINE','Hồ sơ chờ tổng hợp kết quả','/faculty/evaluations',p.evaluation_deadline
    FROM placements p JOIN users u ON u.department_id=p.department_id AND u.role='FACULTY_ADMIN'
    WHERE NOT EXISTS(SELECT 1 FROM final_results f WHERE f.placement_id=p.id AND f.published_at IS NOT NULL)
    UNION ALL
    SELECT u.id,'email/'||CURRENT_DATE::text,'SYSTEM_EMAIL_ERROR','Có email cần đối soát','/admin/notifications/email',CURRENT_DATE::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM users u WHERE u.role='ADMIN' AND EXISTS(SELECT 1 FROM email_outbox WHERE status IN ('FAILED','UNKNOWN')
        AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours')
    UNION ALL
    SELECT u.id,'ai/'||CURRENT_DATE::text,'SYSTEM_AI_ERROR','Có lượt xử lý AI thất bại','/admin/ai',CURRENT_DATE::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM users u WHERE u.role='ADMIN' AND EXISTS(SELECT 1 FROM ai_runs WHERE status='FAILED'
        AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours')
    UNION ALL
    SELECT u.id,'r2/'||CURRENT_DATE::text,'SYSTEM_R2_ERROR','Có upload bị từ chối cần kiểm tra','/admin/storage',CURRENT_DATE::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh'
    FROM users u WHERE u.role='ADMIN' AND EXISTS(SELECT 1 FROM document_upload_tickets WHERE status='REJECTED'
        AND created_at>CURRENT_TIMESTAMP-INTERVAL '24 hours')
), candidates AS (
    SELECT due.*,kind||':'||entity||':'||recipient_id::text||':'||extract(epoch FROM due_at)::text||':'||
        CASE WHEN due_at<? THEN 'overdue' ELSE 'before' END AS event_key
    FROM due JOIN users u ON u.id=due.recipient_id AND u.is_active=true
    WHERE due_at BETWEEN ? AND ?
)
""";

    public List<ReminderCandidate> pending(OffsetDateTime now) {
        return jdbc.query(
                SOURCES
                        + """
SELECT * FROM candidates c WHERE NOT EXISTS(SELECT 1 FROM notification_event_keys k WHERE k.event_key=c.event_key)
ORDER BY due_at,event_key LIMIT 1000
""",
                (rs, index) ->
                        new ReminderCandidate(
                                rs.getString("event_key"),
                                UUID.fromString(rs.getString("recipient_id")),
                                rs.getString("kind"),
                                rs.getString("title"),
                                rs.getString("path"),
                                rs.getObject("due_at", OffsetDateTime.class)),
                now,
                now.minusDays(7),
                now.plusHours(24));
    }

    public boolean stillPending(ReminderCandidate candidate, OffsetDateTime now) {
        return !jdbc.queryForList(
                        SOURCES + "SELECT event_key FROM candidates WHERE event_key=?",
                        String.class,
                        now,
                        now.minusDays(7),
                        now.plusHours(24),
                        candidate.key())
                .isEmpty();
    }

    public boolean reserve(String key) {
        return events.reserve(key);
    }
}
