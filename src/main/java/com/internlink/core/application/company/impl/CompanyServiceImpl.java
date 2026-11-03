package com.internlink.core.application.company.impl;

import com.internlink.core.application.company.CompanyService;
import com.internlink.core.application.system.AuditLogService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.company.Company;
import com.internlink.core.infrastructure.persistence.jpa.JpaCompanyRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.presentation.company.dto.request.CompanyRequest;
import com.internlink.core.presentation.company.dto.response.CompanyResponse;
import com.internlink.core.shared.enums.VerificationStatus;
import com.internlink.core.shared.enums.UserRole;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final JpaCompanyRepository companyRepository;
    private final JpaUserRepository userRepository;
    private final AuditLogService auditLogService;
    private final SecurityGuard securityGuard;
    private final CompanyMapper mapper;
    private final com.internlink.core.application.company.CompanyAccessPolicy access;
    private final com.internlink.core.application.system.NotificationService notifications;

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAll().stream()
            .map(mapper::mapToResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyResponse> getCompaniesByStatus(VerificationStatus status) {
        return companyRepository.findByVerificationStatus(status).stream()
            .map(mapper::mapToResponse)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(UUID id) {
        access.requireRead(id);
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));
        return mapper.mapToResponse(company);
    }

    @Override
    @Transactional
    public CompanyResponse registerCompany(CompanyRequest request) {
        String cleanTaxCode = request.getTaxCode().trim();
        if (companyRepository.existsByTaxCode(cleanTaxCode)) {
            throw new BadRequestException("Mã số thuế '" + cleanTaxCode + "' đã tồn tại trong hệ thống");
        }

        Company company = Company.builder()
            .companyName(request.getCompanyName().trim())
            .taxCode(cleanTaxCode)
            .industry(request.getIndustry())
            .website(request.getWebsite())
            .address(request.getAddress())
            .verificationStatus(VerificationStatus.PENDING)
            .verificationDetail(Map.of())
            .build();

        Company saved = companyRepository.save(company);
        for (UserRole role : List.of(UserRole.SCHOOL_OFFICE, UserRole.ADMIN)) {
            userRepository.findByRole(role).forEach(user -> notifications.sendNotification(user.getId(), "COMPANY_REGISTERED",
                "Doanh nghiệp mới chờ thẩm định", saved.getCompanyName() + " đã đăng ký tham gia hệ thống.",
                role == UserRole.ADMIN ? "/admin/companies" : "/school-office/companies"));
        }
        return mapper.mapToResponse(saved);
    }

    @Override
    @Transactional
    public CompanyResponse updateCompany(UUID id, CompanyRequest request) {
        access.requireOwner(id);
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));

        String cleanTaxCode = request.getTaxCode().trim();
        companyRepository.findByTaxCode(cleanTaxCode)
            .filter(existing -> !existing.getId().equals(id))
            .ifPresent(existing -> {
                throw new BadRequestException("Mã số thuế '" + cleanTaxCode + "' đã được sử dụng bởi doanh nghiệp khác");
            });

        company.setCompanyName(request.getCompanyName().trim());
        company.setTaxCode(cleanTaxCode);
        company.setIndustry(request.getIndustry());
        company.setWebsite(request.getWebsite());
        company.setAddress(request.getAddress());

        return mapper.mapToResponse(companyRepository.save(company));
    }

    @Override
    @Transactional
    public CompanyResponse verifyCompany(UUID id, VerificationStatus status, Map<String, Object> verificationDetail, UUID verifiedByUserId) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", "id", id));

        User verifier = userRepository.findById(verifiedByUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User", "id", verifiedByUserId));
        ResourceAuthorization.require(verifiedByUserId.equals(securityGuard.currentUser().getId())
            && ResourceAuthorization.actsForSchool(verifier));
        if (status == VerificationStatus.PENDING) {
            throw new BadRequestException("Kết quả thẩm định không thể là PENDING");
        }
        if ((status == VerificationStatus.REJECTED || status == VerificationStatus.NEEDS_REVISION)
            && (verificationDetail == null || verificationDetail.get("note") == null
                || verificationDetail.get("note").toString().isBlank()))
            throw new BadRequestException("Cần ghi chú lý do khi từ chối hoặc yêu cầu bổ sung hồ sơ");

        company.setVerificationStatus(status);
        company.setVerificationDetail(verificationDetail != null ? verificationDetail : Map.of());
        company.setVerifiedBy(verifier);
        company.setVerifiedAt(OffsetDateTime.now());

        Company saved = companyRepository.save(company);
        auditLogService.logAction(verifiedByUserId, "REVIEW_COMPANY", "Company", saved.getId(),
            "SUCCESS", Map.of("status", status.name()), null);
        userRepository.findByCompanyIdAndRole(saved.getId(), UserRole.COMPANY_REP).forEach(user ->
            notifications.sendNotification(user.getId(), "COMPANY_REVIEWED", "Kết quả thẩm định doanh nghiệp",
                "Hồ sơ " + saved.getCompanyName() + " có trạng thái " + status.name() + ". Xem ghi chú trong hệ thống.", "/company/dashboard"));
        return mapper.mapToResponse(saved);
    }

}
