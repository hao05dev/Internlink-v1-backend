package com.internlink.core.application.placement;

import com.internlink.core.presentation.placement.dto.request.LearningAgreementRequest;
import com.internlink.core.presentation.placement.dto.response.LearningAgreementResponse;
import com.internlink.core.shared.enums.AgreementStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface LearningAgreementService {
    LearningAgreementResponse getAgreementById(UUID id);
    LearningAgreementResponse getAgreementByOfferId(UUID offerId);
    List<LearningAgreementResponse> getAgreementsByDepartment(UUID departmentId, AgreementStatus status);
    List<LearningAgreementResponse> getAgreementsByStudent(UUID studentId);
    List<LearningAgreementResponse> getAgreementsForOwnCompany();
    LearningAgreementResponse createAgreement(UUID studentId, LearningAgreementRequest request);
    LearningAgreementResponse signAgreement(UUID id, String signerRole, Map<String, Object> signatureData);
    LearningAgreementResponse reviewAgreementByFaculty(UUID id, AgreementStatus status);
}
