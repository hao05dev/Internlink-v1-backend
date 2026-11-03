package com.internlink.core.application.company;

import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.auth.User;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyAccessPolicy {
    private final SecurityGuard guard;
    private final JpaUserRepository users;

    public User actor() {
        return users.findById(guard.currentUser().getId())
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ForbiddenException("Tài khoản không hoạt động"));
    }

    public Company ownCompany() {
        User actor = actor();
        ResourceAuthorization.require(actor.getRole() == UserRole.COMPANY_REP && actor.getCompany() != null);
        return actor.getCompany();
    }

    public void requireRead(UUID id) {
        User user = actor();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(user) || user.getRole() == UserRole.FACULTY_ADMIN
                || ResourceAuthorization.belongsToCompany(user, id));
    }

    public void requireOwner(UUID id) {
        ResourceAuthorization.require(ResourceAuthorization.representsCompany(actor(), id));
    }
}
