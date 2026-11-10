package com.internlink.core.application.portfolio;
import static com.internlink.core.application.portfolio.PortfolioContent.clean;
import static com.internlink.core.application.portfolio.PortfolioContent.validateComplete;
import com.internlink.core.application.placement.InternshipReportService;
import com.internlink.core.application.system.DocumentService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.PortfolioForm;
import com.internlink.core.domain.placement.PortfolioRevision;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipPlacementRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPortfolioFormRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaPortfolioRevisionRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
@Service
@RequiredArgsConstructor
public class PortfolioService {
    private final JpaInternshipPlacementRepository placements;
    private final JpaUserRepository users;
    private final JpaPortfolioFormRepository forms;
    private final JpaPortfolioRevisionRepository revisions;
    private final JpaInternshipReportRepository reports;
    private final SecurityGuard security;
    private final DailyJournalService daily;
    private final PortfolioDocxExporter exporter;
    private final PortfolioMediaResolver media;
    private final DocumentService documents;
    private final InternshipReportService reportService;
    private final PortfolioScoring scoring;
    // Extracted helper services
    private final PortfolioMetadataHelper metadataHelper;
    private final PortfolioViewMapper viewMapper;
    private final PortfolioFormSupport formSupport;
    private final PortfolioSignedFileWorkflow signedFileWorkflow;
    private final com.internlink.core.application.system.notification
                    .PlacementWorkflowNotificationHelper
            notifications;
    public static final List<String> KINDS = List.of("M01", "M02", "M03", "M04", "M05", "FORM04");
    public record Save(Integer version, @NotNull Map<String, Object> content) {}
    public record Action(
            @NotBlank String action, @NotNull Integer version, @Size(max = 4000) String note) {}
    public record FormView(
            UUID id,
            String kind,
            String status,
            Integer version,
            String templateVersion,
            boolean published,
            boolean canRead,
            boolean canEdit,
            boolean canReview,
            boolean canPublish,
            Map<String, Object> content,
            String feedback,
            OffsetDateTime updatedAt,
            List<RevisionView> revisions,
            List<FileView> signedFiles) {}
    public record RevisionView(
            UUID id,
            String action,
            String actor,
            String note,
            OffsetDateTime createdAt,
            boolean downloadable) {}
    public record FileView(UUID id, String name, UUID revisionId, OffsetDateTime uploadedAt) {}
    public record Download(String name, String mime, byte[] bytes) {}
    public record ExtensionRequest(
            @NotNull OffsetDateTime newReportDueAt, @NotBlank String reason) {}
    public static class BytesFile extends BytesMultipartFile {
        public BytesFile(String name, String contentType, byte[] bytes) {
            super(name, contentType, bytes);
        }
    }
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myPlacements() {
        User a = actor();
        List<InternshipPlacement> list =
                switch (a.getRole()) {
                    case STUDENT -> placements.findByStudentId(a.getId());
                    case COMPANY_MENTOR -> placements.findByMentorId(a.getId());
                    case LECTURER -> placements.findByLecturerId(a.getId());
                    case FACULTY_ADMIN ->
                            a.getDepartment() == null
                                    ? List.of()
                                    : placements.findByStudentDepartmentId(a.getDepartment().getId());
                    default -> List.of();
                };
        return list.stream()
                .filter(p -> PortfolioAccess.read(a, p))
                .sorted(Comparator.comparing(InternshipPlacement::getStartDate).reversed())
                .map(metadataHelper::metadata)
                .toList();
    }
    @Transactional(readOnly = true)
    public Map<String, Object> overview(UUID placementId) {
        InternshipPlacement p = placement(placementId, false);
        User a = actor();
        ResourceAuthorization.require(PortfolioAccess.read(a, p));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("placement", metadataHelper.metadata(p));
        result.put("canWriteJournal", PortfolioAccess.student(a, p));
        result.put("canConfirmJournal", PortfolioAccess.confirmDaily(a, p));
        result.put("forms", KINDS.stream().map(k -> formSupport.view(formSupport.load(p, k), a)).toList());
        result.put("weeks", daily.aggregate(p));
        return result;
    }
    @Transactional
    public FormView save(UUID placementId, String kind, Save input) {
        InternshipPlacement p = placement(placementId, true);
        User a = actor();
        PortfolioForm f = formSupport.load(p, kind);
        ResourceAuthorization.require(PortfolioAccess.edit(a, f));
        DailyJournalService.active(p);
        DailyJournalService.checkVersion(
                f.getId() == null ? null : f.getVersion(), input.version());
        if (!Set.of("DRAFT", "REVISION_REQUIRED").contains(viewMapper.formStatus(f))
                || f.isPublished()) {
            throw new BadRequestException("Biểu mẫu đã khóa; cần mở lại để chỉnh sửa");
        }
        Map<String, Object> content = clean(kind, input.content(), metadataHelper.weekCount(p));
        if ("M02".equals(kind)) {
            content = formSupport.followingContent(p, content);
        }
        media.validate(content, p.getId(), kind);
        f.setContent(content);
        f.setStatus(viewMapper.formStatus(f));
        f.setUpdatedBy(a);
        return formSupport.view(forms.saveAndFlush(f), a);
    }
    @Transactional
    public FormView action(UUID placementId, String kind, Action input) {
        InternshipPlacement p = placement(placementId, true);
        PortfolioForm f = formSupport.load(p, kind);
        User a = actor();
        ResourceAuthorization.require(PortfolioAccess.read(a, p));
        if (f.getId() == null) throw new BadRequestException("Hãy lưu biểu mẫu trước");
        DailyJournalService.checkVersion(f.getVersion(), input.version());
        String action = input.action();
        String current = viewMapper.formStatus(f);
        if (Set.of("PUBLISH", "UNPUBLISH").contains(action)) {
            ResourceAuthorization.require(PortfolioAccess.faculty(a, p));
            if (!PortfolioAccess.confidential(kind)
                    || !Set.of("COMPLETED", "APPROVED").contains(current)) {
                throw new BadRequestException("Chỉ công bố phiếu đánh giá đã hoàn thành");
            }
            f.setPublished("PUBLISH".equals(action));
            f.setPublishedBy(a);
            f.setPublishedAt(OffsetDateTime.now());
            formSupport.snapshot(f, a, action, input.note(), null);
        } else if (Set.of("APPROVE", "REVISION_REQUIRED").contains(action)) {
            boolean isM05Lecturer = "M05".equals(kind) && PortfolioAccess.lecturer(a, p);
            boolean isProxyReview = Set.of("M01", "M02", "M03").contains(kind) && PortfolioAccess.lecturer(a, p) && "SUBMITTED".equals(current);
            ResourceAuthorization.require(isM05Lecturer || isProxyReview);
            if ("M05".equals(kind)) {
                if (!"SUBMITTED".equals(current) || f.getReport() == null) {
                    throw new BadRequestException("Báo cáo chưa được gửi");
                }
                String decision = "APPROVE".equals(action) ? "APPROVED" : "REVISION_REQUIRED";
                reportService.review(f.getReport().getId(), decision, input.note());
                f.setStatus(decision);
                f.setFeedback(input.note());
                formSupport.snapshot(f, a, action, input.note(), null);
            } else {
                String decision = "APPROVE".equals(action) ? "COMPLETED" : "REVISION_REQUIRED";
                f.setStatus(decision);
                f.setFeedback(input.note());
                formSupport.snapshot(f, a, "PROXY_" + action, input.note(), null);
            }
        } else if ("REOPEN".equals(action)) {
            ResourceAuthorization.require(PortfolioAccess.edit(a, f));
            DailyJournalService.active(p);
            if ("M05".equals(kind) || !"COMPLETED".equals(current) || f.isPublished()) {
                throw new BadRequestException(
                        "Chỉ mở lại phiếu chưa công bố; báo cáo cần giảng viên yêu cầu sửa");
            }
            if (input.note() == null || input.note().isBlank()) {
                throw new BadRequestException("Cần ghi lý do mở lại");
            }
            f.setStatus("DRAFT");
            formSupport.snapshot(f, a, action, input.note(), null);
        } else {
            ResourceAuthorization.require(PortfolioAccess.edit(a, f));
            DailyJournalService.active(p);
            if (!Set.of("DRAFT", "REVISION_REQUIRED").contains(current)) {
                throw new BadRequestException("Biểu mẫu đã được gửi/hoàn thành");
            }
            if (!(("M05".equals(kind) && "SUBMIT".equals(action))
                    || (!"M05".equals(kind) && "COMPLETE".equals(action)))) {
                throw new BadRequestException("Thao tác không hợp lệ");
            }
            if ("M02".equals(kind)) {
                f.setContent(formSupport.followingContent(p, f.getContent()));
            }
            validateComplete(kind, f.getContent(), metadataHelper.weekCount(p));
            f.setContent(scoring.prepare(f));
            byte[] bytes =
                    exporter.export(
                            kind,
                            media.inlineForExport(f.getContent(), p.getId(), kind),
                            metadataHelper.metadata(p),
                            false);
            if ("M05".equals(kind)) {
                var doc =
                        documents.uploadDocument(
                                a.getId(),
                                ContextType.PLACEMENT,
                                p.getId(),
                                DocumentType.REPORT,
                                new BytesMultipartFile(
                                        metadataHelper.fileName(p, kind),
                                        PortfolioDocxExporter.MIME,
                                        bytes));
                var submitted = reportService.submit(p.getId(), "FINAL", doc.getId());
                f.setReport(reports.findById(submitted.id()).orElseThrow());
                f.setStatus("SUBMITTED");
                f.setFeedback(null);
            } else {
                f.setStatus("COMPLETED");
            }
            var revision = formSupport.snapshot(f, a, action, input.note(), bytes);
            scoring.record(f, revision, a);
        }
        f.setUpdatedBy(a);
        return formSupport.view(forms.saveAndFlush(f), a);
    }
    @Transactional(readOnly = true)
    public Download export(UUID placementId, String kind, UUID revisionId) {
        InternshipPlacement p = placement(placementId, false);
        PortfolioForm f = formSupport.load(p, kind);
        User a = actor();
        ResourceAuthorization.require(PortfolioAccess.content(a, f));
        byte[] bytes;
        if (revisionId != null) {
            var r =
                    revisions
                            .findById(revisionId)
                            .orElseThrow(
                                    () ->
                                            new ResourceNotFoundException(
                                                    "PortfolioRevision", "id", revisionId));
            ResourceAuthorization.require(
                    f.getId() != null && f.getId().equals(r.getForm().getId()));
            if (viewMapper.isPublishedReader(a, f)) {
                ResourceAuthorization.require(
                        viewMapper
                                .findCurrentRevision(f)
                                .map(v -> v.getId().equals(r.getId()))
                                .orElse(false));
            }
            if (r.getDocx() == null)
                throw new BadRequestException("Mốc lịch sử này không có bản DOCX");
            bytes = r.getDocx();
        } else if (f.getId() != null
                && Set.of("SUBMITTED", "COMPLETED", "APPROVED")
                        .contains(viewMapper.formStatus(f))) {
            bytes =
                    revisions
                            .findFirstByFormIdAndDocxIsNotNullOrderByCreatedAtDesc(f.getId())
                            .map(PortfolioRevision::getDocx)
                            .orElseThrow(() -> new BadRequestException("Chưa có bản xuất đã chốt"));
        } else {
            bytes =
                    exporter.export(
                            kind,
                            media.inlineForExport(f.getContent(), p.getId(), kind),
                            metadataHelper.metadata(p),
                            true);
        }
        return new Download(metadataHelper.fileName(p, kind), PortfolioDocxExporter.MIME, bytes);
    }
    @Transactional
    public FileView uploadSigned(UUID placementId, String kind, UUID revisionId, MultipartFile file)
            throws IOException {
        return signedFileWorkflow.upload(placementId, kind, revisionId, file);
    }
    @Transactional(readOnly = true)
    public Download signed(UUID placementId, String kind, UUID fileId) {
        return signedFileWorkflow.download(placementId, kind, fileId);
    }
    @Transactional
    public Map<String, Object> extendDeadline(UUID placementId, ExtensionRequest request) {
        InternshipPlacement p = placement(placementId, true);
        User a = actor();
        ResourceAuthorization.require(
                PortfolioAccess.lecturer(a, p) || PortfolioAccess.faculty(a, p));
        if (p.getTerm().getStatus() == com.internlink.core.shared.enums.TermStatus.CLOSED) {
            throw new BadRequestException("Học kỳ đã kết thúc; không thể gia hạn");
        }
        if (request.newReportDueAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Hạn nộp mới phải ở tương lai");
        }
        Map<String, Object> workSchedule =
                new LinkedHashMap<>(p.getWorkSchedule() == null ? Map.of() : p.getWorkSchedule());
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("reportDueAt", request.newReportDueAt().toString());
        ext.put("reason", request.reason().trim());
        ext.put("grantedBy", a.getFullName());
        ext.put("grantedById", a.getId().toString());
        ext.put("grantedAt", OffsetDateTime.now().toString());
        workSchedule.put("extension", ext);
        p.setWorkSchedule(workSchedule);
        notifications.changed(
                p, a.getId(), "REPORT_DEADLINE_CHANGED", "Đã cập nhật hạn nộp báo cáo");
        placements.saveAndFlush(p);
        return metadataHelper.metadata(p);
    }
    public Map<String, Object> metadata(InternshipPlacement p) {
        return metadataHelper.metadata(p);
    }
    private InternshipPlacement placement(UUID id, boolean lock) {
        return (lock ? placements.findLockedById(id) : placements.findById(id))
                .orElseThrow(() -> new ResourceNotFoundException("InternshipPlacement", "id", id));
    }
    private User actor() {
        UUID id = security.currentUser().getId();
        return users.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }
}
