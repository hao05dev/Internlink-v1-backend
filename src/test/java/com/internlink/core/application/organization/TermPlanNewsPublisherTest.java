package com.internlink.core.application.organization;

import com.internlink.core.application.organization.TermPlanDocumentService.PlanDocument;
import com.internlink.core.application.organization.TermPlanService.MilestoneItem;
import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.InternshipTerm;
import com.internlink.core.domain.system.CareerGuidePost;
import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaCareerGuidePostRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.shared.enums.PostCategory;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermPlanNewsPublisherTest {
    private final JpaCareerGuidePostRepository posts = mock(JpaCareerGuidePostRepository.class);
    private final JpaUserRepository users = mock(JpaUserRepository.class);
    private final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    private final TermPlanNewsPublisher publisher = new TermPlanNewsPublisher(posts, users, assets);
    private final OffsetDateTime sep6 = OffsetDateTime.parse("2026-09-06T23:59:00+07:00");
    private final OffsetDateTime sep14 = OffsetDateTime.parse("2026-09-14T00:00:00+07:00");
    private final OffsetDateTime nov7 = OffsetDateTime.parse("2026-11-07T23:59:00+07:00");

    private InternshipTerm term() {
        var term = new InternshipTerm();
        term.setTermName("Thực tập tốt nghiệp HK1 2026-2027");
        term.setAcademicYear("2026-2027");
        term.setPlanDocumentRef("349/CNTT&TT-GV");
        return term;
    }

    private List<MilestoneItem> milestones() {
        return List.of(
            new MilestoneItem(null, "ACCEPTANCE_FORM_DUE", "SV nộp Phiếu tiếp nhận", "Phải đăng ký học phần", null, sep6, null, 0, "ALLOW_LATE"),
            new MilestoneItem(null, "INTERNSHIP_PERIOD", "Thực tập 8 tuần", null, sep14, nov7, 8, 1, "ALLOW_LATE"));
    }

    @Test
    void contentHasDocumentRefDatesInVietnamTimeAndAudience() {
        String md = TermPlanNewsPublisher.content(term(), milestones(), true);
        assertThat(md).contains("**349/CNTT&TT-GV**")
            .contains("| SV nộp Phiếu tiếp nhận _(Phải đăng ký học phần)_ | Đến 06/09/2026 | Mọi sinh viên |")
            .contains("| Thực tập 8 tuần | 14/09/2026 – 07/11/2026 | Học phần 2–3 tín chỉ (8 tuần) |")
            .contains("tệp đính kèm");
        assertThat(TermPlanNewsPublisher.range(sep14, null)).isEqualTo("Từ 14/09/2026");
        assertThat(TermPlanNewsPublisher.title(term())).isEqualTo("Kế hoạch thực tập tốt nghiệp HK1 2026-2027");
    }

    @Test
    void firstPublishCreatesPinnedPublicPostAuthoredByTheUploader() {
        var term = term();
        var uploader = new User();
        uploader.setId(UUID.randomUUID());
        var doc = new PlanDocument(UUID.randomUUID(), "/api/v1/public/media/x", "KeHoach.pdf", "application/pdf", 2048);
        when(assets.get(doc.id())).thenReturn(new MediaAssetRepository.Asset(doc.id(), uploader.getId(), null,
            uploader.getId(), null, "k", "KeHoach.pdf", "application/pdf", 2048, "c"));
        when(users.findById(uploader.getId())).thenReturn(Optional.of(uploader));
        when(posts.existsBySlug(any())).thenReturn(false);
        when(posts.save(any())).thenAnswer(inv -> {
            CareerGuidePost p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        String slug = publisher.publish(term, milestones(), new User(), Optional.of(doc));

        assertThat(slug).isEqualTo("ke-hoach-thuc-tap-tot-nghiep-hk1-2026-2027");
        assertThat(term.getPlanNewsPostId()).isNotNull();
        verify(posts).save(argThat(p -> p.getAuthor() == uploader && p.getIsPinned()
            && "PUBLIC".equals(p.getVisibility()) && "PUBLISHED".equals(p.getStatus())
            && p.getCategory() == PostCategory.REGULATIONS_GUIDELINES
            && TermPlanNewsPublisher.DEFAULT_COVER.equals(p.getCoverImageUrl())
            && p.getAttachmentUrls().size() == 1 && "2 KB".equals(p.getAttachmentUrls().get(0).get("fileSize"))));
    }

    @Test
    void republishUpdatesTheSamePostAndKeepsItsSlug() {
        var term = term();
        var existing = CareerGuidePost.builder().slug("ke-hoach-cu").viewCount(5)
            .coverImageUrl("/api/v1/public/media/anh-van-phong-tu-chon").build();
        existing.setId(UUID.randomUUID());
        term.setPlanNewsPostId(existing.getId());
        when(posts.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(posts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String slug = publisher.publish(term, milestones(), new User(), Optional.empty());

        assertThat(slug).isEqualTo("ke-hoach-cu");
        assertThat(existing.getViewCount()).isEqualTo(5);
        assertThat(existing.getAttachmentUrls()).isEmpty();
        assertThat(existing.getCoverImageUrl()).isEqualTo("/api/v1/public/media/anh-van-phong-tu-chon");
        verify(posts, never()).existsBySlug(any());
    }
}
