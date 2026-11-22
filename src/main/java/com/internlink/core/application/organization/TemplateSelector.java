package com.internlink.core.application.organization;

import com.internlink.core.domain.organization.ReportTemplateVersion;
import com.internlink.core.infrastructure.persistence.jpa.JpaReportTemplateVersionRepository;
import com.internlink.core.shared.enums.ReportTemplateType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Chọn biểu mẫu phát cho một sinh viên. Thứ tự ưu tiên:
 * mẫu của khoa đúng ngành, mẫu chung của khoa, mẫu chung cấp trường.
 * Không bao giờ trả về mẫu của khoa khác.
 */
@Component
@RequiredArgsConstructor
public class TemplateSelector {

    private final JpaReportTemplateVersionRepository templateRepository;

    /**
     * Ưu tiên mẫu trong kỳ của sinh viên; nếu kỳ đó chưa treo mẫu phù hợp thì lấy
     * mẫu phù hợp mới nhất từ các kỳ khác, trước khi phải dùng file mẫu tự sinh.
     */
    @Transactional(readOnly = true)
    public Optional<ReportTemplateVersion> select(ReportTemplateType requestedType, TemplateAudience audience) {
        ReportTemplateType type = effectiveType(requestedType, audience);
        if (audience.termId() != null) {
            Optional<ReportTemplateVersion> inTerm = pickBest(
                templateRepository.findByTermIdAndReportTypeAndIsActiveTrue(audience.termId(), type), audience);
            if (inTerm.isPresent()) {
                return inTerm;
            }
        }
        return pickBest(templateRepository.findByReportTypeAndIsActiveTrue(type), audience);
    }

    /** Loại mẫu thực sự phát cho sinh viên (Mẫu 04 đổi bản 8/12 tuần theo số tín chỉ). */
    public static ReportTemplateType effectiveType(ReportTemplateType type, TemplateAudience audience) {
        boolean isReception = type == ReportTemplateType.FORM_04_RECEPTION_8W
            || type == ReportTemplateType.FORM_04_RECEPTION_12W;
        Integer credits = audience.internshipCredits();
        if (!isReception || credits == null) {
            return type;
        }
        // 5 tín chỉ = Thực tập doanh nghiệp 12 tuần; 2-3 tín chỉ = TTTT 8 tuần.
        return credits >= 5 ? ReportTemplateType.FORM_04_RECEPTION_12W : ReportTemplateType.FORM_04_RECEPTION_8W;
    }

    private static Optional<ReportTemplateVersion> pickBest(List<ReportTemplateVersion> candidates, TemplateAudience audience) {
        return candidates.stream()
            .filter(t -> rank(t, audience) >= 0)
            .min(Comparator.<ReportTemplateVersion>comparingInt(t -> rank(t, audience))
                .thenComparingInt(t -> weekMismatch(t, audience))
                .thenComparing(t -> t.getVersionNumber() != null ? t.getVersionNumber() : 0, Comparator.reverseOrder())
                .thenComparing(t -> t.getCreatedAt() != null ? t.getCreatedAt() : OffsetDateTime.MIN, Comparator.reverseOrder()));
    }

    /**
     * M-TT-01/M-TT-02 có bản 8 tuần và bản 12 tuần (TTDN) cùng loại mẫu; nhận ra bản 12 tuần
     * qua tên file/tiêu đề. 1 = lệch với số tuần của sinh viên, 0 = khớp hoặc chưa biết tín chỉ.
     */
    static int weekMismatch(ReportTemplateVersion template, TemplateAudience audience) {
        Integer credits = audience.internshipCredits();
        if (credits == null) return 0;
        return isTwelveWeek(template) == (credits >= 5) ? 0 : 1;
    }

    static boolean isTwelveWeek(ReportTemplateVersion template) {
        if (template.getReportType() == ReportTemplateType.FORM_04_RECEPTION_12W) return true;
        String text = ((template.getFileName() != null ? template.getFileName() : "") + " "
            + (template.getTitle() != null ? template.getTitle() : "")).toLowerCase()
            .replace(" ", "").replace("_", "");
        return text.contains("12tuan") || text.contains("12tuần");
    }

    /** Hạng ưu tiên (nhỏ hơn = tốt hơn); -1 nghĩa là không được phát cho sinh viên này. */
    static int rank(ReportTemplateVersion template, TemplateAudience audience) {
        UUID templateDept = TemplateAccessPolicy.departmentIdOf(template);
        boolean schoolWide = templateDept == null;
        if (!schoolWide && !Objects.equals(templateDept, audience.departmentId())) {
            return -1;
        }
        String code = template.getProgramCode() != null ? template.getProgramCode().trim().toUpperCase() : "DEFAULT";
        boolean generic = "DEFAULT".equals(code) || "ALL".equals(code);
        boolean programMatch = audience.programCode() != null && code.equalsIgnoreCase(audience.programCode());
        int base = schoolWide ? 3 : 0;
        if (programMatch) return base;
        if (generic) return base + 1;
        // Mẫu riêng của ngành khác: chỉ dùng tạm khi không biết ngành của sinh viên.
        return audience.programCode() == null ? base + 2 : -1;
    }
}
