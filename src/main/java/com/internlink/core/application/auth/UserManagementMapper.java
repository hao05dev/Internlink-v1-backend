package com.internlink.core.application.auth;

import com.internlink.core.domain.auth.User;
import com.internlink.core.presentation.auth.dto.response.*;
import com.internlink.core.shared.enums.UserRole;
import java.util.Map;
import java.util.HashMap;

public final class UserManagementMapper {
    private UserManagementMapper() {}
    public static UserSummaryDto summary(User user) {
        return UserSummaryDto.builder().id(user.getId()).email(user.getEmail()).fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber()).role(user.getRole())
            .departmentId(user.getDepartment() == null ? null : user.getDepartment().getId())
            .companyId(user.getCompany() == null ? null : user.getCompany().getId())
            .mustChangePassword(user.getMustChangePassword()).isActive(user.getIsActive())
            .lastLoginAt(user.getLastLoginAt()).build();
    }

    public static UserDetailDto detail(User user) {
        return UserDetailDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .phoneNumber(user.getPhoneNumber())
            .role(user.getRole())
            .departmentId(user.getDepartment() == null ? null : user.getDepartment().getId())
            .departmentName(user.getDepartment() == null ? null : user.getDepartment().getName())
            .companyId(user.getCompany() == null ? null : user.getCompany().getId())
            .companyName(user.getCompany() == null ? null : user.getCompany().getCompanyName())
            .mustChangePassword(user.getMustChangePassword())
            .lastLoginAt(user.getLastLoginAt())
            .isActive(user.getIsActive())
            .createdAt(user.getCreatedAt())
            .updatedAt(user.getUpdatedAt())
            .authProvider(user.getAuthProvider())
            .permissions(computePermissions(user.getRole()))
            .build();
    }

    private static Map<String, String> computePermissions(UserRole role) {
        Map<String, String> map = new HashMap<>();
        if (role == null) return map;
        switch (role) {
            case ADMIN -> {
                map.put("MANAGE_USERS", "FULL_ACCESS");
                map.put("MANAGE_DEPARTMENTS", "FULL_ACCESS");
                map.put("MANAGE_TERMS", "FULL_ACCESS");
                map.put("MANAGE_COMPANIES", "FULL_ACCESS");
                map.put("MANAGE_CANDIDATES", "FULL_ACCESS");
                map.put("MANAGE_LETTERS", "FULL_ACCESS");
                map.put("MANAGE_TEMPLATES", "FULL_ACCESS");
                map.put("MANAGE_BLOG", "FULL_ACCESS");
                map.put("MANAGE_STORAGE", "FULL_ACCESS");
                map.put("MANAGE_SYSTEM", "FULL_ACCESS");
            }
            case FACULTY_ADMIN -> {
                map.put("MANAGE_USERS", "READ_ONLY");
                map.put("MANAGE_DEPARTMENTS", "READ_ONLY");
                map.put("MANAGE_TERMS", "FULL_ACCESS");
                map.put("MANAGE_COMPANIES", "READ_ONLY");
                map.put("MANAGE_CANDIDATES", "FULL_ACCESS");
                map.put("MANAGE_LETTERS", "READ_ONLY");
                map.put("MANAGE_TEMPLATES", "FULL_ACCESS");
                map.put("MANAGE_BLOG", "READ_ONLY");
                map.put("MANAGE_STORAGE", "NO_ACCESS");
                map.put("MANAGE_SYSTEM", "NO_ACCESS");
            }
            case LECTURER -> {
                map.put("MANAGE_USERS", "NO_ACCESS");
                map.put("MANAGE_DEPARTMENTS", "READ_ONLY");
                map.put("MANAGE_TERMS", "READ_ONLY");
                map.put("MANAGE_COMPANIES", "READ_ONLY");
                map.put("MANAGE_CANDIDATES", "FULL_ACCESS");
                map.put("MANAGE_LETTERS", "READ_ONLY");
                map.put("MANAGE_TEMPLATES", "READ_ONLY");
                map.put("MANAGE_BLOG", "NO_ACCESS");
                map.put("MANAGE_STORAGE", "NO_ACCESS");
                map.put("MANAGE_SYSTEM", "NO_ACCESS");
            }
            case COMPANY_REP, COMPANY_MENTOR -> {
                map.put("MANAGE_USERS", "NO_ACCESS");
                map.put("MANAGE_DEPARTMENTS", "NO_ACCESS");
                map.put("MANAGE_TERMS", "READ_ONLY");
                map.put("MANAGE_COMPANIES", role == UserRole.COMPANY_REP ? "FULL_ACCESS" : "READ_ONLY");
                map.put("MANAGE_CANDIDATES", "FULL_ACCESS");
                map.put("MANAGE_LETTERS", "NO_ACCESS");
                map.put("MANAGE_TEMPLATES", "READ_ONLY");
                map.put("MANAGE_BLOG", "NO_ACCESS");
                map.put("MANAGE_STORAGE", "NO_ACCESS");
                map.put("MANAGE_SYSTEM", "NO_ACCESS");
            }
            case SCHOOL_OFFICE -> {
                map.put("MANAGE_USERS", "NO_ACCESS");
                map.put("MANAGE_DEPARTMENTS", "READ_ONLY");
                map.put("MANAGE_TERMS", "READ_ONLY");
                map.put("MANAGE_COMPANIES", "FULL_ACCESS");
                map.put("MANAGE_CANDIDATES", "NO_ACCESS");
                map.put("MANAGE_LETTERS", "FULL_ACCESS");
                map.put("MANAGE_TEMPLATES", "READ_ONLY");
                map.put("MANAGE_BLOG", "FULL_ACCESS");
                map.put("MANAGE_STORAGE", "READ_ONLY");
                map.put("MANAGE_SYSTEM", "NO_ACCESS");
            }
            default -> {
                map.put("MANAGE_USERS", "NO_ACCESS");
                map.put("MANAGE_DEPARTMENTS", "NO_ACCESS");
                map.put("MANAGE_TERMS", "READ_ONLY");
                map.put("MANAGE_COMPANIES", "READ_ONLY");
                map.put("MANAGE_CANDIDATES", "NO_ACCESS");
                map.put("MANAGE_LETTERS", "READ_ONLY");
                map.put("MANAGE_TEMPLATES", "READ_ONLY");
                map.put("MANAGE_BLOG", "READ_ONLY");
                map.put("MANAGE_STORAGE", "NO_ACCESS");
                map.put("MANAGE_SYSTEM", "NO_ACCESS");
            }
        }
        return map;
    }
}
