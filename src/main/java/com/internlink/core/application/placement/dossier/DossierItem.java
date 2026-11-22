package com.internlink.core.application.placement.dossier;

import com.internlink.core.shared.enums.DocumentType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Các mục trong bộ hồ sơ thực tập sinh viên nộp về Khoa. */
public enum DossierItem {
    FORM_04("Mẫu 04", "Phiếu tiếp nhận sinh viên thực tập (Mẫu 04)",
        "Doanh nghiệp ký & đóng dấu xác nhận tiếp nhận sinh viên", "ONBOARDING", "Doanh nghiệp tiếp nhận",
        true, DocumentType.ACCEPTANCE_LETTER),
    M01("M01", "Phiếu giao việc thực tập doanh nghiệp (M-TT-01)",
        "Mentor phân công nhiệm vụ, đề tài và kế hoạch làm việc", "ONBOARDING", "Mentor doanh nghiệp",
        true, DocumentType.AGREEMENT),
    M02("M02", "Phiếu theo dõi tiến độ thực tập (M-TT-02)",
        "Ghi chép và xác nhận tiến độ thực tập định kỳ", "IN_PROGRESS", "Sinh viên & Mentor",
        true, DocumentType.EVIDENCE),
    M03("M03", "Phiếu đánh giá của cơ quan tiếp nhận (M-TT-03)",
        "Đơn vị tiếp nhận chấm điểm thực tập và đóng dấu xác nhận", "CLOSING", "Lãnh đạo DN & Mentor",
        true, DocumentType.EXTERNAL_EVALUATION),
    M05("M05", "Đề cương & Báo cáo tổng kết thực tập (M-TT-05)",
        "Báo cáo tốt nghiệp hoàn chỉnh nộp cho Khoa và GVHD", "CLOSING", "Sinh viên",
        true, DocumentType.REPORT),
    M04("M04", "Phiếu đánh giá dành cho GVHD (M-TT-04)",
        "Tiêu chí và thang điểm đánh giá của Giảng viên hướng dẫn (Tham khảo)", "CLOSING", "Giảng viên hướng dẫn",
        false, null);

    private final String shortLabel;
    private final String title;
    private final String description;
    private final String phase;
    private final String signer;
    private final boolean mandatory;
    private final DocumentType documentType;

    DossierItem(String shortLabel, String title, String description, String phase, String signer,
                boolean mandatory, DocumentType documentType) {
        this.shortLabel = shortLabel;
        this.title = title;
        this.description = description;
        this.phase = phase;
        this.signer = signer;
        this.mandatory = mandatory;
        this.documentType = documentType;
    }

    public String shortLabel() { return shortLabel; }
    public String title() { return title; }
    public String description() { return description; }
    public String phase() { return phase; }
    public String signer() { return signer; }
    public boolean mandatory() { return mandatory; }
    public DocumentType documentType() { return documentType; }

    public static List<DossierItem> required() {
        return Arrays.stream(values()).filter(DossierItem::mandatory).toList();
    }

    public static Optional<DossierItem> ofDocumentType(DocumentType type) {
        return Arrays.stream(values()).filter(i -> i.documentType != null && i.documentType == type).findFirst();
    }

    /** Nhận cả mã cũ (M-TT-01, FORM_04_8W...) mà giao diện từng gửi lên. */
    public static Optional<DossierItem> ofTemplateCode(String code) {
        if (code == null) return Optional.empty();
        return switch (code.trim().toUpperCase()) {
            case "FORM_04", "04", "FORM_04_8W", "FORM_04_12W" -> Optional.of(FORM_04);
            case "M01", "M-TT-01", "M01_12W" -> Optional.of(M01);
            case "M02", "M-TT-02", "M02_12W" -> Optional.of(M02);
            case "M03", "M-TT-03" -> Optional.of(M03);
            case "M05", "M-TT-05", "FINAL_REPORT" -> Optional.of(M05);
            default -> Optional.empty();
        };
    }
}
