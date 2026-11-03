package com.internlink.core.application.organization;

import com.internlink.core.presentation.organization.dto.request.StudentRosterImportItem;
import com.internlink.core.presentation.organization.dto.response.BatchProvisionResponse;
import com.internlink.core.presentation.organization.dto.response.StudentRosterResponse;

import java.util.List;
import java.util.UUID;

public interface StudentRosterService {
    List<StudentRosterResponse> getRostersByTerm(UUID termId);
    List<StudentRosterResponse> importRosterList(UUID termId, List<StudentRosterImportItem> items);
    StudentRosterResponse provisionAccount(UUID rosterId);
    BatchProvisionResponse batchProvisionEligibleAccounts(UUID termId, List<UUID> rosterIds);
    StudentRosterResponse getMySupervisionInfo(UUID studentUserId, String email);
}
