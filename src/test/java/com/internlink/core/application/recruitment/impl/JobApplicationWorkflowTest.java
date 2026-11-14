package com.internlink.core.application.recruitment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.application.system.NotificationService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.AcademicProgram;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.organization.StudentRoster;
import com.internlink.core.domain.recruitment.JobApplication;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.PlacementOffer;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentProfileRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentRosterRepository;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.recruitment.dto.request.JobApplicationRequest;
import com.internlink.core.presentation.recruitment.dto.request.JobPositionRequest;
import com.internlink.core.presentation.recruitment.dto.request.PlacementOfferRequest;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;
import com.internlink.core.shared.security.SecurityGuard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class JobApplicationWorkflowTest extends RecruitmentTestFixture {
    @Test
    void externalJobRejectsInAppApplicationWithDedicatedCode() {
        User student = user(UserRole.STUDENT);
        JobPosition job = job(company(), term(department()), department(), JobStatus.APPROVED);
        job.setApplicationMethod(com.internlink.core.shared.enums.ApplicationMethod.EXTERNAL);
        job.setExternalApplyEmail("hiring@example.com");
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        JobApplicationRequest request =
                JobApplicationRequest.builder()
                        .jobId(job.getId())
                        .submittedCvDocumentId(UUID.randomUUID())
                        .build();

        assertThatThrownBy(() -> applicationService.applyJob(student.getId(), request))
                .isInstanceOf(com.internlink.core.shared.error.AppException.class)
                .extracting("errorCode")
                .isEqualTo(com.internlink.core.shared.error.ErrorCode.JOB_APPLY_EXTERNAL_ONLY);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applyJobRejectsCvOwnedByAnotherUser() {
        User student = user(UserRole.STUDENT);
        JobPosition job = job(company(), term(department()), department(), JobStatus.APPROVED);
        job.getTerm().setStatus(TermStatus.APPLICATION_OPEN);
        job.getTerm().setApplicationDeadline(OffsetDateTime.now().plusDays(2));
        AcademicProgram program = AcademicProgram.builder().code("CNTT").name("CNTT").build();
        StudentProfile profile = StudentProfile.builder().user(student).program(program).build();
        StudentRoster roster =
                StudentRoster.builder().build();
        Document foreignCv =
                Document.builder()
                        .owner(user(UserRole.STUDENT))
                        .documentType(DocumentType.CV)
                        .build();
        foreignCv.setId(UUID.randomUUID());
        when(userRepository.findById(student.getId())).thenReturn(Optional.of(student));
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(studentProfileRepository.findByUserId(student.getId()))
                .thenReturn(Optional.of(profile));
        when(studentRosterRepository.findByTermIdAndClaimedUserId(
                        job.getTerm().getId(), student.getId()))
                .thenReturn(Optional.of(roster));
        when(applicationRepository.existsByJobIdAndStudentId(job.getId(), student.getId()))
                .thenReturn(false);
        when(documentRepository.findById(foreignCv.getId())).thenReturn(Optional.of(foreignCv));

        JobApplicationRequest request =
                JobApplicationRequest.builder()
                        .jobId(job.getId())
                        .submittedCvDocumentId(foreignCv.getId())
                        .build();

        assertThatThrownBy(() -> applicationService.applyJob(student.getId(), request))
                .isInstanceOf(BadRequestException.class);
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void applicationStatusRejectsInvalidJump() {
        JobApplication application = application(ApplicationStatus.SUBMITTED);
        User representative = user(UserRole.COMPANY_REP);
        representative.setCompany(application.getJob().getCompany());
        when(applicationRepository.findById(application.getId()))
                .thenReturn(Optional.of(application));
        when(userRepository.findById(representative.getId()))
                .thenReturn(Optional.of(representative));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(representative));

        assertThatThrownBy(
                        () ->
                                applicationService.updateApplicationStatus(
                                        application.getId(), ApplicationStatus.OFFERED))
                .isInstanceOf(BadRequestException.class);
    }

}
