package com.internlink.core.application.system;

import com.internlink.core.domain.system.OnboardingProgress;
import com.internlink.core.infrastructure.persistence.jpa.JpaOnboardingProgressRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Tiến độ tour hướng dẫn; mọi thao tác chỉ trên bản ghi của chính người đang đăng nhập. */
@Service
@RequiredArgsConstructor
public class OnboardingProgressService {
    public static final String TOUR_KEY_PATTERN = "[a-z0-9][a-z0-9-]{0,59}";

    private final JpaOnboardingProgressRepository repository;
    private final SecurityGuard guard;

    public record Progress(String tourKey, int version, String status, Integer lastStep, OffsetDateTime updatedAt) {}

    public record UpdateRequest(
            @Min(1) @Max(1000) int version,
            @NotNull @Pattern(regexp = "COMPLETED|DISMISSED") String status,
            @Min(0) @Max(100) Integer lastStep) {}

    @Transactional(readOnly = true)
    public List<Progress> list() {
        return repository.findByUserIdOrderByTourKey(currentUserId()).stream().map(OnboardingProgressService::toResponse).toList();
    }

    @Transactional
    public Progress save(String tourKey, UpdateRequest request) {
        if (tourKey == null || !tourKey.matches(TOUR_KEY_PATTERN)) {
            throw new BadRequestException("Mã tour không hợp lệ");
        }
        OnboardingProgress row = OnboardingProgress.builder()
                .userId(currentUserId())
                .tourKey(tourKey)
                .tourVersion(request.version())
                .status(request.status())
                .lastStep(request.lastStep())
                .updatedAt(OffsetDateTime.now())
                .build();
        return toResponse(repository.save(row));
    }

    private UUID currentUserId() {
        return guard.currentUser().getId();
    }

    private static Progress toResponse(OnboardingProgress row) {
        return new Progress(row.getTourKey(), row.getTourVersion(), row.getStatus(), row.getLastStep(), row.getUpdatedAt());
    }
}
