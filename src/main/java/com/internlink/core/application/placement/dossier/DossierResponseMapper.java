package com.internlink.core.application.placement.dossier;

import com.internlink.core.application.placement.ReportDeadlines;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.system.Document;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse;
import com.internlink.core.presentation.placement.dto.response.StudentDossierResponse.StudentDossierItemDto;
import com.internlink.core.shared.enums.PlacementStatus;
import com.internlink.core.shared.enums.TermStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Dựng response hồ sơ dùng chung cho màn hình sinh viên và màn hình GVHD duyệt. */
@Component
public class DossierResponseMapper {

    public StudentDossierResponse toResponse(InternshipPlacement placement, DossierSnapshot snapshot) {
        UUID placementId = placement != null ? placement.getId() : null;
        String companyName = placement != null && placement.getCompany() != null
            ? placement.getCompany().getCompanyName()
            : "Đơn vị tiếp nhận";
        String placementTitle = placement != null ? "Thực tập tại " + companyName : "Học phần Thực tập tốt nghiệp";

        int totalRequired = snapshot.requiredCount();
        int submitted = snapshot.submittedCount();
        int completion = totalRequired > 0 ? (int) Math.round(submitted * 100.0 / totalRequired) : 0;

        boolean termClosed = placement != null && placement.getTerm() != null
            && placement.getTerm().getStatus() == TermStatus.CLOSED;
        boolean finished = placement != null && placement.getStatus() != PlacementStatus.ACTIVE;
        OffsetDateTime deadline = placement != null ? ReportDeadlines.due(placement, "FINAL") : null;
        boolean overdue = deadline != null && OffsetDateTime.now().isAfter(deadline);
        String lockReason = null;
        if (termClosed) {
            lockReason = "Học kỳ thực tập đã chính thức kết thúc (CLOSED). Toàn bộ hồ sơ đã khóa lưu trữ học vụ.";
        } else if (finished) {
            lockReason = "Đợt thực tập của bạn đã hoàn tất/kết thúc (" + placement.getStatus() + "). Toàn bộ hồ sơ đã khóa lưu trữ.";
        } else if (overdue) {
            lockReason = "Đã quá thời hạn nộp hồ sơ thực tập quy định. Hệ thống đã khóa nộp hồ sơ.";
        }

        return StudentDossierResponse.builder()
            .placementId(placementId)
            .placementTitle(placementTitle)
            .companyName(companyName)
            .studentName(placement != null && placement.getStudent() != null ? placement.getStudent().getFullName() : null)
            .totalRequired(totalRequired)
            .submittedCount(submitted)
            .missingCount(totalRequired - submitted)
            .approvedCount(snapshot.approvedCount())
            .isComplete(snapshot.isComplete())
            .completionPercentage(completion)
            .isLocked(lockReason != null)
            .lockReason(lockReason)
            .deadline(deadline)
            .items(snapshot.entries().stream().map(e -> toItem(placementId, e)).toList())
            .build();
    }

    private StudentDossierItemDto toItem(UUID placementId, DossierEntry entry) {
        DossierItem item = entry.item();
        Document doc = entry.document();
        // Kèm placementId để backend chọn đúng mẫu của khoa, ngành và kỳ của sinh viên.
        String scope = placementId != null ? "?placementId=" + placementId : "";
        return StudentDossierItemDto.builder()
            .templateCode(item.name())
            .title(item.title())
            .description(item.description())
            .phase(item.phase())
            .signer(item.signer())
            .isMandatory(item.mandatory())
            .isSubmitted(entry.isSubmitted())
            .status(entry.status())
            .feedback(entry.feedback())
            .reviewerName(entry.reviewerName())
            .reviewedAt(entry.reviewedAt())
            .approvedByOffice(entry.approvedByOffice())
            .documentId(doc != null ? doc.getId() : null)
            .submittedFileName(doc != null ? doc.getOriginalName() : null)
            .submittedFileSizeBytes(doc != null ? doc.getSizeBytes() : null)
            .submittedAt(doc != null ? doc.getCreatedAt() : null)
            .downloadTemplateUrl("/api/v1/organization/templates/official/" + item.name() + "/download" + scope)
            .previewTemplateUrl("/api/v1/organization/templates/official/" + item.name() + "/preview" + scope)
            // Đi qua API để kiểm tra quyền (SV chủ hồ sơ, GVHD, Khoa) thay vì lộ đường dẫn lưu trữ.
            .fileUrl(doc != null && doc.getId() != null ? "/api/v1/documents/" + doc.getId() + "/preview" : null)
            .build();
    }
}
