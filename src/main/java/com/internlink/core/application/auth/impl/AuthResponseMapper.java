package com.internlink.core.application.auth.impl;

import com.internlink.core.domain.auth.User;
import com.internlink.core.presentation.auth.dto.response.AuthResponse;
import com.internlink.core.presentation.auth.dto.response.UserSummaryDto;

public final class AuthResponseMapper {
    private AuthResponseMapper() {}
    public static AuthResponse auth(User user) {
        return AuthResponse.builder().userId(user.getId()).email(user.getEmail())
                .fullName(user.getFullName()).role(user.getRole())
                .mustChangePassword(user.getMustChangePassword()).build();
    }
    public static UserSummaryDto summary(User user) {
        return UserSummaryDto.builder().id(user.getId()).email(user.getEmail())
                .fullName(user.getFullName()).phoneNumber(user.getPhoneNumber()).role(user.getRole())
                .departmentId(user.getDepartment() == null ? null : user.getDepartment().getId())
                .companyId(user.getCompany() == null ? null : user.getCompany().getId())
                .mustChangePassword(user.getMustChangePassword()).lastLoginAt(user.getLastLoginAt())
                .isActive(user.getIsActive()).googleLinked(user.getGoogleSubject() != null).build();
    }
}
