package com.internlink.core.application.portfolio;

import com.internlink.core.shared.security.DepartmentScope;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.*;
import com.internlink.core.shared.enums.UserRole;
import java.util.Set;

/** Assessment visibility is narrower than generic placement access. */
public final class PortfolioAccess {
    private PortfolioAccess() {}
    public static boolean student(User a, InternshipPlacement p) { return a.getRole()==UserRole.STUDENT && a.getId().equals(p.getStudent().getId()); }
    public static boolean mentor(User a, InternshipPlacement p) { return a.getRole()==UserRole.COMPANY_MENTOR && p.getMentor()!=null && a.getId().equals(p.getMentor().getId()); }
    public static boolean lecturer(User a, InternshipPlacement p) { return a.getRole()==UserRole.LECTURER && p.getLecturer()!=null && a.getId().equals(p.getLecturer().getId()); }
    public static boolean faculty(User a, InternshipPlacement p) { return a.getRole()==UserRole.FACULTY_ADMIN && DepartmentScope.sameDepartment(a, DepartmentScope.idOf(p)); }
    public static boolean read(User a, InternshipPlacement p) { return student(a,p)||mentor(a,p)||lecturer(a,p)||faculty(a,p); }
    public static boolean confirmDaily(User a, InternshipPlacement p) { return mentor(a,p)||(p.getMentor()==null && lecturer(a,p)); }
    public static boolean proxyUpload(User a, InternshipPlacement p, String kind) { return student(a, p) && p.getMentor() == null && Set.of("M01", "M02", "M03").contains(kind); }
    public static boolean edit(User a, PortfolioForm f) {
        return switch(f.getKind()) {
            case "M01","M02","M03" -> mentor(a,f.getPlacement());
            case "M04" -> lecturer(a,f.getPlacement());
            case "M05","FORM04" -> student(a,f.getPlacement());
            default -> false;
        };
    }
    public static boolean confidential(String kind) { return Set.of("M02","M03","M04").contains(kind); }
    public static boolean content(User a, PortfolioForm f) {
        if (!read(a,f.getPlacement())) return false;
        if (student(a,f.getPlacement()) && confidential(f.getKind())) return f.isPublished();
        if (mentor(a,f.getPlacement()) && "M04".equals(f.getKind())) return f.isPublished();
        return true;
    }
}
