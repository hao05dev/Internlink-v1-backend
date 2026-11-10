package com.internlink.core.application.organization;

import com.internlink.core.shared.enums.TermStatus;
import com.internlink.core.presentation.organization.dto.request.InternshipTermRequest;
import com.internlink.core.presentation.organization.dto.response.InternshipTermResponse;

import java.util.List;
import java.util.UUID;

public interface InternshipTermService {
    List<InternshipTermResponse> getAllTerms();
    List<InternshipTermResponse> getActiveTerms();
    InternshipTermResponse getTermById(UUID id);
    InternshipTermResponse createTerm(InternshipTermRequest request);
    InternshipTermResponse updateTermStatus(UUID termId, TermStatus newStatus);
}
