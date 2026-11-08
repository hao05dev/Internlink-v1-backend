package com.internlink.core.application.system.impl;

import com.internlink.core.application.system.CareerGuidePostService;
import com.internlink.core.domain.auth.User;
import com.internlink.core.application.system.upload.MediaGallery;
import com.internlink.core.domain.system.CareerGuidePost;
import com.internlink.core.infrastructure.persistence.jpa.JpaCareerGuidePostRepository;
import com.internlink.core.infrastructure.persistence.jpa.JpaUserRepository;
import com.internlink.core.infrastructure.persistence.jpa.specification.CareerGuidePostSpecifications;
import com.internlink.core.presentation.system.dto.request.CreatePostRequest;
import com.internlink.core.presentation.system.dto.request.UpdatePostRequest;
import com.internlink.core.presentation.system.dto.response.PostResponse;
import com.internlink.core.shared.enums.PostCategory;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerGuidePostServiceImpl implements CareerGuidePostService {

    private final JpaCareerGuidePostRepository postRepository;
    private final JpaUserRepository userRepository;
    private final CareerGuidePostMapper postMapper;
    private final com.internlink.core.application.system.upload.MediaReferencePolicy media;
    private final com.internlink.core.application.system.PostViewGuardService postViewGuardService;

    private static final Sort DEFAULT_POST_SORT =
            Sort.by(
                    Sort.Order.desc("isPinned"),
                    Sort.Order.desc("publishedAt").nullsLast(),
                    Sort.Order.desc("createdAt"));

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getPublishedPosts(PostCategory category, String search) {
        Specification<CareerGuidePost> spec =
                CareerGuidePostSpecifications.withFilters(category, "PUBLISHED", search);
        List<CareerGuidePost> posts = postRepository.findAll(spec, DEFAULT_POST_SORT);
        return posts.stream().map(postMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPublishedPostBySlug(String slug) {
        CareerGuidePost post =
                postRepository
                        .findBySlug(slug)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Bài viết cẩm nang", "slug", slug));

        if (!"PUBLISHED".equalsIgnoreCase(post.getStatus())) {
            throw new ResourceNotFoundException(
                    "Bài viết cẩm nang không khả dụng hoặc chưa được xuất bản.");
        }

        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public PostResponse recordView(String slug, String visitorIdentity) {
        CareerGuidePost post =
                postRepository
                        .findBySlug(slug)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Bài viết cẩm nang", "slug", slug));

        if (!"PUBLISHED".equalsIgnoreCase(post.getStatus())) {
            throw new ResourceNotFoundException(
                    "Bài viết cẩm nang không khả dụng hoặc chưa được xuất bản.");
        }

        // Kiểm tra điều kiện duy nhất 1 view trong 24 giờ cho cùng 1 người thật
        if (postViewGuardService.recordViewIfEligible(post.getId(), visitorIdentity)) {
            postRepository.incrementViewCount(post.getId());
            post.setViewCount(post.getViewCount() + 1);
            log.info("Ghi nhận lượt xem hợp lệ mới cho bài viết [{}]. Tổng views: {}", slug, post.getViewCount());
        }

        return postMapper.toResponse(post);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PostResponse> getAllPostsForManagement(
            PostCategory category, String status, String search) {
        Specification<CareerGuidePost> spec =
                CareerGuidePostSpecifications.withFilters(category, status, search);
        List<CareerGuidePost> posts = postRepository.findAll(spec, DEFAULT_POST_SORT);
        return posts.stream().map(postMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID id) {
        CareerGuidePost post =
                postRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Bài viết cẩm nang", "id", id));
        return postMapper.toResponse(post);
    }

    @Override
    @Transactional
    public PostResponse createPost(UUID authorUserId, CreatePostRequest request) {
        User author =
                userRepository
                        .findById(authorUserId)
                        .orElseThrow(
                                () ->
                                        new ResourceNotFoundException(
                                                "Người dùng", "id", authorUserId));

        String targetSlug =
                (request.getSlug() != null && !request.getSlug().isBlank())
                        ? PostSlugNormalizer.normalize(request.getSlug())
                        : generateUniqueSlug(request.getTitle(), null);

        if (postRepository.existsBySlug(targetSlug)) {
            targetSlug = generateUniqueSlug(request.getTitle(), null);
        }

        String status =
                (request.getStatus() != null && !request.getStatus().isBlank())
                        ? request.getStatus().toUpperCase(Locale.ROOT)
                        : "PUBLISHED";

        OffsetDateTime publishedAt = "PUBLISHED".equals(status) ? OffsetDateTime.now() : null;

        CareerGuidePost post =
                CareerGuidePost.builder()
                        .author(author)
                        .department(author.getDepartment())
                        .title(request.getTitle().trim())
                        .slug(targetSlug)
                        .category(request.getCategory())
                        .summary(request.getSummary().trim())
                        .content(request.getContent().trim())
                        .coverImageUrl(request.getCoverImageUrl())
                        .galleryImages(MediaGallery.normalize(request.getGalleryImages()))
                        .attachmentUrls(
                                request.getAttachmentUrls() != null
                                        ? request.getAttachmentUrls()
                                        : List.of())
                        .tags(request.getTags() != null ? request.getTags() : List.of())
                        .isPinned(Boolean.TRUE.equals(request.getIsPinned()))
                        .visibility(
                                request.getVisibility() != null
                                        ? request.getVisibility()
                                        : "PUBLIC")
                        .status(status)
                        .viewCount(0)
                        .publishedAt(publishedAt)
                        .build();

        applyMediaRules(post);
        CareerGuidePost saved = postRepository.save(post);
        log.info("Created new CareerGuidePost id={} title='{}'", saved.getId(), saved.getTitle());
        return postMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PostResponse updatePost(UUID id, UpdatePostRequest request) {
        CareerGuidePost post =
                postRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Bài viết cẩm nang", "id", id));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            post.setTitle(request.getTitle().trim());
        }

        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String newSlug = PostSlugNormalizer.normalize(request.getSlug());
            if (postRepository.existsBySlugAndIdNot(newSlug, id)) {
                throw new BadRequestException(
                        "Đường dẫn (slug) '" + newSlug + "' đã tồn tại ở bài viết khác.");
            }
            post.setSlug(newSlug);
        }

        if (request.getCategory() != null) {
            post.setCategory(request.getCategory());
        }

        if (request.getSummary() != null && !request.getSummary().isBlank()) {
            post.setSummary(request.getSummary().trim());
        }

        if (request.getContent() != null && !request.getContent().isBlank()) {
            post.setContent(request.getContent().trim());
        }

        if (request.getCoverImageUrl() != null) {
            post.setCoverImageUrl(request.getCoverImageUrl());
        }

        if (request.getAttachmentUrls() != null) {
            post.setAttachmentUrls(request.getAttachmentUrls());
        }

        if (request.getGalleryImages() != null) {
            post.setGalleryImages(MediaGallery.normalize(request.getGalleryImages()));
        }

        if (request.getTags() != null) {
            post.setTags(request.getTags());
        }

        if (request.getIsPinned() != null) {
            post.setIsPinned(request.getIsPinned());
        }

        if (request.getVisibility() != null) {
            post.setVisibility(request.getVisibility());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String newStatus = request.getStatus().toUpperCase(Locale.ROOT);
            if ("PUBLISHED".equals(newStatus)
                    && !"PUBLISHED".equals(post.getStatus())
                    && post.getPublishedAt() == null) {
                post.setPublishedAt(OffsetDateTime.now());
            }
            post.setStatus(newStatus);
        }

        applyMediaRules(post);
        CareerGuidePost saved = postRepository.save(post);
        log.info("Updated CareerGuidePost id={} title='{}'", saved.getId(), saved.getTitle());
        return postMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void deletePost(UUID id) {
        CareerGuidePost post =
                postRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Bài viết cẩm nang", "id", id));
        postRepository.delete(post);
        log.info("Deleted CareerGuidePost id={}", id);
    }

    @Override
    @Transactional
    public PostResponse togglePinPost(UUID id) {
        CareerGuidePost post =
                postRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Bài viết cẩm nang", "id", id));
        post.setIsPinned(!Boolean.TRUE.equals(post.getIsPinned()));
        applyMediaRules(post);
        CareerGuidePost saved = postRepository.save(post);
        return postMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PostResponse updatePostStatus(UUID id, String status) {
        CareerGuidePost post =
                postRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("Bài viết cẩm nang", "id", id));

        String newStatus = status.toUpperCase(Locale.ROOT);
        if ("PUBLISHED".equals(newStatus)
                && !"PUBLISHED".equals(post.getStatus())
                && post.getPublishedAt() == null) {
            post.setPublishedAt(OffsetDateTime.now());
        }
        post.setStatus(newStatus);
        applyMediaRules(post);
        CareerGuidePost saved = postRepository.save(post);
        return postMapper.toResponse(saved);
    }

    /** Ảnh bìa mặc định = ảnh đầu của gallery, rồi kiểm tra quyền sở hữu mọi ảnh/tệp. */
    private void applyMediaRules(CareerGuidePost post) {
        post.setCoverImageUrl(
                MediaGallery.coverFor(post.getGalleryImages(), post.getCoverImageUrl()));
        media.post(
                post.getAuthor().getId(),
                post.getCoverImageUrl(),
                post.getGalleryImages(),
                post.getAttachmentUrls(),
                post.getContent());
    }

    private String generateUniqueSlug(String title, UUID currentId) {
        String baseSlug = PostSlugNormalizer.normalize(title);
        if (baseSlug.isBlank()) {
            baseSlug = "bai-viet-" + System.currentTimeMillis();
        }
        String candidate = baseSlug;
        int counter = 1;

        while (true) {
            boolean exists =
                    (currentId == null)
                            ? postRepository.existsBySlug(candidate)
                            : postRepository.existsBySlugAndIdNot(candidate, currentId);

            if (!exists) {
                return candidate;
            }
            candidate = baseSlug + "-" + counter;
            counter++;
        }
    }

}
