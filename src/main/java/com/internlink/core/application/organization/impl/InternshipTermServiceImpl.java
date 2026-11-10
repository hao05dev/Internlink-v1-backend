package com.internlink.core.application.organization.impl;

import com.internlink.core.application.organization.InternshipTermService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.presentation.organization.dto.request.InternshipTermRequest;
import com.internlink.core.presentation.organization.dto.response.InternshipTermResponse;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InternshipTermServiceImpl implements InternshipTermService {

    private final JpaInternshipTermRepository termRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;
    private final AuditLogService auditLogService;

    // ── State Machine: chuyển trạng thái hợp lệ cho Kỳ thực tập ─────────────
    //
    //   DRAFT → REGISTRATION_OPEN → APPLICATION_OPEN → ACTIVE → EVALUATING → CLOSED
    //
    // Mỗi bước có thể có điều kiện tiên quyết riêng (ví dụ: CLOSED cần tất cả
    // placement trong kỳ đã kết thúc).
    private static final Map<TermStatus, Set<TermStatus>> VALID_TRANSITIONS = Map.of(
        TermStatus.DRAFT,              Set.of(TermStatus.REGISTRATION_OPEN),
        TermStatus.REGISTRATION_OPEN,  Set.of(TermStatus.APPLICATION_OPEN, TermStatus.DRAFT),
        TermStatus.APPLICATION_OPEN,   Set.of(TermStatus.ACTIVE, TermStatus.REGISTRATION_OPEN),
        TermStatus.ACTIVE,             Set.of(TermStatus.EVALUATING),
        TermStatus.EVALUATING,         Set.of(TermStatus.CLOSED),
        TermStatus.CLOSED,             Set.of() // trạng thái cuối, không chuyển tiếp
    );

    @Override
    @Transactional(readOnly = true)
    public List<InternshipTermResponse> getAllTerms() {
        return termRepository.findAllByOrderByStartDateDesc().stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternshipTermResponse> getActiveTerms() {
        List<InternshipTerm> terms = termRepository.findByStatus(TermStatus.ACTIVE);
        if (terms.isEmpty()) {
            terms = termRepository.findAll().stream()
                .filter(t -> t.getStatus() != TermStatus.CLOSED)
                .toList();
        }
        if (terms.isEmpty()) {
            terms = termRepository.findAll();
        }
        return terms.stream().map(this::mapToResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InternshipTermResponse getTermById(UUID id) {
        InternshipTerm term = termRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", id));
        return mapToResponse(term);
    }

    @Override
    @Transactional
    public InternshipTermResponse createTerm(InternshipTermRequest request) {
        requireSchoolOffice();

        if (termRepository.existsByCode(request.getCode().trim().toUpperCase())) {
            throw new BadRequestException("Mã kỳ thực tập '" + request.getCode() + "' đã tồn tại");
        }

        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Ngày bắt đầu thực tập phải trước ngày kết thúc");
        }

        if (request.getRegistrationOpenAt().isAfter(request.getRegistrationCloseAt())) {
            throw new BadRequestException("Thời gian mở đăng ký phải trước thời gian đóng đăng ký");
        }

        InternshipTerm term = InternshipTerm.builder()
            .code(request.getCode().trim().toUpperCase())
            .termName(request.getTermName().trim())
            .academicYear(request.getAcademicYear().trim())
            .semester(request.getSemester().trim())
            .registrationOpenAt(request.getRegistrationOpenAt())
            .registrationCloseAt(request.getRegistrationCloseAt())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .applicationDeadline(request.getApplicationDeadline())
            .evaluationDeadline(request.getEvaluationDeadline())
            .status(TermStatus.DRAFT) // Kỳ mới luôn bắt đầu ở DRAFT bất kể request
            .settings(request.getSettings() != null ? request.getSettings() : Map.of())
            .build();

        InternshipTerm saved = termRepository.save(term);
        auditLogService.logAction(securityGuard.currentUser().getId(), "CREATE_INTERNSHIP_TERM",
            "InternshipTerm", saved.getId(), "SUCCESS", Map.of("code", saved.getCode()), null);
        return mapToResponse(saved);
    }

    /**
     * Chuyển trạng thái kỳ thực tập theo State Machine có kiểm soát.
     *
     * <p>Quy tắc chuyển tiếp:
     * <pre>
     *   DRAFT → REGISTRATION_OPEN
     *   REGISTRATION_OPEN → APPLICATION_OPEN | DRAFT (quay lại)
     *   APPLICATION_OPEN → ACTIVE | REGISTRATION_OPEN (quay lại)
     *   ACTIVE → EVALUATING
     *   EVALUATING → CLOSED  (chỉ khi tất cả placement đã kết thúc)
     *   CLOSED → (không chuyển tiếp)
     * </pre>
     * </p>
     */
    @Override
    @Transactional
    public InternshipTermResponse updateTermStatus(UUID termId, TermStatus newStatus) {
        InternshipTerm term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        requireSchoolOffice();

        TermStatus currentStatus = term.getStatus();

        // ── 1. Kiểm tra chuyển trạng thái có hợp lệ theo State Machine không ──
        Set<TermStatus> allowed = VALID_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new BadRequestException(String.format(
                "Không thể chuyển trạng thái kỳ '%s' từ %s sang %s. Các trạng thái hợp lệ: %s",
                term.getCode(), currentStatus, newStatus, allowed.isEmpty() ? "không có" : allowed
            ));
        }

        // ── 2. Điều kiện tiên quyết theo từng bước đích ──────────────────────
        switch (newStatus) {
            case REGISTRATION_OPEN -> {
                // Chỉ mở đăng ký khi thời điểm hiện tại chưa quá hạn đăng ký
                OffsetDateTime now = OffsetDateTime.now();
                if (now.isAfter(term.getRegistrationCloseAt())) {
                    throw new BadRequestException(
                        "Thời gian đăng ký đã qua hạn, không thể mở đăng ký kỳ '" + term.getCode() + "'"
                    );
                }
            }
            case APPLICATION_OPEN -> {
                // Kiểm tra applicationDeadline chưa qua
                OffsetDateTime now = OffsetDateTime.now();
                if (now.isAfter(term.getApplicationDeadline())) {
                    throw new BadRequestException(
                        "Hạn nộp hồ sơ đã qua, không thể chuyển sang giai đoạn ứng tuyển"
                    );
                }
            }
            case EVALUATING -> {
                // Kiểm tra evaluationDeadline chưa qua
                OffsetDateTime now = OffsetDateTime.now();
                if (now.isAfter(term.getEvaluationDeadline())) {
                    throw new BadRequestException(
                        "Hạn đánh giá đã qua, không thể mở giai đoạn đánh giá"
                    );
                }
            }
            case CLOSED -> {
                // Kiểm tra tất cả placement trong kỳ đã kết thúc
                long unfinishedCount = placementRepository.countUnfinishedPlacementsByTerm(termId);
                if (unfinishedCount > 0) {
                    throw new BadRequestException(String.format(
                        "Kỳ '%s' còn %d lần thực tập chưa kết thúc. Phải hoàn tất hoặc xử lý tất cả trước khi đóng kỳ.",
                        term.getCode(), unfinishedCount
                    ));
                }
            }
            default -> { /* DRAFT, ACTIVE: không có điều kiện tiên quyết đặc biệt */ }
        }

        term.setStatus(newStatus);
        InternshipTerm saved = termRepository.save(term);
        auditLogService.logAction(securityGuard.currentUser().getId(), "UPDATE_INTERNSHIP_TERM_STATUS",
            "InternshipTerm", saved.getId(), "SUCCESS", Map.of("from", currentStatus.name(), "to", newStatus.name()), null);
        return mapToResponse(saved);
    }

    /** Kỳ thực tập là cấp Trường: chỉ Văn phòng Trường (hoặc ADMIN) tạo và chuyển trạng thái. */
    private void requireSchoolOffice() {
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(
            userRepository.findById(securityGuard.currentUser().getId()).orElseThrow()));
    }

    private InternshipTermResponse mapToResponse(InternshipTerm entity) {
        return InternshipTermResponse.builder()
            .id(entity.getId())
            .code(entity.getCode())
            .termName(entity.getTermName())
            .academicYear(entity.getAcademicYear())
            .semester(entity.getSemester())
            .registrationOpenAt(entity.getRegistrationOpenAt())
            .registrationCloseAt(entity.getRegistrationCloseAt())
            .startDate(entity.getStartDate())
            .endDate(entity.getEndDate())
            .applicationDeadline(entity.getApplicationDeadline())
            .evaluationDeadline(entity.getEvaluationDeadline())
            .status(entity.getStatus())
            .settings(entity.getSettings())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
