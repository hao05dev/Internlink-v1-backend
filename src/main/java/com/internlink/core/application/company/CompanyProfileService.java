package com.internlink.core.application.company;

import com.internlink.core.presentation.company.dto.request.CompanyProfileRequest;
import com.internlink.core.presentation.company.dto.response.CompanyPublicResponse;

public interface CompanyProfileService {
    CompanyPublicResponse getOwnProfile();
    CompanyPublicResponse updateOwnProfile(CompanyProfileRequest request);
    java.util.List<com.internlink.core.presentation.company.dto.response.CompanyMentorResponse> getOwnMentors();
}
