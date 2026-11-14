package com.internlink.core.application.recruitment.impl;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobApplicationRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.recruitment.dto.request.JobPositionRequest;
import com.internlink.core.shared.enums.ApplicationMethod;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.ResourceAuthorization;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Quyết định và kiểm tra kênh ứng tuyển của tin (IN_APP / EXTERNAL).
 * Tin VP Trường đăng lại cho DN chưa có tài khoản đại diện mặc định EXTERNAL,
 * vì không ai phía DN xử lý đơn nộp trong hệ thống.
 */
@Component
@RequiredArgsConstructor
public class ExternalApplyPolicy {
    private final JpaUserRepository users;
    private final JpaJobApplicationRepository applications;

    public void applyOnCreate(JobPosition job, JobPositionRequest request, User actor) {
        ApplicationMethod method = request.getApplicationMethod() != null
                ? request.getApplicationMethod()
                : defaultMethod(job.getCompany().getId(), actor);
        apply(job, request, method);
    }

    public void applyOnUpdate(JobPosition job, JobPositionRequest request) {
        ApplicationMethod method = request.getApplicationMethod() != null
                ? request.getApplicationMethod()
                : job.getApplicationMethod();
        if (method == ApplicationMethod.EXTERNAL && !job.isExternalApply()
                && applications.countActiveApplicationsByJob(job.getId()) > 0)
            throw new BadRequestException(
                    "Tin đã có hồ sơ nộp trong hệ thống, không thể chuyển sang ứng tuyển ngoài");
        apply(job, request, method);
    }

    private ApplicationMethod defaultMethod(UUID companyId, User actor) {
        boolean companyHasRep = users.findByCompanyIdAndRole(companyId, UserRole.COMPANY_REP).stream()
                .anyMatch(u -> !Boolean.FALSE.equals(u.getIsActive()));
        return ResourceAuthorization.actsForSchool(actor) && !companyHasRep
                ? ApplicationMethod.EXTERNAL
                : ApplicationMethod.IN_APP;
    }

    private static void apply(JobPosition job, JobPositionRequest request, ApplicationMethod method) {
        job.setApplicationMethod(method);
        if (method == ApplicationMethod.IN_APP) {
            job.setExternalApplyUrl(null);
            job.setExternalApplyEmail(null);
            job.setExternalApplySubject(null);
            job.setExternalApplyNote(null);
            return;
        }
        String url = blankToNull(request.getExternalApplyUrl());
        String email = blankToNull(request.getExternalApplyEmail());
        String note = blankToNull(request.getExternalApplyNote());
        if (url == null && email == null && note == null)
            throw new BadRequestException(
                    "Doanh nghiệp nhận hồ sơ ngoài hệ thống: cần nhập link, email hoặc hướng dẫn nộp hồ sơ");
        if (url != null && !url.matches("(?i)^https?://\\S+$"))
            throw new BadRequestException("Link ứng tuyển phải bắt đầu bằng http:// hoặc https://");
        job.setExternalApplyUrl(url);
        job.setExternalApplyEmail(email);
        job.setExternalApplySubject(email != null ? blankToNull(request.getExternalApplySubject()) : null);
        job.setExternalApplyNote(note);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
