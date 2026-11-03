package com.internlink.core.application.placement;

import com.internlink.core.domain.placement.AcceptanceFormStatus;
import com.internlink.core.domain.placement.StudentFoundApplication;
import com.internlink.core.infrastructure.persistence.jpa.JpaStudentFoundApplicationRepository;
import com.internlink.core.presentation.placement.dto.response.StudentFoundResponse;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.security.ResourceAuthorization;
import com.internlink.core.shared.security.TermGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Văn phòng Trường CNTT-TT: đối chiếu bản giấy Phiếu tiếp nhận khi SV nộp tại sảnh,
 * trả phiếu sai cho SV sửa, và xác nhận SV đã nhận giấy tờ sau khi Khoa công bố danh sách.
 */
@Service
@RequiredArgsConstructor
public class AcceptanceFormOfficeService {
    private static final Set<String> OFFICE_VISIBLE = Set.of(
        AcceptanceFormStatus.SUBMITTED, AcceptanceFormStatus.RECEIVED, AcceptanceFormStatus.APPROVED,
        AcceptanceFormStatus.PUBLISHED, AcceptanceFormStatus.REVISION_REQUIRED, AcceptanceFormStatus.REJECTED);

    private final JpaStudentFoundApplicationRepository applications;
    private final AcceptanceFormLookup lookup;
    private final AcceptanceFormMapper mapper;
    private final AcceptanceFormNotifier notifier;

    @Transactional(readOnly = true)
    public List<StudentFoundResponse> intake(UUID termId, String status) {
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(lookup.actor()));
        if (status != null && !OFFICE_VISIBLE.contains(status))
            throw new BadRequestException("Trạng thái lọc không hợp lệ");
        var source = termId != null
            ? applications.findByTermId(termId)
            : applications.findByStatusInOrderByUpdatedAtDesc(status != null ? Set.of(status) : OFFICE_VISIBLE);
        return source.stream()
            .filter(a -> OFFICE_VISIBLE.contains(a.getStatus()))
            .filter(a -> status == null || status.equals(a.getStatus()))
            .sorted(Comparator.comparing(StudentFoundApplication::getUpdatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .map(mapper::toResponse)
            .toList();
    }

    /** SV đã nộp bản giấy khớp với Form online: chuyển phiếu vào danh sách xét duyệt của Khoa. */
    @Transactional
    public StudentFoundResponse confirmReceived(UUID id) {
        var app = requireOfficeAction(id);
        AcceptanceFormLookup.requireStatus(app, AcceptanceFormStatus.SUBMITTED,
            "Chỉ xác nhận bản giấy cho phiếu SV đã nộp Form online");
        if (app.getAcceptanceDocument() == null || !"ACTIVE".equals(app.getAcceptanceDocument().getStatus()))
            throw new BadRequestException("Phiếu chưa có bản scan hợp lệ");
        app.setStatus(AcceptanceFormStatus.RECEIVED);
        app.setReceivedAt(OffsetDateTime.now());
        app.setReceivedBy(lookup.actor());
        var saved = applications.save(app);
        notifier.received(saved);
        return mapper.toResponse(saved);
    }

    /** Bản giấy thiếu chữ ký/dấu hoặc không khớp Form: trả SV sửa và nộp lại. */
    @Transactional
    public StudentFoundResponse returnToStudent(UUID id, String note) {
        var app = requireOfficeAction(id);
        AcceptanceFormLookup.requireStatus(app, AcceptanceFormStatus.SUBMITTED,
            "Chỉ trả lại phiếu đang chờ đối chiếu bản giấy");
        String reason = AcceptanceFormLookup.requireNote(note, "Cần nêu lý do trả phiếu");
        app.setStatus(AcceptanceFormStatus.REVISION_REQUIRED);
        app.setReviewNote(reason);
        var saved = applications.save(app);
        notifier.returned(saved, "Văn phòng Trường", reason);
        return mapper.toResponse(saved);
    }

    /** SV đã đến Văn phòng Trường nhận giấy tờ sau khi danh sách được công bố. */
    @Transactional
    public StudentFoundResponse confirmDocumentsCollected(UUID id) {
        var app = requireOfficeAction(id);
        AcceptanceFormLookup.requireStatus(app, AcceptanceFormStatus.PUBLISHED,
            "Chỉ phát giấy tờ cho SV có tên trong danh sách đã công bố");
        if (app.getDocumentsCollectedAt() != null)
            throw new BadRequestException("Sinh viên đã nhận giấy tờ");
        app.setDocumentsCollectedAt(OffsetDateTime.now());
        app.setDocumentsCollectedBy(lookup.actor());
        var saved = applications.save(app);
        notifier.documentsCollected(saved);
        return mapper.toResponse(saved);
    }

    private StudentFoundApplication requireOfficeAction(UUID id) {
        ResourceAuthorization.require(ResourceAuthorization.actsForSchool(lookup.actor()));
        var app = lookup.get(id);
        TermGuard.requireNotClosed(app.getTerm());
        return app;
    }
}
