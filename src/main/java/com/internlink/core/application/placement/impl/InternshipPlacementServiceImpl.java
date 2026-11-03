package com.internlink.core.application.placement.impl;

import com.internlink.core.application.placement.AcceptanceFormGate;
import com.internlink.core.application.placement.InternshipPlacementService;
import com.internlink.core.application.placement.PlacementEligibilityService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.LearningAgreement;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.placement.dto.response.InternshipPlacementResponse;
import com.internlink.core.shared.enums.AgreementStatus;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InternshipPlacementServiceImpl implements InternshipPlacementService {

    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaLearningAgreementRepository agreementRepository;
    private final JpaUserRepository userRepository;
    private final JpaInternshipTermRepository termRepository;
    private final SecurityGuard securityGuard;

    private final InternshipPlacementMapper placementMapper;
    private final PlacementTransitionValidator transitionValidator;
    private final PlacementNotificationHelper notificationHelper;
    private final PlacementEligibilityService placementEligibility;
    private final com.internlink.core.shared.api.PaginatedResponseMapper paginatedResponseMapper;
    private final AcceptanceFormGate acceptanceFormGate;

    @Override
    @Transactional(readOnly = true)
    public List<InternshipPlacementResponse> getPlacementsByTerm(UUID termId) {
        var term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(
            currentActor(), term.getDepartment().getId()));
        return placementRepository.findByTermId(termId).stream()
            .map(placementMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public com.internlink.core.shared.api.PageResponse<InternshipPlacementResponse> getPlacementsByTermPage(
            UUID termId, com.internlink.core.shared.api.PageParam pageParam
    ) {
        var term = termRepository.findById(termId)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(
            currentActor(), term.getDepartment().getId()));
        org.springframework.data.domain.Page<InternshipPlacement> page = placementRepository.findByTermId(termId, pageParam.toPageable());
        return paginatedResponseMapper.toPageResponse(page, placementMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternshipPlacementResponse> getPlacementsByStudent(UUID studentId) {
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor)
            || actor.getRole() == UserRole.STUDENT && actor.getId().equals(studentId));
        return placementRepository.findByStudentId(studentId).stream()
            .map(placementMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternshipPlacementResponse> getPlacementsForMyCompany() {
        User actor = currentActor();
        ResourceAuthorization.require(actor.getRole() == UserRole.COMPANY_REP && actor.getCompany() != null);
        return placementRepository.findByCompanyId(actor.getCompany().getId()).stream()
            .map(placementMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternshipPlacementResponse> getPlacementsByMentor(UUID mentorId) {
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor)
            || actor.getRole() == UserRole.COMPANY_MENTOR && actor.getId().equals(mentorId));
        return placementRepository.findByMentorId(mentorId).stream()
            .map(placementMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternshipPlacementResponse> getPlacementsByLecturer(UUID lecturerId) {
        User actor = currentActor();
        ResourceAuthorization.require(ResourceAuthorization.isAdmin(actor)
            || actor.getRole() == UserRole.LECTURER && actor.getId().equals(lecturerId));
        return placementRepository.findByLecturerId(lecturerId).stream()
            .map(placementMapper::toResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public InternshipPlacementResponse getPlacementById(UUID id) {
        InternshipPlacement placement = placementRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", id));
        ResourceAuthorization.require(ResourceAuthorization.canReadPlacement(currentActor(), placement));
        return placementMapper.toResponse(placement);
    }

    @Override
    @Transactional
    public InternshipPlacementResponse activatePlacementFromAgreement(UUID agreementId, UUID lecturerId) {
        LearningAgreement agreement = agreementRepository.findById(agreementId)
            .orElseThrow(() -> new ResourceNotFoundException("LearningAgreement", "id", agreementId));

        if (agreement.getStatus() != AgreementStatus.APPROVED) {
            throw new BadRequestException("Thỏa thuận 3 bên phải được phê duyệt (APPROVED) trước khi kích hoạt thực tập");
        }

        InternshipTerm term = null;
        if (agreement.getOffer() != null
            && agreement.getOffer().getApplication() != null
            && agreement.getOffer().getApplication().getJob() != null) {
            term = agreement.getOffer().getApplication().getJob().getTerm();
            TermGuard.requireNotClosed(term);
        }

        StudentRoster roster = null;
        if (term != null) {
            roster = placementEligibility.requireEligibleRoster(agreement.getStudent().getId(), term.getId());
            placementEligibility.requireNoLivePlacement(agreement.getStudent().getId(), term.getId());
        }

        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(
            currentActor(), agreement.getDepartment().getId()));

        if (placementRepository.findByAgreementId(agreementId).isPresent()) {
            throw new BadRequestException("Lần thực tập đã được kích hoạt từ thỏa thuận này rồi");
        }
        acceptanceFormGate.approveForAgreement(agreementId, currentActor());

        User chosen = lecturerId == null ? null : userRepository.findById(lecturerId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        User lecturer = placementEligibility.resolveLecturer(chosen, roster);
        placementEligibility.requireActiveLecturer(lecturer, agreement.getDepartment().getId());
        if (term != null) {
            placementEligibility.syncRosterLecturer(agreement.getStudent().getId(), term.getId(), lecturer);
        }

        User mentor = agreement.getOffer().getProposedMentor();
        if (mentor == null) {
            throw new BadRequestException("Offer chưa được chỉ định Mentor doanh nghiệp");
        }

        InternshipPlacement placement = InternshipPlacement.builder()
            .agreement(agreement)
            .student(agreement.getStudent())
            .company(agreement.getCompany())
            .mentor(mentor)
            .lecturer(lecturer)
            .term(term)
            .startDate(agreement.getOffer().getStartDate())
            .endDate(agreement.getOffer().getEndDate())
            .totalHoursWorked(BigDecimal.ZERO)
            .status(PlacementStatus.PREPARING)
            .workSchedule(Map.of())
            .build();

        InternshipPlacement saved = placementRepository.save(placement);
        notificationHelper.logAndNotifyActivation(currentActor().getId(), saved, agreement, mentor);

        return placementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InternshipPlacementResponse assignLecturer(UUID id, UUID lecturerId) {
        InternshipPlacement placement = placementRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", id));
        TermGuard.requireNotClosed(placement.getTerm());
        User actor = currentActor();
        ResourceAuthorization.require(actor.getRole() == UserRole.FACULTY_ADMIN
            && ResourceAuthorization.managesDepartment(actor, placement.getTerm().getDepartment().getId()));
        if (Set.of(PlacementStatus.COMPLETED, PlacementStatus.TERMINATED, PlacementStatus.TRANSFERRED)
            .contains(placement.getStatus())) {
            throw new BadRequestException("Không thể đổi GVHD cho lần thực tập đã kết thúc");
        }
        User lecturer = userRepository.findById(lecturerId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", lecturerId));
        placementEligibility.requireActiveLecturer(lecturer, placement.getTerm().getDepartment().getId());

        UUID previousId = placement.getLecturer() != null ? placement.getLecturer().getId() : null;
        placement.setLecturer(lecturer);
        InternshipPlacement saved = placementRepository.save(placement);
        placementEligibility.syncRosterLecturer(placement.getStudent().getId(), placement.getTerm().getId(), lecturer);
        notificationHelper.logAndNotifyLecturerAssignment(actor.getId(), saved, lecturer, previousId);

        return placementMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InternshipPlacementResponse updatePlacementStatus(UUID id, PlacementStatus newStatus) {
        InternshipPlacement placement = placementRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", id));
        TermGuard.requireNotClosed(placement.getTerm());
        ResourceAuthorization.require(ResourceAuthorization.managesDepartment(
            currentActor(), placement.getTerm().getDepartment().getId()));

        PlacementStatus currentStatus = placement.getStatus();
        transitionValidator.validateTransition(placement, newStatus);

        placement.setStatus(newStatus);
        InternshipPlacement saved = placementRepository.save(placement);
        notificationHelper.logAndNotifyStatusChange(saved, currentStatus, newStatus);

        return placementMapper.toResponse(saved);
    }

    private User currentActor() {
        UUID actorId = securityGuard.currentUser().getId();
        return userRepository.findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
    }
}
