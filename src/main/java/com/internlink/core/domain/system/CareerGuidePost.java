package com.internlink.core.domain.system;

import com.internlink.core.domain.auth.User;
import com.internlink.core.domain.organization.Department;
import com.internlink.core.shared.domain.BaseEntity;
import com.internlink.core.shared.enums.PostCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "career_guide_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareerGuidePost extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User author;

    @Column(name = "slug", nullable = false, unique = true, length = 255)
    private String slug;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private PostCategory category;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "cover_image_url", columnDefinition = "text")
    private String coverImageUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "attachment_urls", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> attachmentUrls = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "gallery_images", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> galleryImages = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    @Column(name = "tags", columnDefinition = "jsonb", nullable = false)
    private List<String> tags = List.of();

    @Builder.Default
    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned = false;

    @Builder.Default
    @Column(name = "visibility", nullable = false, length = 30)
    private String visibility = "PUBLIC";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "PUBLISHED";

    @Builder.Default
    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;
}
