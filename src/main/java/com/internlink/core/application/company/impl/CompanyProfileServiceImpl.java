package com.internlink.core.application.company.impl;

import com.internlink.core.application.company.CompanyProfileService;
import com.internlink.core.application.company.CompanyAccessPolicy;
import com.internlink.core.application.system.upload.MediaReferencePolicy;
import com.internlink.core.infrastructure.persistence.jpa.JpaCompanyRepository;
import com.internlink.core.presentation.company.dto.request.CompanyProfileRequest;
import com.internlink.core.presentation.company.dto.response.CompanyPublicResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyProfileServiceImpl implements CompanyProfileService {
    private final CompanyAccessPolicy access;
    private final JpaCompanyRepository companies;
    private final CompanyProfileMapper mapper;
    private final CompanyPublicMapper publicMapper;
    private final MediaReferencePolicy media;
    private final com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository users;

    @Override
    @Transactional(readOnly = true)
    public CompanyPublicResponse getOwnProfile() {
        return publicMapper.toResponse(access.ownCompany());
    }

    @Override
    @Transactional
    public CompanyPublicResponse updateOwnProfile(CompanyProfileRequest request) {
        var company = access.ownCompany();
        media.company(company.getId(), request.getLogoUrl(), request.getCoverImageUrl());
        mapper.apply(company, request);
        return publicMapper.toResponse(companies.save(company));
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<com.internlink.core.presentation.company.dto.response.CompanyMentorResponse> getOwnMentors() {
        var company = access.ownCompany();
        return users.findByCompanyIdAndRole(company.getId(), com.internlink.core.shared.enums.UserRole.COMPANY_MENTOR).stream()
            .filter(user -> !Boolean.FALSE.equals(user.getIsActive()))
            .map(user -> new com.internlink.core.presentation.company.dto.response.CompanyMentorResponse(
                user.getId(), user.getFullName(), user.getEmail()))
            .sorted(java.util.Comparator.comparing(
                com.internlink.core.presentation.company.dto.response.CompanyMentorResponse::fullName,
                java.util.Comparator.nullsLast(String::compareTo)))
            .toList();
    }
}
