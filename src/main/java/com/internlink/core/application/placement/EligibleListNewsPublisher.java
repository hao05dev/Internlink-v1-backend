package com.internlink.core.application.placement;

import com.internlink.core.application.system.impl.PostSlugNormalizer;
import com.internlink.core.application.system.upload.MediaAssetService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.system.CareerGuidePost;
import com.internlink.core.infrastructure.persistence.jpa.JpaCareerGuidePostRepository;
import com.internlink.core.shared.enums.PostCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Công bố Danh sách SV đủ điều kiện TTTT lên trang Tin tức: mỗi kỳ một bài ghim công khai,
 * mỗi đợt công bố thêm một tệp Excel (sinh từ DB) vào bài đó, giữ nguyên đường dẫn đã gửi trong thông báo/email.
 */
@Component
@RequiredArgsConstructor
public class EligibleListNewsPublisher {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    static final String DEFAULT_COVER = "/images/news-term-plan-cover.jpg";

    private final JpaCareerGuidePostRepository posts;
    private final MediaAssetService media;

    /** Một đợt đã công bố: số thứ tự, thời điểm, số SV. */
    public record Batch(int number, OffsetDateTime publishedAt, int studentCount) {}

    /** Tác giả bài viết của kỳ; tệp đính kèm phải thuộc đúng tác giả này mới được tải công khai. */
    public Optional<CareerGuidePost> existing(InternshipTerm term) {
        return Optional.ofNullable(term.getEligibleListPostId()).flatMap(posts::findById);
    }

    /**
     * Lưu tệp Excel của đợt mới, rồi tạo/cập nhật bài tin của kỳ.
     * @return slug của bài tin để thông báo và email dẫn tới.
     */
    public String publish(InternshipTerm term, User publisher, List<Batch> batches, byte[] workbook,
                          String fileName, String note) {
        CareerGuidePost post = existing(term)
            .orElseGet(() -> CareerGuidePost.builder().slug(uniqueSlug(term)).viewCount(0).author(publisher).build());
        if (post.getAuthor() == null) post.setAuthor(publisher);
        var file = media.storeGeneratedPostAttachment(workbook, fileName, EligibleListWorkbook.MIME,
            publisher.getId(), post.getAuthor().getId());

        List<Map<String, Object>> attachments = new ArrayList<>(
            post.getAttachmentUrls() == null ? List.of() : post.getAttachmentUrls());
        attachments.add(0, attachment(file));
        Batch latest = batches.get(batches.size() - 1);

        post.setDepartment(null);
        post.setTitle(title(term));
        post.setCategory(PostCategory.REGULATIONS_GUIDELINES);
        post.setSummary("Văn phòng Trường CNTT-TT công bố đợt " + latest.number() + " ("
            + latest.studentCount() + " sinh viên). Sinh viên có tên đến sảnh Văn phòng Trường nhận giấy tờ thực tập.");
        if (post.getCoverImageUrl() == null || post.getCoverImageUrl().isBlank()) post.setCoverImageUrl(DEFAULT_COVER);
        post.setContent(content(term, batches, note));
        post.setAttachmentUrls(attachments);
        post.setTags(List.of("Danh sách TTTT", term.getAcademicYear() == null ? "Thực tập" : term.getAcademicYear()));
        post.setIsPinned(true);
        post.setVisibility("PUBLIC");
        post.setStatus("PUBLISHED");
        post.setPublishedAt(OffsetDateTime.now());
        var saved = posts.save(post);
        term.setEligibleListPostId(saved.getId());
        return saved.getSlug();
    }

    static String title(InternshipTerm term) {
        String name = Objects.toString(term.getTermName(), "");
        return "Danh sách sinh viên đủ điều kiện "
            + (name.isEmpty() ? "thực tập thực tế" : Character.toLowerCase(name.charAt(0)) + name.substring(1));
    }

    static String content(InternshipTerm term, List<Batch> batches, String note) {
        StringBuilder md = new StringBuilder();
        md.append("Văn phòng Trường CNTT-TT công bố danh sách sinh viên đủ điều kiện thực tập thực tế ")
            .append(term.getTermName()).append(". Danh sách chi tiết của từng đợt nằm trong các tệp Excel đính kèm bên dưới.\n\n");
        if (note != null && !note.isBlank())
            md.append("> **Thông báo đợt ").append(batches.get(batches.size() - 1).number()).append(":** ")
                .append(note.trim().replace("\n", " ")).append("\n\n");
        md.append("## Các đợt đã công bố\n\n| Đợt | Thời điểm công bố | Số sinh viên |\n|---|---|---|\n");
        for (Batch b : batches)
            md.append("| Đợt ").append(b.number()).append(" | ")
                .append(b.publishedAt().atZoneSameInstant(VN).format(DATE_TIME)).append(" | ")
                .append(b.studentCount()).append(" |\n");
        md.append("\n## Sinh viên lưu ý\n\n")
            .append("- Sinh viên có tên trong danh sách đến **sảnh Văn phòng Trường CNTT-TT** nhận giấy tờ liên quan để đi thực tập thực tế.\n")
            .append("- Mang theo thẻ sinh viên khi nhận giấy tờ.\n")
            .append("- Sinh viên chưa có tên: kiểm tra trạng thái Phiếu tiếp nhận trên InternLink.\n");
        return md.toString();
    }

    private String uniqueSlug(InternshipTerm term) {
        String base = "danh-sach-sv-du-dieu-kien-" + PostSlugNormalizer.normalize(Objects.toString(term.getTermName(), "tttt"));
        String slug = base;
        for (int i = 2; posts.existsBySlug(slug); i++) slug = base + "-" + i;
        return slug;
    }

    private static Map<String, Object> attachment(MediaAssetService.Uploaded f) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", f.id().toString());
        item.put("fileName", f.fileName());
        item.put("fileUrl", f.url());
        item.put("fileSize", Math.max(1, Math.round(f.sizeBytes() / 1024.0)) + " KB");
        item.put("fileType", "XLSX");
        return item;
    }
}
