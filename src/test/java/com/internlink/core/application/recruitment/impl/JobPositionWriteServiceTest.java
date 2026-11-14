package com.internlink.core.application.recruitment.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.recruitment.dto.request.JobPositionRequest;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ForbiddenException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

class JobPositionWriteServiceTest extends RecruitmentTestFixture {
    @Test
    void companyCannotSelfApproveJobThroughCreatePayload() {
        Department department = department();
        InternshipTerm term = term(department);
        Company company = company();
        User representative = user(UserRole.COMPANY_REP);
        representative.setCompany(company);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        when(userRepository.findById(representative.getId()))
                .thenReturn(Optional.of(representative));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(representative));

        JobPositionRequest request = jobRequest(company.getId(), term.getId(), department.getId());
        request.setStatus(JobStatus.APPROVED);

        assertThat(jobService.createJob(request, representative.getId()).getStatus())
                .isEqualTo(JobStatus.DRAFT);
    }

    @Test
    void blogManagerCanCreateJobForPartnerCompanyWithAutoApproval() {
        Department department = department();
        InternshipTerm term = term(department);
        Company company = company();
        User blogManager = user(UserRole.SCHOOL_OFFICE);
        when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        when(userRepository.findById(blogManager.getId()))
                .thenReturn(Optional.of(blogManager));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(blogManager));
        User representative = user(UserRole.COMPANY_REP);
        representative.setCompany(company);
        when(userRepository.findByCompanyIdAndRole(company.getId(), UserRole.COMPANY_REP))
                .thenReturn(List.of(representative));

        JobPositionRequest request = jobRequest(company.getId(), term.getId(), department.getId());

        JobPositionResponse response = jobService.createJob(request, blogManager.getId());
        assertThat(response.getStatus()).isEqualTo(JobStatus.APPROVED);
        assertThat(response.getApplicationMethod()).isEqualTo(ApplicationMethod.IN_APP);
    }

    @Test
    void schoolOfficeRepostForCompanyWithoutAccountDefaultsToExternalApply() {
        Department department = department();
        InternshipTerm term = term(department);
        Company company = company();
        User schoolOffice = schoolOfficeActingOn(company, term);

        JobPositionRequest request = jobRequest(company.getId(), term.getId(), department.getId());
        request.setExternalApplyEmail("recruit@unit.com.vn");
        request.setExternalApplySubject("[UNIT-Internship] Họ và Tên_PQA");
        request.setExternalApplyUrl("  ");

        JobPositionResponse response = jobService.createJob(request, schoolOffice.getId());
        assertThat(response.getApplicationMethod()).isEqualTo(ApplicationMethod.EXTERNAL);
        assertThat(response.getExternalApplyEmail()).isEqualTo("recruit@unit.com.vn");
        assertThat(response.getExternalApplySubject()).isEqualTo("[UNIT-Internship] Họ và Tên_PQA");
        assertThat(response.getExternalApplyUrl()).isNull();
    }

    @Test
    void externalApplyNeedsAtLeastOneChannel() {
        Department department = department();
        InternshipTerm term = term(department);
        Company company = company();
        User schoolOffice = schoolOfficeActingOn(company, term);

        JobPositionRequest request = jobRequest(company.getId(), term.getId(), department.getId());
        assertThatThrownBy(() -> jobService.createJob(request, schoolOffice.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("link, email hoặc hướng dẫn");

        request.setExternalApplyUrl("forms.gle/abc");
        assertThatThrownBy(() -> jobService.createJob(request, schoolOffice.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("http");

        request.setExternalApplyUrl(null);
        request.setExternalApplyNote("Nộp hồ sơ trực tiếp tại công ty");
        assertThat(jobService.createJob(request, schoolOffice.getId()).getExternalApplyNote())
                .isEqualTo("Nộp hồ sơ trực tiếp tại công ty");
    }

    @Test
    void inAppJobWithApplicationsCannotSwitchToExternal() {
        Department department = department();
        InternshipTerm term = term(department);
        Company company = company();
        User schoolOffice = schoolOfficeActingOn(company, term);
        JobPosition existing = job(company, term, department, JobStatus.APPROVED);
        when(jobRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(applicationRepository.countActiveApplicationsByJob(existing.getId())).thenReturn(3L);

        JobPositionRequest request = jobRequest(company.getId(), term.getId(), department.getId());
        request.setApplicationMethod(ApplicationMethod.EXTERNAL);
        request.setExternalApplyEmail("hr@example.com");

        assertThatThrownBy(() -> jobService.updateJob(existing.getId(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã có hồ sơ");
    }

    private User schoolOfficeActingOn(Company company, InternshipTerm term) {
        User schoolOffice = user(UserRole.SCHOOL_OFFICE);
        lenient().when(companyRepository.findById(company.getId())).thenReturn(Optional.of(company));
        lenient().when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        when(userRepository.findById(schoolOffice.getId())).thenReturn(Optional.of(schoolOffice));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(schoolOffice));
        return schoolOffice;
    }


    @Test
    void representativeCannotCreateJobForAnotherCompany() {
        Department department = department();
        InternshipTerm term = term(department);
        Company targetCompany = company();
        User representative = user(UserRole.COMPANY_REP);
        representative.setCompany(company());
        when(companyRepository.findById(targetCompany.getId()))
                .thenReturn(Optional.of(targetCompany));
        when(termRepository.findById(term.getId())).thenReturn(Optional.of(term));
        when(userRepository.findById(representative.getId()))
                .thenReturn(Optional.of(representative));
        when(securityGuard.currentUser()).thenReturn(CustomUserDetail.create(representative));

        assertThatThrownBy(
                        () ->
                                jobService.createJob(
                                        jobRequest(
                                                targetCompany.getId(),
                                                term.getId(),
                                                department.getId()),
                                        representative.getId()))
                .isInstanceOf(ForbiddenException.class);
        verify(jobRepository, never()).save(any());
    }
}
