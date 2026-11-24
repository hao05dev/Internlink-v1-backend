package com.internlink.core.application.organization;

import com.internlink.core.application.organization.TermPlanDocumentService.PlanDocument;
import com.internlink.core.application.organization.TermPlanService.MilestoneItem;
import com.internlink.core.application.system.impl.PostSlugNormalizer;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.system.CareerGuidePost;
import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaCareerGuidePostRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.PostCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Công bố Kế hoạch TTTT thì đăng (hoặc cập nhật) một bài ghim ở trang Tin tức công khai, kèm bảng mốc và tệp văn bản.
 * Mỗi kỳ chỉ có một bài: công bố lại sẽ sửa đúng bài cũ, giữ nguyên đường dẫn đã gửi trong thông báo.
 */
@Component
@RequiredArgsConstructor
public class TermPlanNewsPublisher {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Ảnh bìa có sẵn ở frontend (public/images); Văn phòng vẫn đổi được ảnh khác khi sửa bài. */
    static final String DEFAULT_COVER = "/images/news-term-plan-cover.jpg";

    private final JpaCareerGuidePostRepository posts;
    private final JpaUserRepository users;
    private final MediaAssetRepository assets;

    /** @return slug của bài tin để thông báo dẫn tới. */
    public String publish(InternshipTerm term, List<MilestoneItem> milestones, User publisher,
                          Optional<PlanDocument> document) {
        CareerGuidePost post = Optional.ofNullable(term.getPlanNewsPostId()).flatMap(posts::findById)
                .orElseGet(() -> CareerGuidePost.builder().slug(uniqueSlug(term)).viewCount(0).build());
        // Tệp đính kèm chỉ công khai khi bài viết do chính người tải tệp làm tác giả (MediaAccessPolicy).
        post.setAuthor(document.map(d -> assets.get(d.id()).context()).flatMap(users::findById).orElse(publisher));
        post.setDepartment(null);
        post.setTitle(title(term));
        post.setCategory(PostCategory.REGULATIONS_GUIDELINES);
        post.setSummary(summary(term, milestones));
        if (post.getCoverImageUrl() == null || post.getCoverImageUrl().isBlank()) post.setCoverImageUrl(DEFAULT_COVER);
        post.setContent(content(term, milestones, document.isPresent()));
        post.setAttachmentUrls(document.map(TermPlanNewsPublisher::attachment).map(List::of).orElse(List.of()));
        post.setTags(List.of("Kế hoạch TTTT", term.getAcademicYear() == null ? "Thực tập" : term.getAcademicYear()));
        post.setIsPinned(true);
        post.setVisibility("PUBLIC");
        post.setStatus("PUBLISHED");
        post.setPublishedAt(OffsetDateTime.now());
        var saved = posts.save(post);
        term.setPlanNewsPostId(saved.getId());
        return saved.getSlug();
    }

    /** Slug bài tin đang công khai của kỳ, để giao diện dẫn tới. */
    public Optional<String> slug(InternshipTerm term) {
        return Optional.ofNullable(term.getPlanNewsPostId()).flatMap(posts::findById)
                .filter(p -> "PUBLISHED".equals(p.getStatus())).map(CareerGuidePost::getSlug);
    }

    static String title(InternshipTerm term) {
        String name = term.getTermName();
        return "Kế hoạch " + (name.isEmpty() ? "" : Character.toLowerCase(name.charAt(0)) + name.substring(1));
    }

    private String uniqueSlug(InternshipTerm term) {
        String base = "ke-hoach-" + PostSlugNormalizer.normalize(term.getTermName());
        String slug = base;
        for (int i = 2; posts.existsBySlug(slug); i++) slug = base + "-" + i;
        return slug;
    }

    static String summary(InternshipTerm term, List<MilestoneItem> milestones) {
        return (term.getPlanDocumentRef() != null ? "Văn bản số " + term.getPlanDocumentRef() + ". " : "")
                + "Các mốc thời gian " + term.getTermName() + ": " + milestones.size()
                + " mốc từ nhận giấy giới thiệu, nộp phiếu tiếp nhận đến chấm điểm.";
    }

    static String content(InternshipTerm term, List<MilestoneItem> milestones, boolean hasDocument) {
        StringBuilder md = new StringBuilder();
        if (term.getPlanDocumentRef() != null)
            md.append("Căn cứ văn bản số **").append(term.getPlanDocumentRef()).append("** của Trường.\n\n");
        md.append("## Các mốc thời gian\n\n| Nội dung | Thời gian | Áp dụng |\n|---|---|---|\n");
        for (MilestoneItem m : milestones)
            md.append("| ").append(cell(m.label())).append(m.note() == null ? "" : " _(" + cell(m.note()) + ")_")
                    .append(" | ").append(range(m.startAt(), m.endAt()))
                    .append(" | ").append(m.requiredWeeks() == null ? "Mọi sinh viên"
                            : m.requiredWeeks() == 8 ? "Học phần 2–3 tín chỉ (8 tuần)" : "Học phần 5 tín chỉ (12 tuần)")
                    .append(" |\n");
        md.append("\n## Sinh viên lưu ý\n\n")
                .append("- Phải đăng ký học phần thực tập và đóng học phí như các học phần khác.\n")
                .append("- Đăng nhập InternLink để nộp Phiếu tiếp nhận đúng hạn; hệ thống sẽ nhắc trước các mốc quan trọng.\n");
        if (hasDocument) md.append("- Xem văn bản gốc có chữ ký trong tệp đính kèm bên dưới.\n");
        return md.toString();
    }

    static String range(OffsetDateTime start, OffsetDateTime end) {
        if (start != null && end != null) return day(start) + " – " + day(end);
        return start != null ? "Từ " + day(start) : "Đến " + day(end);
    }

    private static String day(OffsetDateTime value) {
        return value.atZoneSameInstant(VN).format(DATE);
    }

    private static String cell(String text) {
        return text.replace("|", "/").replace("\n", " ");
    }

    private static Map<String, Object> attachment(PlanDocument d) {
        String ext = d.fileName() == null || !d.fileName().contains(".") ? "PDF"
                : d.fileName().substring(d.fileName().lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", d.id().toString());
        item.put("fileName", d.fileName());
        item.put("fileUrl", d.url());
        item.put("fileSize", Math.max(1, Math.round(d.sizeBytes() / 1024.0)) + " KB");
        item.put("fileType", ext);
        return item;
    }
}
