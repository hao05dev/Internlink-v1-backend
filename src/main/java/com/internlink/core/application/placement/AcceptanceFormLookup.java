package com.internlink.core.application.placement;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Tra cứu dùng chung cho các service xử lý Phiếu tiếp nhận. */
@Component
@RequiredArgsConstructor
public class AcceptanceFormLookup {
    private final JpaStudentFoundApplicationRepository applications;
    private final JpaUserRepository users;
    private final SecurityGuard security;

    public StudentFoundApplication get(UUID id) {
        return applications.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("StudentFoundApplication", "id", id));
    }

    public User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    public static void requireStatus(StudentFoundApplication app, String expected, String message) {
        if (!expected.equals(app.getStatus())) throw new BadRequestException(message);
    }

    public static String requireNote(String note, String message) {
        if (note == null || note.isBlank()) throw new BadRequestException(message);
        return note.trim();
    }
}
