package com.internlink.core.application.recruitment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.recruitment.PlacementOffer;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.recruitment.dto.request.PlacementOfferRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class PlacementOfferWorkflowTest extends RecruitmentTestFixture {

    @Test
    void studentCannotReadAnotherStudentsOffer() {
        JobApplication application = application(ApplicationStatus.OFFERED);
        PlacementOffer offer = PlacementOffer.builder().application(application).build();
        offer.setId(UUID.randomUUID());
        User stranger = user(UserRole.STUDENT);
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(stranger));
        when(userRepository.findById(stranger.getId())).thenReturn(Optional.of(stranger));

        assertThatThrownBy(() -> offerService.getOfferById(offer.getId()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void createOfferRequiresActiveReviewAndValidDates() {
        JobApplication application = application(ApplicationStatus.SUBMITTED);
        User representative = user(UserRole.COMPANY_REP);
        representative.setCompany(application.getJob().getCompany());
        when(applicationRepository.findById(application.getId()))
                .thenReturn(Optional.of(application));
        when(userRepository.findById(representative.getId()))
                .thenReturn(Optional.of(representative));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(representative));
        when(offerRepository.findByApplicationId(application.getId())).thenReturn(Optional.empty());

        PlacementOfferRequest request = offerRequest(application.getId());
        assertThatThrownBy(() -> offerService.createOffer(request))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void declinedOfferAlsoClosesApplication() {
        JobApplication application = application(ApplicationStatus.OFFERED);
        PlacementOffer offer =
                PlacementOffer.builder()
                        .application(application)
                        .startDate(LocalDate.now().plusDays(1))
                        .endDate(LocalDate.now().plusMonths(3))
                        .expiresAt(OffsetDateTime.now().plusDays(1))
                        .status(OfferStatus.SENT)
                        .termsSnapshot(Map.of())
                        .build();
        offer.setId(UUID.randomUUID());
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(securityGuard.currentUser())
                .thenReturn(CustomUserDetail.create(application.getStudent()));

        assertThat(offerService.respondToOffer(offer.getId(), OfferStatus.DECLINED).getStatus())
                .isEqualTo(OfferStatus.DECLINED);
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        verify(applicationRepository).save(application);
    }

    @Test
    void declinedOfferReturnsJobSlot() {
        RedisJobSlotService slots = mock(RedisJobSlotService.class);
        PlacementOfferServiceImpl service = new PlacementOfferServiceImpl(
                offerRepository, applicationRepository, jobRepository, studentRosterRepository,
                userRepository, placementEligibility, securityGuard, auditLogService,
                notificationService, slots);
        JobApplication application = application(ApplicationStatus.OFFERED);
        PlacementOffer offer =
                PlacementOffer.builder()
                        .application(application)
                        .startDate(LocalDate.now().plusDays(1))
                        .endDate(LocalDate.now().plusMonths(3))
                        .expiresAt(OffsetDateTime.now().plusDays(1))
                        .status(OfferStatus.SENT)
                        .termsSnapshot(Map.of())
                        .build();
        offer.setId(UUID.randomUUID());
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(securityGuard.currentUser())
                .thenReturn(CustomUserDetail.create(application.getStudent()));

        service.respondToOffer(offer.getId(), OfferStatus.DECLINED);

        verify(slots).releaseSlot(application.getJob().getId());
    }

    @Test
    void studentCannotSetAdministrativeOfferStatus() {
        PlacementOffer offer =
                PlacementOffer.builder()
                        .application(application(ApplicationStatus.OFFERED))
                        .expiresAt(OffsetDateTime.now().plusDays(1))
                        .status(OfferStatus.SENT)
                        .build();
        offer.setId(UUID.randomUUID());
        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(securityGuard.currentUser())
                .thenReturn(CustomUserDetail.create(offer.getApplication().getStudent()));

        assertThatThrownBy(() -> offerService.respondToOffer(offer.getId(), OfferStatus.WITHDRAWN))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInternshipOffer_AutomaticallyDeclinesOtherOffersAndWithdrawsApplications() {
        JobApplication application = application(ApplicationStatus.OFFERED);
        User student = application.getStudent();
        UUID termId = application.getJob().getTerm().getId();

        PlacementOffer offer = PlacementOffer.builder()
                .application(application)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusMonths(3))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .status(OfferStatus.SENT)
                .termsSnapshot(Map.of())
                .build();
        offer.setId(UUID.randomUUID());

        JobApplication otherApp = application(ApplicationStatus.REVIEWING);
        otherApp.setStudent(student);
        PlacementOffer otherOffer = PlacementOffer.builder()
                .application(otherApp)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusMonths(3))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .status(OfferStatus.SENT)
                .termsSnapshot(Map.of())
                .build();
        otherOffer.setId(UUID.randomUUID());

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(student));
        when(studentRosterRepository.findByTermIdAndClaimedUserIdForUpdate(termId, student.getId()))
                .thenReturn(Optional.of(mock(StudentRoster.class)));
        when(placementRepository.findByTermIdAndStudentId(termId, student.getId()))
                .thenReturn(List.of());
        when(offerRepository.findAll()).thenReturn(List.of(offer, otherOffer));
        when(offerRepository.findOtherSentOffersByStudentAndTerm(student.getId(), termId, offer.getId()))
                .thenReturn(List.of(otherOffer));
        when(applicationRepository.findOtherActiveApplicationsByStudentAndTerm(student.getId(), termId, application.getId()))
                .thenReturn(List.of(otherApp));

        var response = offerService.respondToOffer(offer.getId(), OfferStatus.ACCEPTED);

        assertThat(response.getStatus()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(otherOffer.getStatus()).isEqualTo(OfferStatus.DECLINED);
        assertThat(otherApp.getStatus()).isEqualTo(ApplicationStatus.WITHDRAWN);
        verify(offerRepository).save(otherOffer);
        verify(applicationRepository).save(otherApp);
    }

    @Test
    void acceptInternshipOffer_FailsIfStudentAlreadyHasLivePlacement() {
        JobApplication application = application(ApplicationStatus.OFFERED);
        User student = application.getStudent();
        UUID termId = application.getJob().getTerm().getId();

        PlacementOffer offer = PlacementOffer.builder()
                .application(application)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusMonths(3))
                .expiresAt(OffsetDateTime.now().plusDays(1))
                .status(OfferStatus.SENT)
                .termsSnapshot(Map.of())
                .build();
        offer.setId(UUID.randomUUID());

        InternshipPlacement livePlacement = InternshipPlacement.builder()
                .student(student)
                .term(application.getJob().getTerm())
                .status(PlacementStatus.ACTIVE)
                .build();

        when(offerRepository.findById(offer.getId())).thenReturn(Optional.of(offer));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(student));
        when(studentRosterRepository.findByTermIdAndClaimedUserIdForUpdate(termId, student.getId()))
                .thenReturn(Optional.of(mock(StudentRoster.class)));
        when(placementRepository.findByTermIdAndStudentId(termId, student.getId()))
                .thenReturn(List.of(livePlacement));

        assertThatThrownBy(() -> offerService.respondToOffer(offer.getId(), OfferStatus.ACCEPTED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã có một vị trí thực tập đang hoạt động");
    }
}
