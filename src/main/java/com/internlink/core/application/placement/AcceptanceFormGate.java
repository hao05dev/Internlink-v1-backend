package com.internlink.core.application.placement;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.placement.AcceptanceFormStatus;
import com.internlink.core.domain.placement.InternshipPlacement;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.shared.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Ràng buộc Phiếu tiếp nhận lên vòng đời placement: SV trúng tuyển qua hệ thống cũng phải nộp phiếu,
 * và chỉ bắt đầu thực tập khi Khoa đã công bố danh sách.
 */
@Component
@RequiredArgsConstructor
public class AcceptanceFormGate {
    private final JpaStudentFoundApplicationRepository applications;

    /** Kích hoạt placement từ thỏa thuận 3 bên chỉ khi VP Trường đã nhận bản giấy phiếu; đánh dấu phiếu đã duyệt. */
    public void approveForAgreement(UUID agreementId, User reviewer) {
        var form = applications.findByAgreementId(agreementId)
            .orElseThrow(() -> new BadRequestException("Sinh viên chưa nộp Phiếu tiếp nhận cho thỏa thuận này"));
        if (!AcceptanceFormStatus.RECEIVED.equals(form.getStatus()))
            throw new BadRequestException("Phiếu tiếp nhận chưa được Văn phòng Trường xác nhận bản giấy");
        form.setStatus(AcceptanceFormStatus.APPROVED);
        form.setReviewedBy(reviewer);
        form.setReviewedAt(OffsetDateTime.now());
        applications.save(form);
    }

    /** Placement chỉ được ACTIVE khi phiếu đã nằm trong danh sách Khoa công bố (placement cũ không có phiếu được giữ nguyên). */
    public void requirePublished(InternshipPlacement placement) {
        Optional<StudentFoundApplication> form = placement.getStudentFoundApplication() != null
            ? Optional.of(placement.getStudentFoundApplication())
            : placement.getAgreement() != null ? applications.findByAgreementId(placement.getAgreement().getId()) : Optional.empty();
        if (form.isPresent() && !AcceptanceFormStatus.PUBLISHED.equals(form.get().getStatus()))
            throw new BadRequestException("Khoa chưa công bố danh sách sinh viên thực tập có phiếu này");
    }
}
