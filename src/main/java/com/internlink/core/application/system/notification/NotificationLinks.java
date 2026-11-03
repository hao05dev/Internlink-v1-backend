package com.internlink.core.application.system.notification;
import com.internlink.core.shared.enums.UserRole;
public final class NotificationLinks {
    private NotificationLinks() {}
    public static String normalize(String path, UserRole role) {
        if (path == null || !path.startsWith("/") || path.startsWith("//") || path.contains("\\")
            || path.contains("\r") || path.contains("\n")) return home(role);
        if (path.startsWith("/placements/")) return switch(role) {
            case STUDENT -> "/student/internship-record";
            case LECTURER -> "/lecturer/supervision";
            case COMPANY_MENTOR -> "/mentor/internship-record";
            case COMPANY_REP -> "/company/interns";
            case FACULTY_ADMIN -> "/faculty/internship-management";
            case ADMIN -> "/admin/dashboard";
            case SCHOOL_OFFICE -> "/school-office/acceptance-forms";
        };
        if (path.startsWith("/agreements/")) return role == UserRole.STUDENT ? "/student/learning-agreement" : home(role);
        if (path.startsWith("/final-results/placement/")) return role == UserRole.STUDENT ? "/student/final-report" : home(role);
        if (path.equals("/student/weekly-logs")) return role == UserRole.STUDENT ? "/student/daily-logs" : home(role);
        if (path.startsWith("/student/") && role != UserRole.STUDENT) return home(role);
        if (path.startsWith("/faculty/") && role != UserRole.FACULTY_ADMIN) return home(role);
        if (path.startsWith("/company/") && role != UserRole.COMPANY_REP) return home(role);
        if (path.startsWith("/mentor/") && role != UserRole.COMPANY_MENTOR) return home(role);
        if (path.startsWith("/lecturer/") && role != UserRole.LECTURER) return home(role);
        if (path.startsWith("/admin/") && role != UserRole.ADMIN) return home(role);
        if (path.startsWith("/school-office/") && role != UserRole.SCHOOL_OFFICE) return home(role);
        return path;
    }
    public static String home(UserRole role) {
        return switch(role) {
            case STUDENT -> "/student/dashboard";
            case LECTURER -> "/lecturer/dashboard";
            case COMPANY_MENTOR -> "/mentor/dashboard";
            case COMPANY_REP -> "/company/dashboard";
            case FACULTY_ADMIN -> "/faculty/dashboard";
            case ADMIN -> "/admin/dashboard";
            case SCHOOL_OFFICE -> "/school-office/acceptance-forms";
        };
    }
}
