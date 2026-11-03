package com.internlink.core.application.ai_matching.impl;

import com.internlink.core.application.placement.WeeklyLogbookService;
import com.internlink.core.application.system.DocumentService;
import com.internlink.core.domain.ai_matching.*;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.ai_matching.dto.request.CvExtractionRequest;
import com.internlink.core.presentation.ai_matching.dto.response.StudentSkillResponse;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiWorkspaceService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private final SecurityGuard guard;
    private final JpaUserRepository users;
    private final JpaDocumentRepository documents;
    private final DocumentService documentService;
    private final AiCvProcessingHandler cvHandler;
    private final JpaSkillTaxonomyRepository taxonomy;
    private final JpaStudentSkillRepository skills;
    private final JpaStudentProfileRepository profiles;
    private final JpaInternshipTermRepository terms;
    private final JpaStudentRosterRepository rosters;
    private final JpaJobPositionRepository jobs;
    private final JpaJobSkillRepository jobSkills;
    private final JpaInternshipPlacementRepository placements;
    private final WeeklyLogbookService logbooks;
    private final AiServiceClient client;
    private final AiMatchingMapper mapper;
    private final AiRunNotificationHelper runNotifications;

    private User actor() {
        UUID id = guard.currentUser().getId();
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    @Transactional
    public Map<String, Object> extractCv(CvExtractionRequest request) {
        guard.requireRole(UserRole.STUDENT);
        User actor = actor();
        if (!request.consent()) throw new BadRequestException("Cần đồng ý phân tích CV");
        if (request.documentId() != null && request.text() != null && !request.text().isBlank())
            throw new BadRequestException("Chọn một nguồn: tài liệu hoặc văn bản");
        Map<String, Object> context = new HashMap<>();
        context.put("allow_cloud", request.allowCloud());
        context.put("consent", true);
        String text = request.text();
        if (request.documentId() != null) {
            var document =
                    documents
                            .findById(request.documentId())
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "Document", "id", request.documentId()));
            ResourceAuthorization.require(
                    document.getOwner().getId().equals(actor.getId())
                            && document.getDocumentType() == DocumentType.CV
                            && "ACTIVE".equals(document.getStatus())
                            && document.getDeletedAt() == null);
            var download = documentService.downloadDocument(document.getId(), actor.getId());
            if (download.getSizeBytes() != null && download.getSizeBytes() > MAX_BYTES)
                throw new BadRequestException("CV tối đa 10 MB");
            try (var stream = download.getResource().getInputStream()) {
                byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) throw new BadRequestException("CV tối đa 10 MB");
                context.put("document_base64", Base64.getEncoder().encodeToString(bytes));
            } catch (java.io.IOException ex) {
                throw new BadRequestException("Không đọc được tài liệu nguồn");
            }
            text = "[document]";
        }
        var result = cvHandler.processCvSkills(actor.getId(), request.documentId(), text, context);
        runNotifications.cvCompleted(result.run());
        Map<String, Object> response = new HashMap<>(result.run().getOutputResult());
        response.put("skills", result.skills());
        response.put("runId", result.run().getId());
        // The extraction endpoint returns the report even on failure so the saved run remains
        // auditable.
        return response;
    }

    @Transactional
    public StudentSkillResponse declareSkill(String skillId, String evidence) {
        guard.requireRole(UserRole.STUDENT);
        if (evidence == null || evidence.isBlank() || evidence.length() > 2000)
            throw new BadRequestException("Hãy mô tả minh chứng kỹ năng (tối đa 2.000 ký tự)");
        User actor = actor();
        SkillTaxonomy skill =
                taxonomy.findById(skillId)
                        .filter(s -> Boolean.TRUE.equals(s.getIsActive()))
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "SkillTaxonomy", "id", skillId));
        StudentSkillId id = new StudentSkillId(actor.getId(), skillId);
        StudentSkill row =
                skills.findById(id)
                        .orElse(StudentSkill.builder().id(id).student(actor).skill(skill).build());
        Map<String, Object> history =
                new HashMap<>(row.getAiEvidence() == null ? Map.of() : row.getAiEvidence());
        history.put("assertionStatus", "student_declared");
        history.put("studentEvidence", evidence.strip());
        row.setAiEvidence(history);
        row.setSource(SkillSource.STUDENT_DECLARED);
        row.setConfidence(null);
        row.setIsConfirmed(true);
        return mapper.toStudentSkillResponse(skills.save(row));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> options() {
        User actor = actor();
        List<InternshipTerm> visibleTerms = List.of();
        List<JobPosition> visibleJobs = List.of();
        if (actor.getRole() == UserRole.STUDENT) {
            visibleTerms =
                    profiles.findByUserId(actor.getId())
                            .filter(p -> p.getProgram() != null)
                            .map(
                                    p ->
                                            terms.findByDepartmentId(
                                                    p.getProgram().getDepartment().getId()))
                            .orElse(List.of());
        } else if (actor.getRole() == UserRole.FACULTY_ADMIN && actor.getDepartment() != null) {
            visibleTerms = terms.findByDepartmentId(actor.getDepartment().getId());
            visibleJobs =
                    visibleTerms.stream()
                            .flatMap(t -> jobs.findByTermId(t.getId()).stream())
                            .toList();
        } else if (actor.getRole() == UserRole.COMPANY_REP && actor.getCompany() != null) {
            visibleJobs = jobs.findByCompanyId(actor.getCompany().getId());
        }
        var visiblePlacements =
                switch (actor.getRole()) {
                    case STUDENT -> placements.findByStudentId(actor.getId());
                    case LECTURER -> placements.findByLecturerId(actor.getId());
                    case COMPANY_MENTOR -> placements.findByMentorId(actor.getId());
                    default -> List.<com.internlink.core.domain.placement.InternshipPlacement>of();
                };
        return Map.of(
                "terms",
                visibleTerms.stream()
                        // Active terms first so screens default to the term in progress.
                        .sorted(Comparator.comparing((InternshipTerm t) -> t.getStatus() != TermStatus.ACTIVE))
                        .map(AiTermOptions::of)
                        .toList(),
                "jobs",
                visibleJobs.stream()
                        .map(j -> Map.of("id", j.getId(), "name", j.getTitle()))
                        .toList(),
                "placements",
                visiblePlacements.stream()
                        .map(
                                p ->
                                        Map.of(
                                                "id",
                                                p.getId(),
                                                "name",
                                                p.getStudent().getFullName()
                                                        + " — "
                                                        + p.getTerm().getTermName()))
                        .toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reviewJob(UUID id) {
        User actor = actor();
        JobPosition job =
                jobs.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", id));
        ResourceAuthorization.require(
                ResourceAuthorization.representsCompany(actor, job.getCompany().getId())
                        || ResourceAuthorization.managesDepartment(
                                actor, job.getDepartment().getId()));
        Map<String, Object> checklist =
                client.infer(
                        "/api/v1/assistance/job-review",
                        Map.of(
                                "description",
                                job.getDescription(),
                                "learning_outcomes",
                                job.getTargetLearningOutcomes(),
                                "location",
                                Objects.toString(job.getLocation(), ""),
                                "work_format",
                                Objects.toString(job.getWorkFormat(), "")));
        Map<String, Object> extracted =
                client.infer(
                        "/api/v1/skills/extract",
                        Map.of(
                                "text",
                                job.getDescription(),
                                "source_type",
                                "jd",
                                "allow_cloud",
                                false));
        return Map.of(
                "checklist",
                checklist,
                "extraction",
                extracted,
                "jobId",
                id,
                "sourceHash",
                ContentHash.sha256(job.getDescription()),
                "requiresReview",
                true);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summarize(UUID placementId) {
        // Existing logbook service enforces the actor's placement relationship.
        var rows = logbooks.getLogbooksByPlacement(placementId);
        List<Map<String, Object>> entries =
                rows.stream()
                        .filter(row -> row.getStatus() != LogbookStatus.DRAFT)
                        .map(
                                row -> {
                                    String text =
                                            Objects.toString(row.getTasksCompleted(), "")
                                                    + "\n"
                                                    + Objects.toString(
                                                            row.getLearningReflection(), "");
                                    return Map.<String, Object>of(
                                            "id",
                                            row.getId().toString(),
                                            "text",
                                            text,
                                            "version",
                                            ContentHash.sha256(text),
                                            "verification",
                                            row.getMentorReviewedAt() != null
                                                            && (row.getStatus()
                                                                            == LogbookStatus
                                                                                    .APPROVED_BY_MENTOR
                                                                    || row.getStatus()
                                                                            == LogbookStatus
                                                                                    .APPROVED_BY_LECTURER)
                                                    ? "mentor_reviewed"
                                                    : "self_reported");
                                })
                        .filter(row -> !row.get("text").toString().isBlank())
                        .toList();
        if (entries.size() > 100
                || entries.stream().mapToInt(e -> e.get("text").toString().length()).sum() > 200000)
            throw new BadRequestException("Nhật ký vượt giới hạn 100 bản ghi hoặc 200.000 ký tự");
        return client.infer("/api/v1/assistance/journal-summary", Map.of("entries", entries));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> learningPlan(List<String> ids, int hours) {
        guard.requireRole(UserRole.STUDENT);
        if (ids == null || ids.isEmpty() || ids.size() > 3 || hours < 1 || hours > 20)
            throw new BadRequestException("Chọn 1–3 kỹ năng và 1–20 giờ mỗi tuần");
        return client.infer(
                "/api/v1/assistance/learning-plan",
                Map.of("skill_ids", ids, "hours_per_week", hours));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> skillGaps(UUID termId) {
        User actor = actor();
        InternshipTerm term =
                terms.findById(termId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "InternshipTerm", "id", termId));
        ResourceAuthorization.require(
                ResourceAuthorization.managesDepartment(actor, term.getDepartment().getId()));
        var roster = rosters.findByTermId(termId);
        List<UUID> studentIds =
                roster.stream()
                        .filter(r -> r.getClaimedUser() != null)
                        .map(r -> r.getClaimedUser().getId())
                        .distinct()
                        .toList();
        Map<String, Object> report = new HashMap<>();
        report.put("cohortSize", roster.size());
        report.put("claimedProfiles", studentIds.size());
        report.put("minimumGroupSize", 5);
        report.put(
                "notice",
                "Chỉ phản ánh kỹ năng tự xác nhận; thiếu dữ liệu không đồng nghĩa thiếu năng lực.");
        if (studentIds.size() < 5) {
            report.put("suppressed", true);
            report.put("skills", List.of());
            return report;
        }
        Map<String, Long> counts = new HashMap<>();
        int profilesWithSkills = 0;
        for (UUID id : studentIds) {
            var confirmed = skills.findByIdStudentIdAndIsConfirmedTrue(id);
            if (!confirmed.isEmpty()) profilesWithSkills++;
            confirmed.stream()
                    .map(s -> s.getSkill().getId())
                    .distinct()
                    .forEach(s -> counts.merge(s, 1L, Long::sum));
        }
        List<UUID> approved =
                jobs.findByTermIdAndStatus(termId, JobStatus.APPROVED).stream()
                        .map(JobPosition::getId)
                        .toList();
        Map<String, Long> demand = new HashMap<>();
        if (!approved.isEmpty())
            jobSkills.findByIdJobIdIn(approved).stream()
                    .filter(s -> s.getRequirementType() == RequirementType.MANDATORY)
                    .forEach(s -> demand.merge(s.getSkill().getId(), 1L, Long::sum));
        report.put("suppressed", false);
        report.put(
                "profilesWithConfirmedSkills", profilesWithSkills < 5 ? null : profilesWithSkills);
        report.put("approvedJobs", approved.size());
        report.put(
                "skills",
                taxonomy.findAll().stream()
                        .filter(s -> demand.containsKey(s.getId()))
                        .map(
                                s -> {
                                    Map<String, Object> row = new HashMap<>();
                                    long count = counts.getOrDefault(s.getId(), 0L);
                                    row.put("id", s.getId());
                                    row.put("name", s.getSkillName());
                                    row.put("jobsRequiring", demand.get(s.getId()));
                                    // Suppress both the cell and its complement to prevent simple
                                    // subtraction.
                                    boolean safe = count >= 5 && studentIds.size() - count >= 5;
                                    row.put("confirmedStudents", safe ? count : null);
                                    return row;
                                })
                        .toList());
        return report;
    }

}
