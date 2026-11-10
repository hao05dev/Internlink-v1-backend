package com.internlink.core.application.recruitment;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobPositionAccessPolicy {
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;

    public User currentActor() {
        UUID actorId = securityGuard.currentUser().getId();
        return userRepository
                .findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
    }

    public boolean canReadJob(User actor, JobPosition job) {
        return job.getStatus() == JobStatus.APPROVED
                || ResourceAuthorization.isAdmin(actor)
                || actor.getRole() == UserRole.SCHOOL_OFFICE
                || ResourceAuthorization.isFacultyAdmin(actor)
                || ResourceAuthorization.representsCompany(actor, job.getCompany().getId());
    }
}
