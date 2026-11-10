package com.internlink.core.application.placement.impl;

import com.internlink.core.application.placement.LearningAgreementService;
import com.internlink.core.application.placement.PlacementEligibilityService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.placement.LearningAgreement;
import com.internlink.core.domain.recruitment.PlacementOffer;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.persistence.jpa.JpaLearningAgreementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPlacementOfferRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.placement.dto.request.LearningAgreementRequest;
import com.internlink.core.presentation.placement.dto.response.LearningAgreementResponse;
import com.internlink.core.shared.enums.AgreementStatus;
import com.internlink.core.shared.enums.OfferStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LearningAgreementServiceImpl implements LearningAgreementService {

    private final JpaLearningAgreementRepository agreementRepository;
    private final JpaPlacementOfferRepository offerRepository;
    private final JpaUserRepository userRepository;
    private final JpaStudentProfileRepository studentProfileRepository;
    private final PlacementEligibilityService placementEligibility;
    private final SecurityGuard securityGuard;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public LearningAgreementResponse getAgreementById(UUID id) {
        LearningAgreement agreement = agreementRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LearningAgreement", "id", id));
        ResourceAuthorization.require(ResourceAuthorization.canReadAgreement(currentActor(), agreement));
        return mapToResponse(agreement);
    }

    @Override
    @Transactional(readOnly = true)
    public LearningAgreementResponse getAgreementByOfferId(UUID offerId) {
        LearningAgreement agreement = agreementRepository.findByOfferId(offerId)
            .orElseThrow(() -> new ResourceNotFoundException("LearningAgreement", "offerId", offerId));
        ResourceAuthorization.require(ResourceAuthorization.canReadAgreement(currentActor(), agreement));
        return mapToResponse(agreement);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningAgreementResponse> getAgreementsByStudent(UUID studentId) {
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor) || actor.getId().equals(studentId));
        return agreementRepository.findByStudentId(studentId).stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LearningAgreementResponse> getAgreementsByDepartment(UUID departmentId, AgreementStatus status) {
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(currentActor(), departmentId));
        List<LearningAgreement> agreements = status == null
            ? agreementRepository.findByDepartmentId(departmentId)
            : agreementRepository.findByDepartmentIdAndStatus(departmentId, status);
        return agreements.stream()
            .map(this::mapToResponse)
            .toList();
    }

    @Override
    @Transactional
    public LearningAgreementResponse createAgreement(UUID studentId, LearningAgreementRequest request) {
        PlacementOffer offer = offerRepository.findById(request.getOfferId())
            .orElseThrow(() -> new ResourceNotFoundException("PlacementOffer", "id", request.getOfferId()));
        TermGuard.requireNotClosed(offer.getApplication().getJob().getTerm());

        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw new BadRequestException("Chỉ có thể tạo Thỏa thuận học tập cho Offer đã được chấp nhận (ACCEPTED)");
        }

        if (!offer.getApplication().getStudent().getId().equals(studentId)) {
            throw new BadRequestException("Offer không thuộc về sinh viên hiện tại");
        }

        if (agreementRepository.findByOfferId(request.getOfferId()).isPresent()) {
            throw new BadRequestException("Đã tồn tại Thỏa thuận học tập cho Offer này");
        }

        // Đảm bảo sinh viên chưa có placement live trong kỳ (hoặc nếu là fresher giữ việc làm ngoài thì không tạo agreement)
        var term = offer.getApplication().getJob().getTerm();
        if (term != null) {
            placementEligibility.requireNoLivePlacement(studentId, term.getId());
        }

        User student = offer.getApplication().getStudent();
        // Khoa quản lý thỏa thuận = khoa của sinh viên (tin tuyển dụng là cấp Trường).
        Department department = student.getDepartment();
        if (department == null) {
            throw new BadRequestException("Tài khoản sinh viên chưa gắn với khoa quản lý ngành");
        }

        LearningAgreement agreement = LearningAgreement.builder()
            .offer(offer)
            .student(student)
            .company(offer.getApplication().getJob().getCompany())
            .department(department)
            .targetCredits(request.getTargetCredits())
            .learningObjectives(request.getLearningObjectives())
            .status(AgreementStatus.DRAFT)
            .build();

        LearningAgreement saved = agreementRepository.save(agreement);

        auditLogService.logAction(
            studentId,
            "CREATE_AGREEMENT",
            "LearningAgreement",
            saved.getId(),
            "SUCCESS",
            Map.of("offerId", request.getOfferId()),
            null
        );

        notificationService.sendNotification(
            studentId,
            "AGREEMENT_CREATED",
            "Thỏa thuận học tập đã khởi tạo",
            "Thỏa thuận học tập 3 bên đã được tạo. Vui lòng ký số để hoàn tất.",
            "/agreements/" + saved.getId()
        );

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public LearningAgreementResponse signAgreement(UUID id, String signerRole, Map<String, Object> signatureData) {
        LearningAgreement agreement = agreementRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LearningAgreement", "id", id));
        if (agreement.getOffer() != null
            && agreement.getOffer().getApplication() != null
            && agreement.getOffer().getApplication().getJob() != null) {
            TermGuard.requireNotClosed(agreement.getOffer().getApplication().getJob().getTerm());
        }

        if (agreement.getStatus() == AgreementStatus.APPROVED || agreement.getStatus() == AgreementStatus.CANCELLED) {
            throw new BadRequestException("Thỏa thuận đã hoàn tất hoặc đã hủy, không thể ký lại");
        }
        if (signatureData == null || signatureData.isEmpty()) {
            throw new BadRequestException("Dữ liệu chữ ký không được để trống");
        }

        var currentUser = securityGuard.currentUser();
        UUID currentUserId = currentUser.getId();
        UserRole actualRole = currentUser.getRole();

        switch (actualRole) {
            case STUDENT -> {
                if (!agreement.getStudent().getId().equals(currentUserId)) {
                    throw new ForbiddenException(
                        "Bạn chỉ có thể ký thỏa thuận học tập của chính mình"
                    );
                }
                agreement.setStudentSignature(signatureData);
            }
            case COMPANY_REP -> {
                if (!ResourceAuthorization.representsCompany(currentActor(), agreement.getCompany().getId())) {
                    throw new ForbiddenException(
                        "Bạn chỉ có thể ký thỏa thuận học tập của doanh nghiệp mình"
                    );
                }
                agreement.setCompanySignature(signatureData);
            }
            case FACULTY_ADMIN, ADMIN -> {
                ResourceAuthorization.require(ResourceAuthorization.managesDepartment(
                    currentActor(), com.internlink.core.shared.security.DepartmentScope.id(agreement.getDepartment())));
                agreement.setFacultySignature(signatureData);
            }
            default -> throw new ForbiddenException(
                "Vai trò " + actualRole + " không có thẩm quyền ký thỏa thuận học tập"
            );
        }

        boolean fullySigned = agreement.getStudentSignature() != null
            && agreement.getCompanySignature() != null
            && agreement.getFacultySignature() != null;

        if (fullySigned) {
            agreement.setStatus(AgreementStatus.APPROVED);
        } else {
            agreement.setStatus(AgreementStatus.PENDING_SIGNATURES);
        }

        LearningAgreement saved = agreementRepository.save(agreement);

        auditLogService.logAction(
            currentUserId,
            "SIGN_AGREEMENT_" + actualRole.name(),
            "LearningAgreement",
            saved.getId(),
            "SUCCESS",
            Map.of("status", saved.getStatus().name()),
            null
        );

        if (fullySigned) {
            UUID studentId = agreement.getStudent().getId();
            notificationService.sendNotification(
                studentId,
                "AGREEMENT_APPROVED",
                "Thỏa thuận học tập đã được duyệt",
                "Thỏa thuận 3 bên của bạn đã hoàn tất chữ ký và được phê duyệt chính thức.",
                "/agreements/" + saved.getId()
            );

            var jobCreatedBy = agreement.getOffer().getApplication().getJob().getCreatedBy();
            if (jobCreatedBy != null) {
                notificationService.sendNotification(
                    jobCreatedBy.getId(),
                    "AGREEMENT_APPROVED",
                    "Thỏa thuận học tập đã hoàn tất",
                    "Thỏa thuận 3 bên với sinh viên " + agreement.getStudent().getFullName() + " đã hoàn tất chữ ký.",
                    "/agreements/" + saved.getId()
                );
            }
        }

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public LearningAgreementResponse reviewAgreementByFaculty(UUID id, AgreementStatus status) {
        LearningAgreement agreement = agreementRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("LearningAgreement", "id", id));
        if (agreement.getOffer() != null
            && agreement.getOffer().getApplication() != null
            && agreement.getOffer().getApplication().getJob() != null) {
            TermGuard.requireNotClosed(agreement.getOffer().getApplication().getJob().getTerm());
        }
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(
            currentActor(), agreement.getDepartment().getId()));

        if (status != AgreementStatus.REVISION_REQUESTED && status != AgreementStatus.CANCELLED) {
            throw new BadRequestException("Kết quả rà soát chỉ có thể là REVISION_REQUESTED hoặc CANCELLED");
        }

        agreement.setStatus(status);
        LearningAgreement saved = agreementRepository.save(agreement);

        UUID currentUserId = securityGuard.currentUser() != null ? securityGuard.currentUser().getId() : null;
        auditLogService.logAction(
            currentUserId,
            "REVIEW_AGREEMENT_" + status.name(),
            "LearningAgreement",
            saved.getId(),
            "SUCCESS",
            Map.of("status", status.name()),
            null
        );

        if (status == AgreementStatus.REVISION_REQUESTED) {
            notificationService.sendNotification(
                agreement.getStudent().getId(),
                "AGREEMENT_REVISION",
                "Yêu cầu chỉnh sửa thỏa thuận học tập",
                "Khoa đã yêu cầu chỉnh sửa thỏa thuận học tập của bạn. Vui lòng kiểm tra lại nội dung.",
                "/agreements/" + saved.getId()
            );
        }

        return mapToResponse(saved);
    }

    private LearningAgreementResponse mapToResponse(LearningAgreement entity) {
        String studentCode = studentProfileRepository.findById(entity.getStudent().getId())
            .map(StudentProfile::getStudentCode)
            .orElse(null);

        UUID termId = (entity.getOffer() != null
                && entity.getOffer().getApplication() != null
                && entity.getOffer().getApplication().getJob() != null
                && entity.getOffer().getApplication().getJob().getTerm() != null)
                ? entity.getOffer().getApplication().getJob().getTerm().getId()
                : null;

        return LearningAgreementResponse.builder()
            .id(entity.getId())
            .offerId(entity.getOffer().getId())
            .termId(termId)
            .studentId(entity.getStudent().getId())
            .studentName(entity.getStudent().getFullName())
            .studentCode(studentCode)
            .companyId(entity.getCompany().getId())
            .companyName(entity.getCompany().getCompanyName())
            .departmentId(entity.getDepartment().getId())
            .departmentName(entity.getDepartment().getName())
            .targetCredits(entity.getTargetCredits())
            .learningObjectives(entity.getLearningObjectives())
            .status(entity.getStatus())
            .studentSignature(entity.getStudentSignature())
            .companySignature(entity.getCompanySignature())
            .facultySignature(entity.getFacultySignature())
            .documentId(entity.getDocument() != null ? entity.getDocument().getId() : null)
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private User currentActor() {
        UUID actorId = securityGuard.currentUser().getId();
        return userRepository.findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
    }
}
