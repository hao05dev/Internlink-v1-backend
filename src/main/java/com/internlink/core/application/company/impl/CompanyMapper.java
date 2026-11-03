package com.internlink.core.application.company.impl;

import com.internlink.core.domain.company.Company;
import com.internlink.core.presentation.company.dto.response.CompanyResponse;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {
    public CompanyResponse mapToResponse(Company entity) {
        return CompanyResponse.builder()
            .id(entity.getId())
            .companyName(entity.getCompanyName())
            .taxCode(entity.getTaxCode())
            .industry(entity.getIndustry())
            .website(entity.getWebsite())
            .logoUrl(entity.getLogoUrl())
            .coverImageUrl(entity.getCoverImageUrl())
            .address(entity.getAddress())
            .verificationStatus(entity.getVerificationStatus())
            .verificationDetail(entity.getVerificationDetail())
            .verifiedByUserId(entity.getVerifiedBy() != null ? entity.getVerifiedBy().getId() : null)
            .verifiedAt(entity.getVerifiedAt())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
