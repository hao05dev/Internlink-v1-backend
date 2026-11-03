package com.internlink.core.application.placement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import com.internlink.core.application.exception_case.impl.InternshipCaseServiceImpl;
import com.internlink.core.application.placement.impl.InternshipPlacementMapper;
import com.internlink.core.application.placement.impl.InternshipPlacementServiceImpl;
import com.internlink.core.application.placement.impl.PlacementNotificationHelper;
import com.internlink.core.application.placement.impl.PlacementTransitionValidator;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.LearningAgreement;
import com.internlink.core.infrastructure.persistence.jpa.JpaFinalResultRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipCaseRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaLearningAgreementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.exception_case.dto.request.InternshipCaseRequest;
import com.internlink.core.shared.api.PaginatedResponseMapper;
import com.internlink.core.shared.enums.AgreementStatus;
import com.internlink.core.shared.enums.CaseSeverity;
import com.internlink.core.shared.enums.CaseStatus;
import com.internlink.core.shared.enums.CaseType;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.SecurityGuard;

@ExtendWith(MockitoExtension.class)
class CorePlacementServicesTest {

    @Mock
    private JpaStudentProfileRepository profileRepository;

    @Mock
    private JpaFinalResultRepository finalResultRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private JpaInternshipPlacementRepository placementRepository;

    @Mock
    private JpaLearningAgreementRepository agreementRepository;

    @Mock
    private JpaUserRepository userRepository;

    @Mock
    private JpaInternshipTermRepository termRepository;

    @Mock
    private SecurityGuard securityGuard;

    @Mock
    private JpaInternshipCaseRepository caseRepository;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private User user(UserRole role) {
        User value =
                User.builder()
                        .email(UUID.randomUUID() + "@example.com")
                        .passwordHash("hash")
                        .fullName("User")
                        .role(role)
                        .build();
        value.setId(UUID.randomUUID());
        return value;
    }

    @Test
    void placementActivationRequiresApprovedAgreement() {
        InternshipPlacementMapper placementMapper =
                new InternshipPlacementMapper(profileRepository);
        PlacementTransitionValidator transitionValidator =
                new PlacementTransitionValidator(finalResultRepository, null);
        PlacementNotificationHelper notificationHelper =
                new PlacementNotificationHelper(auditLogService, notificationService);
        PlacementEligibilityService placementEligibility =
                new PlacementEligibilityService(placementRepository, null, null);
        InternshipPlacementServiceImpl service =
                new InternshipPlacementServiceImpl(
                        placementRepository,
                        agreementRepository,
                        userRepository,
                        termRepository,
                        securityGuard,
                        placementMapper,
                        transitionValidator,
                        notificationHelper,
                        placementEligibility,
                        new PaginatedResponseMapper(),
                        null);
        LearningAgreement agreement =
                LearningAgreement.builder().status(AgreementStatus.DRAFT).build();
        agreement.setId(UUID.randomUUID());
        when(agreementRepository.findById(agreement.getId())).thenReturn(Optional.of(agreement));

        assertThatThrownBy(
                        () ->
                                service.activatePlacementFromAgreement(
                                         agreement.getId(), UUID.randomUUID()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void reportedCaseStartsOpenAndKeepsReporter() {
        InternshipCaseServiceImpl service =
                new InternshipCaseServiceImpl(caseRepository, placementRepository, userRepository);
        User reporter = user(UserRole.STUDENT);
        Company company =
                Company.builder().companyName("Acme").taxCode("123").address(Map.of()).build();
        company.setId(UUID.randomUUID());
        InternshipPlacement placement =
                InternshipPlacement.builder().student(reporter).company(company).build();
        placement.setId(UUID.randomUUID());
        when(placementRepository.findById(placement.getId())).thenReturn(Optional.of(placement));
        when(userRepository.findById(reporter.getId())).thenReturn(Optional.of(reporter));
        when(caseRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        InternshipCaseRequest request =
                InternshipCaseRequest.builder()
                        .placementId(placement.getId())
                        .caseType(CaseType.INCIDENT)
                        .severity(CaseSeverity.HIGH)
                        .summary("Incident")
                        .build();

        var response = service.reportCase(reporter.getId(), request);

        assertThat(response.getStatus()).isEqualTo(CaseStatus.OPEN);
        assertThat(response.getReportedByUserId()).isEqualTo(reporter.getId());
    }
}
