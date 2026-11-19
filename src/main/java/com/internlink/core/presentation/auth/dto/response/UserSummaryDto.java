package com.internlink.core.presentation.auth.dto.response;

import com.internlink.core.shared.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;
import java.util.UUID;



@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {

    private UUID id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private UUID departmentId;
    private UUID companyId;
    private Boolean mustChangePassword;
    private OffsetDateTime lastLoginAt;
    private Boolean isActive;
    /** Tài khoản đã liên kết danh tính Google (đăng nhập Google được). */
    private Boolean googleLinked;
}
