package com.internlink.core.application.system.upload;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DocumentAccessPolicy {
    private final JpaUserRepository userRepository;
    private final SecurityGuard securityGuard;

    private final JpaJobApplicationRepository applicationRepository;
    private final JpaStudentFoundApplicationRepository studentFoundApplicationRepository;
    private final JpaLearningAgreementRepository agreementRepository;
    private final JpaInternshipPlacementRepository placementRepository;
    private final JpaPlacementTaskRepository taskRepository;
    private final JpaWeeklyLogbookRepository logbookRepository;
    private final JpaRubricEvaluationRepository rubricRepository;
    private final JpaInternshipCaseRepository caseRepository;

    public User currentActor() {
        UUID actorId = securityGuard.currentUser().getId();
        return userRepository
                .findById(actorId)
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
    }

    public void requireUploadAccess(UUID ownerId, ContextType contextType, UUID contextId) {
        User actor = currentActor();
        ResourceAuthorization.require(
                actor.getId().equals(ownerId) && canAccessContext(actor, contextType, contextId));
    }

    public boolean canReadDocument(User actor, Document document) {
        if (actor.getId().equals(document.getOwner().getId())
                || ResourceAuthorization.isAdmin(actor)) {
            return true;
        }
        if (document.getContextType() == ContextType.PROFILE) {
            return document.getDocumentType() == DocumentType.CV
                    && applicationRepository.findBySubmittedCvDocumentId(document.getId()).stream()
                            .anyMatch(app -> ResourceAuthorization.canReadApplication(actor, app));
        }
        return canAccessContext(actor, document.getContextType(), document.getContextId());
    }

    private boolean canAccessContext(User actor, ContextType contextType, UUID contextId) {
        if (contextType == null || contextId == null) return false;
        return switch (contextType) {
            case PROFILE -> actor.getId().equals(contextId);
            case APPLICATION ->
                    applicationRepository
                            .findById(contextId)
                            .map(app -> ResourceAuthorization.canReadApplication(actor, app))
                            .orElse(false);
            case SELF_FOUND ->
                    studentFoundApplicationRepository
                            .findById(contextId)
                            .map(
                                    app ->
                                            actor.getId().equals(app.getStudent().getId())
                                                    || ResourceAuthorization.actsForSchool(actor)
                                                    || ResourceAuthorization.managesDepartment(
                                                            actor,
                                                            app.getTerm().getDepartment().getId())
                                                    || actor.getRole()
                                                                    == com.internlink.core.shared
                                                                            .enums.UserRole.LECTURER
                                                            && placementRepository
                                                                    .findByStudentFoundApplicationId(
                                                                            app.getId())
                                                                    .map(
                                                                            p ->
                                                                                    p.getLecturer()
                                                                                            .getId()
                                                                                            .equals(
                                                                                                    actor
                                                                                                            .getId()))
                                                                    .orElse(false))
                            .orElse(false);
            case AGREEMENT ->
                    agreementRepository
                            .findById(contextId)
                            .map(
                                    agreement ->
                                            ResourceAuthorization.canReadAgreement(
                                                    actor, agreement))
                            .orElse(false);
            case PLACEMENT ->
                    placementRepository
                            .findById(contextId)
                            .map(
                                    placement ->
                                            ResourceAuthorization.canReadPlacement(
                                                    actor, placement))
                            .orElse(false);
            case TASK ->
                    taskRepository
                            .findById(contextId)
                            .map(
                                    task ->
                                            ResourceAuthorization.canReadPlacement(
                                                    actor, task.getPlacement()))
                            .orElse(false);
            case LOGBOOK ->
                    logbookRepository
                            .findById(contextId)
                            .map(
                                    logbook ->
                                            ResourceAuthorization.canReadPlacement(
                                                    actor, logbook.getPlacement()))
                            .orElse(false);
            case EVALUATION ->
                    rubricRepository
                            .findById(contextId)
                            .map(
                                    rubric ->
                                            ResourceAuthorization.canReadPlacement(
                                                    actor, rubric.getPlacement()))
                            .orElse(false);
            case CASE ->
                    caseRepository
                            .findById(contextId)
                            .map(
                                    caseFile ->
                                            ResourceAuthorization.canReadPlacement(
                                                    actor, caseFile.getPlacement()))
                            .orElse(false);
        };
    }
}
