package com.internlink.core.application.recruitment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.internlink.core.application.recruitment.JobPositionAccessPolicy;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.recruitment.ExternalApplyClick;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.JpaExternalApplyClickRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobPositionRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.shared.enums.ApplicationMethod;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class ExternalApplyServiceTest {
    @Mock JpaJobPositionRepository jobs;
    @Mock JpaExternalApplyClickRepository clicks;
    @Mock JpaStudentFoundApplicationRepository acceptanceForms;
    @Mock JobPositionAccessPolicy access;
    @InjectMocks ExternalApplyService service;

    @Test
    void studentClickOnPublishedExternalJobIsCounted() {
        User student = user(UserRole.STUDENT);
        JobPosition job = job(ApplicationMethod.EXTERNAL, JobStatus.APPROVED);
        when(access.currentActor()).thenReturn(student);
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        service.recordClick(job.getId());

        verify(clicks).recordClick(student.getId(), job.getId());
    }

    @Test
    void inAppJobHasNoExternalChannelToClick() {
        JobPosition job = job(ApplicationMethod.IN_APP, JobStatus.APPROVED);
        when(access.currentActor()).thenReturn(user(UserRole.STUDENT));
        when(jobs.findById(job.getId())).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.recordClick(job.getId())).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(clicks);
    }

    @Test
    void myJobsListsExternalJobsWithAddressFallbackToCompanyOffice() {
        User student = user(UserRole.STUDENT);
        JobPosition job = job(ApplicationMethod.EXTERNAL, JobStatus.CLOSED);
        job.setLocation(" ");
        job.setExternalApplyEmail("hr@unit.vn");
        OffsetDateTime at = OffsetDateTime.now();
        ExternalApplyClick click = ExternalApplyClick.builder().student(student).job(job).lastClickedAt(at).build();
        when(access.currentActor()).thenReturn(student);
        when(clicks.findByStudentIdWithJob(student.getId())).thenReturn(List.of(click));

        var options = service.myJobs();

        assertThat(options).singleElement().satisfies(o -> {
            assertThat(o.jobId()).isEqualTo(job.getId());
            assertThat(o.companyName()).isEqualTo("UNIT");
            assertThat(o.hostAddress()).isEqualTo("Văn phòng Cần Thơ");
            assertThat(o.externalApplyEmail()).isEqualTo("hr@unit.vn");
            assertThat(o.lastClickedAt()).isEqualTo(at);
        });
    }

    private static User user(UserRole role) {
        User user = User.builder().role(role).build();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static JobPosition job(ApplicationMethod method, JobStatus status) {
        Company company = Company.builder().companyName("UNIT").officeAddress("Văn phòng Cần Thơ").build();
        company.setId(UUID.randomUUID());
        JobPosition job = JobPosition.builder().company(company).title("TTS Java").status(status)
                .applicationMethod(method).build();
        job.setId(UUID.randomUUID());
        return job;
    }
}
