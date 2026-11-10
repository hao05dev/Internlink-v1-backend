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

        JobPositionRequest request = jobRequest(company.getId(), term.getId(), department.getId());

        JobPositionResponse response = jobService.createJob(request, blogManager.getId());
        assertThat(response.getStatus()).isEqualTo(JobStatus.APPROVED);
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
