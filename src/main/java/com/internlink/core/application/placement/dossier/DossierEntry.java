package com.internlink.core.application.placement.dossier;

import com.internlink.core.domain.system.Document;

import java.time.OffsetDateTime;

/**
 * Tình trạng một mục hồ sơ.
 *
 * @param status MISSING, PENDING, APPROVED hoặc REVISION_REQUIRED
 * @param document file sinh viên nộp gần nhất (có thể null)
 * @param approvedByOffice Mẫu 04 đã được Văn phòng nhận bản giấy qua phiếu tiếp nhận
 */
public record DossierEntry(
    DossierItem item,
    String status,
    Document document,
    String feedback,
    String reviewerName,
    OffsetDateTime reviewedAt,
    boolean approvedByOffice
) {
    public static final String MISSING = "MISSING";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REVISION_REQUIRED = "REVISION_REQUIRED";

    public boolean isApproved() {
        return APPROVED.equals(status);
    }

    public boolean isSubmitted() {
        return document != null || approvedByOffice;
    }

    /** Mô tả ngắn cho thông báo lỗi và cột Excel, ví dụ "M03 chưa nộp". */
    public String problemLabel() {
        return item.shortLabel() + switch (status) {
            case MISSING -> " chưa nộp";
            case PENDING -> " chờ GVHD duyệt";
            case REVISION_REQUIRED -> " cần nộp lại";
            default -> "";
        };
    }
}
