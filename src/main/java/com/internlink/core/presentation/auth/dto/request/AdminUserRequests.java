package com.internlink.core.presentation.auth.dto.request;

import com.internlink.core.shared.enums.UserRole;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AdminUserRequests {
    private AdminUserRequests() {}
    public record CreateUserRequest(@NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 150) String fullName, @NotNull UserRole role,
        @NotBlank @Size(min = 8, max = 72) String temporaryPassword, UUID departmentId, UUID companyId) {}
    public record UpdateUserRequest(@NotBlank @Size(max = 150) String fullName, @NotNull UserRole role,
        @NotNull Boolean isActive, UUID departmentId, UUID companyId) {}
    public record ChangeEmailRequest(@NotBlank @Email @Size(max = 150) String newEmail, String reason) {}
}
