package com.internlink.core.application.recruitment.impl;

import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.JobSkill;
import com.internlink.core.infrastructure.persistence.jpa.JpaJobSkillRepository;
import com.internlink.core.presentation.recruitment.dto.response.JobPositionResponse;
import com.internlink.core.presentation.recruitment.dto.response.JobSkillResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.RequirementType;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class JobPositionMapper {
    private final com.internlink.core.application.company.impl.CompanyPublicMapper companies;
    private final JobLocationResolver locations;

    private final JpaJobSkillRepository jobSkillRepository;

    public JobPositionResponse toResponse(JobPosition entity) {
        List<JobSkill> jobSkills = jobSkillRepository.findByIdJobId(entity.getId());
        List<JobSkillResponse> skillResponses =
                jobSkills.stream()
                        .map(
                                js ->
                                        JobSkillResponse.builder()
                                                .skillId(js.getSkill().getId())
                                                .skillName(js.getSkill().getSkillName())
                                                .requirementType(js.getRequirementType())
                                                .requiredLevel(js.getRequiredLevel())
                                                .weight(js.getWeight())
                                                .build())
                        .toList();

        List<String> mandatory =
                jobSkills.stream()
                        .filter(js -> js.getRequirementType() == RequirementType.MANDATORY)
                        .map(js -> js.getSkill().getId())
                        .toList();

        List<String> optional =
                jobSkills.stream()
                        .filter(js -> js.getRequirementType() == RequirementType.OPTIONAL)
                        .map(js -> js.getSkill().getId())
                        .toList();

        boolean isExpired = entity.getApplicationDeadline() != null && java.time.OffsetDateTime.now().isAfter(entity.getApplicationDeadline());
        boolean isAccepting = entity.getStatus() == JobStatus.APPROVED 
                && entity.getClosedEarlyAt() == null 
                && !isExpired;

        return JobPositionResponse.builder()
                .id(entity.getId())
                .companyId(entity.getCompany().getId())
                .companyName(entity.getCompany().getCompanyName())
                .company(companies.summary(entity.getCompany()))
                .useCompanyLocation(entity.isUseCompanyLocation())
                .mapEmbedUrl(locations.resolve(entity).mapEmbedUrl())
                .mapsUrl(locations.resolve(entity).mapsUrl())
                .termId(entity.getTerm() != null ? entity.getTerm().getId() : null)
                .termName(entity.getTerm() != null ? entity.getTerm().getTermName() : null)
                .termStatus(
                        entity.getTerm() != null && entity.getTerm().getStatus() != null
                                ? entity.getTerm().getStatus().name()
                                : null)
                .jobType(entity.getJobType())
                .title(entity.getTitle())
                .workFormat(entity.getWorkFormat())
                .location(locations.resolve(entity).address())
                .vacancies(entity.getVacancies())
                .description(entity.getDescription())
                .bannerUrl(entity.getBannerUrl())
                .attachmentUrls(entity.getAttachmentUrls())
                .targetProgramCodes(entity.getTargetProgramCodes())
                .targetLearningOutcomes(entity.getTargetLearningOutcomes())
                .benefits(entity.getBenefits())
                .stipendAmount(entity.getStipendAmount())
                .status(entity.getStatus())
                .facultyFeedback(entity.getFacultyFeedback())
                .approvedByUserId(
                        entity.getApprovedBy() != null ? entity.getApprovedBy().getId() : null)
                .approvedAt(entity.getApprovedAt())
                .createdAt(entity.getCreatedAt())
                .applicationDeadline(entity.getApplicationDeadline())
                .closedEarlyAt(entity.getClosedEarlyAt())
                .isExpired(isExpired)
                .isAcceptingApplications(isAccepting)
                .skills(skillResponses)
                .mandatorySkillIds(mandatory)
                .optionalSkillIds(optional)
                .build();
    }
}
