package com.internlink.core.application.placement.dossier;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.AcceptanceFormStatus;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.InternshipReport;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.domain.system.Document;
import com.internlink.core.infrastructure.persistence.jpa.JpaDocumentRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaInternshipReportRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Đọc tình trạng bộ hồ sơ nộp Khoa của một lần thực tập.
 * Mẫu 04, M01, M02, M03 theo kết quả GVHD duyệt từng file; M05 theo kết quả duyệt báo cáo cuối kỳ.
 */
@Component
@RequiredArgsConstructor
public class DossierInspector {

    private static final Set<String> OFFICE_RECEIVED = Set.of(
        AcceptanceFormStatus.RECEIVED, AcceptanceFormStatus.APPROVED, AcceptanceFormStatus.PUBLISHED);

    private final JpaDocumentRepository documentRepository;
    private final JpaInternshipReportRepository reportRepository;
    private final JpaStudentFoundApplicationRepository acceptanceFormRepository;

    public DossierSnapshot inspect(InternshipPlacement placement) {
        if (placement == null) {
            return new DossierSnapshot(emptyEntries());
        }
        List<Document> docs = documentRepository.findByContextTypeAndContextId(ContextType.PLACEMENT, placement.getId());
        List<DossierEntry> entries = new ArrayList<>();
        for (DossierItem item : DossierItem.values()) {
            entries.add(switch (item) {
                case FORM_04 -> acceptanceEntry(placement, latest(docs, item));
                case M05 -> reportEntry(placement);
                case M04 -> new DossierEntry(item, DossierEntry.MISSING, null, null, null, null, false);
                default -> documentEntry(item, latest(docs, item));
            });
        }
        return new DossierSnapshot(entries);
    }

    /** Chặn chấm/tổng hợp điểm khi hồ sơ chưa đủ hoặc chưa được GVHD duyệt hết. */
    public void requireComplete(InternshipPlacement placement) {
        DossierSnapshot snapshot = inspect(placement);
        if (!snapshot.isComplete()) {
            throw new BadRequestException("Hồ sơ thực tập chưa đủ để chấm điểm: " + String.join(", ", snapshot.problems()));
        }
    }

    static Document latest(List<Document> docs, DossierItem item) {
        return docs.stream()
            .filter(d -> d.getDocumentType() == item.documentType() && "ACTIVE".equals(d.getStatus()))
            .max(Comparator.comparing(Document::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
            .orElse(null);
    }

    private DossierEntry documentEntry(DossierItem item, Document doc) {
        if (doc == null) {
            return new DossierEntry(item, DossierEntry.MISSING, null, null, null, null, false);
        }
        String status = doc.getReviewStatus() != null ? doc.getReviewStatus() : DossierEntry.PENDING;
        return new DossierEntry(item, status, doc, doc.getReviewNote(), nameOf(doc.getReviewedBy()), doc.getReviewedAt(), false);
    }

    /** Văn phòng đã nhận bản giấy phiếu tiếp nhận thì Mẫu 04 coi như đạt, không cần GVHD duyệt lại. */
    private DossierEntry acceptanceEntry(InternshipPlacement placement, Document doc) {
        Optional<StudentFoundApplication> form = acceptanceForm(placement)
            .filter(f -> f.getReceivedAt() != null || OFFICE_RECEIVED.contains(f.getStatus()));
        if (form.isPresent()) {
            StudentFoundApplication f = form.get();
            Document shown = doc != null ? doc : f.getAcceptanceDocument();
            return new DossierEntry(DossierItem.FORM_04, DossierEntry.APPROVED, shown,
                "Văn phòng đã nhận bản giấy phiếu tiếp nhận", nameOf(f.getReceivedBy()),
                f.getReceivedAt() != null ? f.getReceivedAt() : f.getReviewedAt(), true);
        }
        return documentEntry(DossierItem.FORM_04, doc);
    }

    private Optional<StudentFoundApplication> acceptanceForm(InternshipPlacement placement) {
        if (placement.getStudentFoundApplication() != null) {
            return Optional.of(placement.getStudentFoundApplication());
        }
        if (placement.getTerm() == null || placement.getStudent() == null) return Optional.empty();
        return acceptanceFormRepository.findByTermIdAndStudentId(placement.getTerm().getId(), placement.getStudent().getId());
    }

    /** M05 chỉ tính theo báo cáo cuối kỳ (FINAL); file báo cáo giữa kỳ cũng là REPORT nên không được dùng làm M05. */
    private DossierEntry reportEntry(InternshipPlacement placement) {
        Optional<InternshipReport> report = reportRepository.findByPlacementIdAndReportType(placement.getId(), "FINAL")
            .filter(r -> r.getDocument() != null && "ACTIVE".equals(r.getDocument().getStatus()));
        if (report.isEmpty()) {
            return new DossierEntry(DossierItem.M05, DossierEntry.MISSING, null, null, null, null, false);
        }
        InternshipReport r = report.get();
        String status = switch (String.valueOf(r.getStatus())) {
            case "APPROVED" -> DossierEntry.APPROVED;
            case "REVISION_REQUIRED" -> DossierEntry.REVISION_REQUIRED;
            default -> DossierEntry.PENDING;
        };
        return new DossierEntry(DossierItem.M05, status, r.getDocument(), r.getLecturerFeedback(),
            nameOf(r.getReviewedBy()), r.getReviewedAt(), false);
    }

    private static String nameOf(User user) {
        return user != null ? user.getFullName() : null;
    }

    /** Sinh viên chưa có lần thực tập: mọi mục đều chưa nộp. */
    private static List<DossierEntry> emptyEntries() {
        List<DossierEntry> list = new ArrayList<>();
        for (DossierItem item : DossierItem.values()) {
            list.add(new DossierEntry(item, DossierEntry.MISSING, null, null, null, null, false));
        }
        return list;
    }
}
