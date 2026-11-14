package com.internlink.core.application.ai_matching.impl;

import com.internlink.core.application.ai_matching.EligibilityResult;
import com.internlink.core.application.ai_matching.RecommendationCandidateService;
import com.internlink.core.application.ai_matching.RecommendationEligibilityService;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.JobSkill;
import com.internlink.core.domain.student.StudentProfile;
import com.internlink.core.infrastructure.integration.ai.AiEmbeddingResult;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.ai_matching.dto.response.AiMatchScoreResponse;
import com.internlink.core.shared.enums.JobStatus;
import com.internlink.core.shared.enums.RequirementType;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class AiMatchScoringHandler {

    private final JpaJobPositionRepository jobPositionRepository;
    private final JpaJobSkillRepository jobSkillRepository;
    private final JpaStudentSkillRepository studentSkillRepository;
    private final JpaStudentProfileRepository studentProfileRepository;
    private final AiServiceClient aiServiceClient;
    private final EmbeddingPreparationService embeddingPreparationService;
    private final AiMatchingMapper mapper;
    private final RecommendationCandidateService candidateService;
    private final RecommendationEligibilityService eligibilityService;

    public AiMatchScoreResponse calculateMatchScoreForJob(UUID studentId, UUID jobId) {
        JobPosition job = jobPositionRepository.findById(jobId)
            .filter(j -> j.getStatus() == JobStatus.APPROVED)
            .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", jobId));

        EligibilityResult eligibility = eligibilityService.evaluateJobEligibility(studentId, job, OffsetDateTime.now());
        List<JobSkill> requirements = jobSkillRepository.findByIdJobId(jobId);
        StudentContext context = studentContext(studentId);
        Map<String, AiEmbeddingResult> embeddings = embeddingPreparationService.prepare(List.of(
            studentDocument(studentId, context), jobDocument(job, requirements)));
        Map<String, Object> student = studentPayload(studentId, context, embeddings.get("student"));
        Map<String, Object> jobPayload = jobPayload(job, requirements, embeddings.get(job.getId().toString()));
        Map<String, Object> result = aiServiceClient.rankJobs(student, List.of(jobPayload)).getFirst();
        return response(job, result, eligibility);
    }

    public List<AiMatchScoreResponse> recommendJobsForStudent(UUID studentId, UUID termId) {
        List<JobPosition> eligibleJobs = candidateService.findEligibleCandidateJobs(studentId, termId);
        if (eligibleJobs.isEmpty()) return List.of();

        Map<UUID, List<JobSkill>> requirements = jobSkillRepository.findByIdJobIdIn(
            eligibleJobs.stream().map(JobPosition::getId).toList()).stream()
            .collect(Collectors.groupingBy(skill -> skill.getId().getJobId()));
        StudentContext context = studentContext(studentId);
        List<AiMatchScoreResponse> responses = new ArrayList<>();

        for (int offset = 0; offset < eligibleJobs.size(); offset += 100) {
            List<JobPosition> batch = eligibleJobs.subList(offset, Math.min(offset + 100, eligibleJobs.size()));
            List<EmbeddingDocument> documents = new ArrayList<>();
            documents.add(studentDocument(studentId, context));
            batch.forEach(job -> documents.add(jobDocument(
                job, requirements.getOrDefault(job.getId(), List.of()))));
            Map<String, AiEmbeddingResult> embeddings = embeddingPreparationService.prepare(documents);
            Map<String, Object> student = studentPayload(studentId, context, embeddings.get("student"));
            List<Map<String, Object>> payload = batch.stream()
                .map(job -> jobPayload(job, requirements.getOrDefault(job.getId(), List.of()),
                    embeddings.get(job.getId().toString())))
                .toList();
            Map<String, Map<String, Object>> results = aiServiceClient.rankJobs(student, payload).stream()
                .collect(Collectors.toMap(row -> String.valueOf(row.get("job_id")), row -> row));
            for (JobPosition job : batch) {
                Map<String, Object> result = results.get(job.getId().toString());
                if (result != null) responses.add(response(job, result, EligibilityResult.eligible()));
            }
        }
        responses.sort(Comparator.comparing(AiMatchScoreResponse::getScoreAvailable).reversed()
            .thenComparing(AiMatchScoreResponse::getMatchScore, Comparator.reverseOrder())
            .thenComparing(row -> row.getJobId().toString()));
        return responses;
    }

    private StudentContext studentContext(UUID id) {
        StudentProfile profile = studentProfileRepository.findByUserId(id).orElse(null);
        List<String> skillIds = studentSkillRepository.findByIdStudentIdAndIsConfirmedTrue(id).stream()
            .map(skill -> skill.getSkill().getId()).distinct().toList();
        return new StudentContext(
            profile == null || profile.getBio() == null ? "" : profile.getBio(), skillIds,
            profile == null ? "0" : String.valueOf(profile.getVersion()));
    }

    private EmbeddingDocument studentDocument(UUID id, StudentContext context) {
        return new EmbeddingDocument("student", "STUDENT_PROFILE", id, context.version(),
            "retrieval_query", context.bio() + " " + String.join(" ", context.skills()));
    }

    private EmbeddingDocument jobDocument(JobPosition job, List<JobSkill> requirements) {
        String text = job.getTitle() + " " + Objects.toString(job.getDescription(), "") + " "
            + String.join(" ", ids(requirements, RequirementType.MANDATORY)) + " "
            + String.join(" ", ids(requirements, RequirementType.OPTIONAL));
        return new EmbeddingDocument(job.getId().toString(), "JOB_POSITION", job.getId(),
            String.valueOf(job.getVersion()), "retrieval_document", text);
    }

    private Map<String, Object> studentPayload(UUID id, StudentContext context, AiEmbeddingResult embedding) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", id.toString());
        payload.put("skill_ids", context.skills());
        payload.put("bio_summary", context.bio());
        addEmbedding(payload, embedding);
        return payload;
    }

    private Map<String, Object> jobPayload(JobPosition job, List<JobSkill> requirements, AiEmbeddingResult embedding) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", job.getId().toString());
        payload.put("title", job.getTitle());
        payload.put("description", Objects.toString(job.getDescription(), ""));
        payload.put("mandatory_skill_ids", ids(requirements, RequirementType.MANDATORY));
        payload.put("optional_skill_ids", ids(requirements, RequirementType.OPTIONAL));
        addEmbedding(payload, embedding);
        return payload;
    }

    private void addEmbedding(Map<String, Object> payload, AiEmbeddingResult embedding) {
        if (embedding == null) return;
        payload.put("embedding", embedding.embedding());
        payload.put("embedding_model", embedding.model() + "@" + embedding.modelRevision()
            + "#" + embedding.chunkPolicy());
    }

    private List<String> ids(List<JobSkill> rows, RequirementType type) {
        return rows.stream().filter(row -> row.getRequirementType() == type)
            .map(row -> row.getSkill().getId()).distinct().toList();
    }

    private AiMatchScoreResponse response(JobPosition job, Map<String, Object> result, EligibilityResult eligibility) {
        BigDecimal score = mapper.extractScore(result);
        boolean scoreAvailable = Boolean.TRUE.equals(result.get("score_available"));
        String scoreLabel = determineScoreLabel(scoreAvailable, score);
        String scoreFormatted = scoreAvailable ? Math.round(score.doubleValue()) + "/100" : null;

        return AiMatchScoreResponse.builder().jobId(job.getId()).jobTitle(job.getTitle())
            .companyName(job.getCompany().getCompanyName())
            .applicationMethod(job.getApplicationMethod())
            .matchScore(score)
            .scoreLabel(scoreLabel)
            .scoreFormatted(scoreFormatted)
            .mandatoryScore(mapper.extractBigDecimal(result, "mandatory_score"))
            .optionalScore(mapper.extractBigDecimal(result, "optional_score"))
            .semanticScore(mapper.extractBigDecimal(result, "semantic_score"))
            .eligibilityStatus(eligibility.reasonCode())
            .eligibilityReason(eligibility.reasonMessage())
            .matchedSkills(mapper.extractStringList(result.get("matched_skills")))
            .missingSkills(mapper.extractStringList(result.get("missing_skills")))
            .explanation(mapper.extractMap(result.get("explanation")))
            .mode(String.valueOf(result.getOrDefault("mode", "unknown")))
            .scoreAvailable(scoreAvailable)
            .semanticAvailable(Boolean.TRUE.equals(result.get("semantic_available")))
            .algorithmVersion(String.valueOf(result.getOrDefault("algorithm_version", "unknown")))
            .warnings(mapper.extractStringList(result.get("warnings"))).build();
    }

    private String determineScoreLabel(boolean scoreAvailable, BigDecimal score) {
        if (!scoreAvailable) return "Chưa đủ dữ liệu";
        double val = score.doubleValue();
        if (val >= 75.0) return "Cao";
        if (val >= 45.0) return "Vừa";
        return "Thấp";
    }

    private record StudentContext(String bio, List<String> skills, String version) {}
}
