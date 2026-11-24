package com.internlink.core.application.organization;

import com.internlink.core.application.system.upload.MediaAssetService;
import com.internlink.core.application.system.upload.MediaPurpose;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.infrastructure.integration.ai.AiServiceClient;
import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipTermRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.SecurityGuard;
import com.internlink.core.shared.security.TermGuard;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/**
 * Văn bản Kế hoạch TTTT (PDF scan/ảnh) của kỳ: VP Trường tải lên như tệp đính kèm bài viết,
 * AI đọc thành bản nháp các mốc để điền sẵn form. Bản nháp không được lưu; VP Trường soát rồi bấm Lưu.
 */
@Service
@RequiredArgsConstructor
public class TermPlanDocumentService {
    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final Set<String> MIME = Set.of("application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> AI_ERRORS = Map.of(
            "form_reader_disabled", "Chức năng đọc văn bản bằng AI chưa được bật, vui lòng nhập tay.",
            "unsupported_document_type", "Chỉ đọc được tệp PDF hoặc ảnh JPG, PNG, WEBP.",
            "document_too_large", "Tệp quá lớn để AI đọc (tối đa 10 MB).");

    private final JpaInternshipTermRepository terms;
    private final JpaUserRepository users;
    private final MediaAssetRepository assets;
    private final MediaAssetService media;
    private final SecurityGuard security;
    private final AiServiceClient ai;

    public record PlanDocument(UUID id, String url, String fileName, String mimeType, long sizeBytes) {}

    /** Ngày dạng yyyy-MM-dd theo văn bản; frontend tự gắn giờ Việt Nam khi lưu. */
    public record DraftMilestone(String kind, String label, String note, String startDate, String endDate,
                                 Integer requiredWeeks) {}

    public record PlanReading(String documentRef, String issuedDate, List<DraftMilestone> milestones,
                              List<String> warnings, String model) {}

    @Transactional(readOnly = true)
    public PlanReading read(UUID termId, UUID mediaId) {
        var term = terms.findById(termId)
                .orElseThrow(() -> new ResourceNotFoundException("InternshipTerm", "id", termId));
        var actor = users.findById(security.currentUser().getId()).orElseThrow();
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(actor));
        TermGuard.requireNotClosed(term);
        var asset = requireDocument(mediaId);
        if (asset.size() > MAX_BYTES) throw new BadRequestException(AI_ERRORS.get("document_too_large"));

        Map<String, Object> reading = ai.readTermPlan(asset.mime(),
                Base64.getEncoder().encodeToString(bytes(asset.id())), term.getAcademicYear());
        if ("FAILED".equals(reading.get("status")))
            throw new BadRequestException(AI_ERRORS.getOrDefault(String.valueOf(reading.get("error_code")),
                    "AI chưa đọc được văn bản lúc này, vui lòng thử lại hoặc nhập tay."));
        return toReading(reading);
    }

    /** Tệp văn bản kế hoạch phải là tệp đính kèm bài viết (PDF/ảnh) mà người dùng hiện tại được đọc. */
    public MediaAssetRepository.Asset requireDocument(UUID mediaId) {
        var asset = assets.get(mediaId);
        String mime = asset.mime() == null ? "" : asset.mime().toLowerCase(Locale.ROOT);
        if (asset.purpose() != MediaPurpose.POST_ATTACHMENT || !MIME.contains(mime))
            throw new BadRequestException(AI_ERRORS.get("unsupported_document_type"));
        return asset;
    }

    /** Thông tin tệp đang gắn với kỳ; tệp đã bị xóa thì coi như chưa có. */
    public Optional<PlanDocument> describe(InternshipTerm term) {
        if (term.getPlanDocumentMediaId() == null) return Optional.empty();
        try {
            var a = assets.get(term.getPlanDocumentMediaId());
            return Optional.of(new PlanDocument(a.id(), a.url(), a.name(), a.mime(), a.size()));
        } catch (ResourceNotFoundException ex) {
            return Optional.empty();
        }
    }

    static PlanReading toReading(Map<String, Object> reading) {
        List<DraftMilestone> milestones = new ArrayList<>();
        if (reading.get("milestones") instanceof List<?> list)
            for (Object item : list)
                if (item instanceof Map<?, ?> m)
                    milestones.add(new DraftMilestone(text(m.get("kind")), text(m.get("label")), text(m.get("note")),
                            text(m.get("start_date")), text(m.get("end_date")), weeks(m.get("required_weeks"))));
        List<String> warnings = new ArrayList<>();
        if (reading.get("warnings") instanceof List<?> list) list.forEach(w -> warnings.add(String.valueOf(w)));
        return new PlanReading(text(reading.get("document_ref")), text(reading.get("issued_date")),
                milestones, warnings, text(reading.get("model")));
    }

    private static Integer weeks(Object value) {
        return value instanceof Number n && (n.intValue() == 8 || n.intValue() == 12) ? n.intValue() : null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private byte[] bytes(UUID mediaId) {
        try (var in = media.download(mediaId, false).resource().getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được tệp văn bản kế hoạch");
        }
    }
}
