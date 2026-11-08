package com.internlink.core.application.system.impl;

import com.internlink.core.domain.system.CareerGuidePost;
import com.internlink.core.presentation.system.dto.response.PostResponse;
import org.springframework.stereotype.Component;

@Component
public class CareerGuidePostMapper {

    public PostResponse toResponse(CareerGuidePost post) {
        if (post == null) return null;

        return PostResponse.builder()
            .id(post.getId())
            .departmentId(post.getDepartment() != null ? post.getDepartment().getId() : null)
            .departmentName(post.getDepartment() != null ? post.getDepartment().getName() : null)
            .authorUserId(post.getAuthor() != null ? post.getAuthor().getId() : null)
            .authorName(post.getAuthor() != null ? post.getAuthor().getFullName() : null)
            .slug(post.getSlug())
            .title(post.getTitle())
            .category(post.getCategory())
            .summary(post.getSummary())
            .content(post.getContent())
            .coverImageUrl(post.getCoverImageUrl())
            .attachmentUrls(post.getAttachmentUrls())
            .galleryImages(post.getGalleryImages())
            .tags(post.getTags())
            .isPinned(post.getIsPinned())
            .visibility(post.getVisibility())
            .status(post.getStatus())
            .viewCount(post.getViewCount())
            .publishedAt(post.getPublishedAt())
            .createdAt(post.getCreatedAt())
            .updatedAt(post.getUpdatedAt())
            .build();
    }
}
