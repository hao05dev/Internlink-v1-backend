package com.internlink.core.domain.placement;

import java.util.Set;

/**
 * Vòng đời Phiếu tiếp nhận:
 * DRAFT -> SUBMITTED (SV nộp Form + scan) -> RECEIVED (VP Trường nhận bản giấy)
 * -> APPROVED (Khoa duyệt) -> PUBLISHED (Khoa công bố danh sách).
 * REVISION_REQUIRED / REJECTED: VP Trường trả lại hoặc Khoa yêu cầu sửa / từ chối.
 */
public final class AcceptanceFormStatus {
    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String RECEIVED = "RECEIVED";
    public static final String REVISION_REQUIRED = "REVISION_REQUIRED";
    public static final String APPROVED = "APPROVED";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String REJECTED = "REJECTED";

    /** SV còn được sửa nội dung phiếu. */
    public static final Set<String> EDITABLE = Set.of(DRAFT, REVISION_REQUIRED, REJECTED);
    /** Phiếu đang trong quy trình hoặc đã được chấp nhận (không cần nhắc SV). */
    public static final Set<String> IN_PROGRESS = Set.of(SUBMITTED, RECEIVED, APPROVED, PUBLISHED);

    private AcceptanceFormStatus() {}
}
