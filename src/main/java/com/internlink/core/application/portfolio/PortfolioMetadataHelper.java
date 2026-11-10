package com.internlink.core.application.portfolio;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PortfolioMetadataHelper {

    private final JpaStudentRosterRepository rosters;

    public int weekCount(InternshipPlacement p) {
        return (int) (ChronoUnit.DAYS.between(p.getStartDate(), p.getEndDate()) / 7) + 1;
    }

    public String fileName(InternshipPlacement p, String kind) {
        return "M-TT-"
                + kind.substring(1)
                + "_"
                + metadata(p).getOrDefault("studentCode", p.getId().toString())
                + ".docx";
    }

    public Map<String, Object> metadata(InternshipPlacement p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("studentName", p.getStudent().getFullName());
        m.put(
                "companyName",
                p.getCompany() != null
                        ? p.getCompany().getCompanyName()
                        : p.getStudentFoundApplication() != null
                                ? p.getStudentFoundApplication().getHostName()
                                : "");
        m.put(
                "mentorName",
                p.getMentor() != null
                        ? p.getMentor().getFullName()
                        : p.getStudentFoundApplication() != null
                                ? p.getStudentFoundApplication().getContactName()
                                : "");
        m.put(
                "mentorEmail",
                p.getMentor() != null
                        ? p.getMentor().getEmail()
                        : p.getStudentFoundApplication() != null
                                ? p.getStudentFoundApplication().getContactEmail()
                                : "");
        m.put(
                "mentorPhone",
                p.getMentor() != null ? Objects.toString(p.getMentor().getPhoneNumber(), "") : "");
        m.put("lecturerName", p.getLecturer() == null ? "" : p.getLecturer().getFullName());
        m.put("termId", p.getTerm().getId());
        m.put("termName", p.getTerm().getTermName());
        m.put("semester", p.getTerm().getSemester());
        m.put("academicYear", p.getTerm().getAcademicYear());
        m.put(
                "termStatus",
                p.getTerm().getStatus() != null ? p.getTerm().getStatus().name() : "ACTIVE");
        var department = DepartmentScope.of(p);
        m.put("departmentName", department != null ? department.getName() : "");
        m.put("startDate", p.getStartDate());
        m.put("endDate", p.getEndDate());
        m.put("status", p.getStatus());
        m.put("source", p.getSource());
        m.put("weekCount", weekCount(p));
        m.put("workSchedule", p.getWorkSchedule());
        m.put("hasMentor", p.getMentor() != null);

        rosters.findByTermIdAndClaimedUserId(p.getTerm().getId(), p.getStudent().getId())
                .ifPresent(
                        r -> {
                            m.put("studentCode", r.getStudentCode());
                            m.put("cohort", r.getAcademicYear());
                            m.put(
                                    "programName",
                                    r.getProgram() == null ? "" : r.getProgram().getName());
                            m.put(
                                    "programCode",
                                    r.getProgram() == null ? "DEFAULT" : r.getProgram().getCode());
                            m.put("courseCode", Objects.toString(r.getInternshipCourseCode(), ""));
                        });

        if (p.getAssessmentScheme() != null) {
            var s = p.getAssessmentScheme();
            m.put("components", s.getComponents());
            m.put("requiredWeeks", s.getRequiredLogbookWeeks());

            m.put("schemeReference", s.getSourceReference());
            m.put("courseCode", s.getCourseCode());
        } else {
            m.put("components", List.of());
        }

        // Extension override for report due date
        if (p.getWorkSchedule() != null
                && p.getWorkSchedule().get("extension") instanceof Map<?, ?> ext) {
            if (com.internlink.core.application.placement.ReportDeadlines.extension(p) != null) {

                m.put("extensionReason", ext.get("reason"));
                m.put("isExtended", true);
            }
        }

        m.put(
                "reportDueAt",
                com.internlink.core.application.placement.ReportDeadlines.due(p, "FINAL"));
        return m;
    }

    public static String detectMime(byte[] b) {
        if (b == null) return null;
        if (b.length > 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-')
            return "application/pdf";
        if (b.length > 8 && (b[0] & 255) == 137 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G')
            return "image/png";
        if (b.length > 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
            return "image/jpeg";
        return null;
    }
}
