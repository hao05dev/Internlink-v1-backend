package com.internlink.core.application.ai_matching;

import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.TermStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationEligibilityServiceTest {

    @Mock
    private JpaStudentRosterRepository rosterRepository;
    @Mock
    private JpaInternshipPlacementRepository placementRepository;
    @Mock
    private JpaJobApplicationRepository applicationRepository;
    @Mock
    private JpaPlacementOfferRepository offerRepository;
    @Mock
    private JpaStudentProfileRepository studentProfileRepository;
    @Mock
    private JpaInternshipTermRepository termRepository;

    @InjectMocks
    private RecommendationEligibilityService eligibilityService;

    private UUID studentId;
    private UUID termId;
    private InternshipTerm term;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        termId = UUID.randomUUID();
        term = InternshipTerm.builder().termName("HK1 2026-2027").status(TermStatus.ACTIVE).build();
        term.setId(termId);
    }

    @Test
    void studentTermEligibility_failsWhenTermClosed() {
        term.setStatus(TermStatus.CLOSED);
        when(termRepository.findById(termId)).thenReturn(Optional.of(term));

        EligibilityResult result = eligibilityService.checkStudentTermEligibility(studentId, termId);
        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("TERM_CLOSED");
    }

    @Test
    void studentTermEligibility_failsWhenNotInRoster() {
        when(termRepository.findById(termId)).thenReturn(Optional.of(term));
        when(rosterRepository.findByTermIdAndClaimedUserId(termId, studentId)).thenReturn(Optional.empty());

        EligibilityResult result = eligibilityService.checkStudentTermEligibility(studentId, termId);
        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("NOT_IN_ROSTER");
    }

    @Test
    void studentTermEligibility_succeedsWhenValid() {
        StudentRoster roster = StudentRoster.builder().build();
        when(termRepository.findById(termId)).thenReturn(Optional.of(term));
        when(rosterRepository.findByTermIdAndClaimedUserId(termId, studentId)).thenReturn(Optional.of(roster));
        when(placementRepository.findByTermIdAndStudentId(termId, studentId)).thenReturn(List.of());

        EligibilityResult result = eligibilityService.checkStudentTermEligibility(studentId, termId);
        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("ELIGIBLE");
    }

    @Test
    void jobEligibility_failsWhenJobNotApproved() {
        JobPosition job = JobPosition.builder().title("Backend Dev").status(JobStatus.PENDING_APPROVAL).build();
        EligibilityResult result = eligibilityService.evaluateJobEligibility(studentId, job, OffsetDateTime.now());
        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("JOB_NOT_APPROVED");
    }

    @Test
    void jobEligibility_failsWhenDeadlinePassed() {
        OffsetDateTime now = OffsetDateTime.now();
        JobPosition job = JobPosition.builder()
            .title("Backend Dev")
            .status(JobStatus.APPROVED)
            .applicationDeadline(now.minusDays(1))
            .build();

        EligibilityResult result = eligibilityService.evaluateJobEligibility(studentId, job, now);
        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("APPLICATION_DEADLINE_PASSED");
    }

    @Test
    void jobEligibility_failsWhenCapacityFull() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID jobId = UUID.randomUUID();
        JobPosition job = JobPosition.builder()
            .title("Backend Dev")
            .status(JobStatus.APPROVED)
            .vacancies(2)
            .build();
        job.setId(jobId);

        when(offerRepository.countReservedPlaces(eq(jobId), any())).thenReturn(2L);

        EligibilityResult result = eligibilityService.evaluateJobEligibility(studentId, job, now);
        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("CAPACITY_FULL");
    }

    @Test
    void jobEligibility_failsWhenDepartmentMismatch() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID jobId = UUID.randomUUID();
        Department itDept = Department.builder().name("CNTT").build();
        itDept.setId(UUID.randomUUID());
        Department econDept = Department.builder().name("Kinh tế").build();
        econDept.setId(UUID.randomUUID());

        JobPosition job = JobPosition.builder()
            .title("Backend Dev")
            .status(JobStatus.APPROVED)
            .department(itDept)
            .vacancies(5)
            .build();
        job.setId(jobId);

        AcademicProgram econProgram = AcademicProgram.builder().department(econDept).build();
        StudentProfile studentProfile = StudentProfile.builder().program(econProgram).build();

        when(offerRepository.countReservedPlaces(eq(jobId), any())).thenReturn(0L);
        when(studentProfileRepository.findByUserId(studentId)).thenReturn(Optional.of(studentProfile));

        EligibilityResult result = eligibilityService.evaluateJobEligibility(studentId, job, now);
        assertThat(result.isEligible()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("DEPARTMENT_MISMATCH");
    }

    @Test
    void jobEligibility_succeedsWhenAllPass() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID jobId = UUID.randomUUID();
        JobPosition job = JobPosition.builder()
            .title("Backend Dev")
            .status(JobStatus.APPROVED)
            .vacancies(5)
            .build();
        job.setId(jobId);

        when(offerRepository.countReservedPlaces(eq(jobId), any())).thenReturn(1L);
        when(studentProfileRepository.findByUserId(studentId)).thenReturn(Optional.empty());
        when(applicationRepository.existsByJobIdAndStudentId(jobId, studentId)).thenReturn(false);

        EligibilityResult result = eligibilityService.evaluateJobEligibility(studentId, job, now);
        assertThat(result.isEligible()).isTrue();
        assertThat(result.reasonCode()).isEqualTo("ELIGIBLE");
    }
}
