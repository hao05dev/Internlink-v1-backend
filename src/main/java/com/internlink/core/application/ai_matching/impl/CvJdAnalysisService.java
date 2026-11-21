package com.internlink.core.application.ai_matching.impl;

import com.internlink.core.application.system.DocumentService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.recruitment.JobPosition;
import com.internlink.core.domain.recruitment.JobSkill;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jpa.*;
import com.internlink.core.presentation.ai_matching.dto.request.CvJdAnalysisRequest;
import com.internlink.core.presentation.system.dto.response.DocumentDownloadInfo;
import com.internlink.core.shared.enums.*;
import com.internlink.core.shared.exception.*;
import com.internlink.core.shared.security.*;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Detailed CV-JD analysis of the student's own CV: score, per-skill evidence, CV highlights. */
@Service
@RequiredArgsConstructor
public class CvJdAnalysisService {
    static final String ENDPOINT = "/api/v1/analysis/cv-jd";
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_DESCRIPTION = 30_000;

    private final SecurityGuard guard;
    private final JpaUserRepository users;
    private final JpaDocumentRepository documents;
    private final DocumentService documentService;
    private final JpaJobPositionRepository jobs;
    private final JpaJobSkillRepository jobSkills;
    private final AiServiceClient client;

    @Transactional(readOnly = true)
    public Map<String, Object> analyze(CvJdAnalysisRequest request) {
        guard.requireRole(UserRole.STUDENT);
        if (!request.consent()) throw new BadRequestException("Cần đồng ý phân tích CV");
        UUID actorId = guard.currentUser().getId();
        User actor = users.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", actorId));
        JobPosition job = jobs.findById(request.jobId())
                .orElseThrow(() -> new ResourceNotFoundException("JobPosition", "id", request.jobId()));
        if (job.getStatus() != JobStatus.APPROVED)
            throw new BadRequestException("Chỉ phân tích được với tin tuyển dụng đang mở");
        Document document = ownedCv(request.documentId(), actor);
        DocumentDownloadInfo download = documentService.downloadDocument(document.getId(), actor.getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("filename", filename(download));
        payload.put("content_base64", Base64.getEncoder().encodeToString(readBounded(download)));
        payload.put("job", jobPayload(job, jobSkills.findByIdJobId(job.getId())));
        payload.put("render_pages", true);

        Map<String, Object> result = new HashMap<>(client.infer(ENDPOINT, payload));
        result.put("jobId", job.getId());
        result.put("documentId", document.getId());
        result.put("documentName", download.getOriginalName());
        return result;
    }

    private Document ownedCv(UUID documentId, User actor) {
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
        ResourceAuthorization.require(
                document.getOwner().getId().equals(actor.getId())
                        && document.getDocumentType() == DocumentType.CV
                        && "ACTIVE".equals(document.getStatus())
                        && document.getDeletedAt() == null);
        return document;
    }

    private static byte[] readBounded(DocumentDownloadInfo download) {
        if (download.getSizeBytes() != null && download.getSizeBytes() > MAX_BYTES)
            throw new BadRequestException("CV tối đa 10 MB");
        try (var stream = download.getResource().getInputStream()) {
            byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new BadRequestException("CV tối đa 10 MB");
            return bytes;
        } catch (java.io.IOException ex) {
            throw new BadRequestException("Không đọc được tài liệu nguồn");
        }
    }

    /** The AI service picks its parser from the file extension. */
    static String filename(DocumentDownloadInfo download) {
        String name = download.getOriginalName();
        if (name != null && name.matches("(?i).+\\.(pdf|docx|txt)$")) return name;
        String mime = Objects.toString(download.getMimeType(), "");
        if (mime.contains("wordprocessingml")) return "cv.docx";
        if (mime.startsWith("text/")) return "cv.txt";
        return "cv.pdf";
    }

    static Map<String, Object> jobPayload(JobPosition job, List<JobSkill> skills) {
        List<Map<String, Object>> requirements = skills.stream()
                .filter(row -> row.getSkill() != null)
                .map(row -> Map.<String, Object>of(
                        "skill_id", row.getSkill().getId(),
                        "skill_name", Objects.toString(row.getSkill().getSkillName(), row.getSkill().getId()),
                        "requirement_type",
                        row.getRequirementType() == RequirementType.OPTIONAL ? "optional" : "mandatory"))
                .toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", job.getId() == null ? null : job.getId().toString());
        payload.put("title", Objects.toString(job.getTitle(), ""));
        payload.put("company_name", job.getCompany() == null ? null : job.getCompany().getCompanyName());
        payload.put("description", plainText(job.getDescription()));
        payload.put("requirements", requirements);
        return payload;
    }

    /** Descriptions may come from a rich-text editor; the analyzer needs lines, not tags. */
    static String plainText(String html) {
        if (html == null) return "";
        String text = html.replaceAll("(?i)<br\\s*/?>|</(p|li|div|h[1-6])>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" ?\\n ?", "\n")
                .replaceAll("\\n{2,}", "\n")
                .strip();
        return text.length() > MAX_DESCRIPTION ? text.substring(0, MAX_DESCRIPTION) : text;
    }
}
