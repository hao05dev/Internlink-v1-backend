package com.internlink.core.presentation.system.controller;

import com.internlink.core.application.system.CareerGuidePostService;
import com.internlink.core.infrastructure.security.CustomUserDetail;
import com.internlink.core.presentation.system.dto.request.CreatePostRequest;
import com.internlink.core.presentation.system.dto.request.UpdatePostRequest;
import com.internlink.core.presentation.system.dto.response.PostResponse;
import com.internlink.core.shared.api.ApiResponse;
import com.internlink.core.shared.enums.PostCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class CareerGuidePostController {

    private final CareerGuidePostService postService;

    /**
     * Lấy danh sách bài viết cẩm nang & sự kiện đã xuất bản (Công khai).
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getPublishedPosts(
        @RequestParam(required = false) PostCategory category,
        @RequestParam(required = false) String search
    ) {
        List<PostResponse> posts = postService.getPublishedPosts(category, search);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    /**
     * Lấy chi tiết bài viết đã xuất bản theo đường dẫn slug (Công khai).
     * Tự động tăng lượt xem.
     */
    @GetMapping("/public/by-slug/{slug}")
    public ResponseEntity<ApiResponse<PostResponse>> getPublishedPostBySlug(
        @PathVariable String slug
    ) {
        PostResponse post = postService.getPublishedPostBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    /**
     * Ghi nhận lượt xem hợp lệ (mỗi người thật tối đa 1 view trong 24 giờ).
     */
    @PostMapping("/public/by-slug/{slug}/view")
    public ResponseEntity<ApiResponse<PostResponse>> recordPostView(
        @PathVariable String slug,
        @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
        jakarta.servlet.http.HttpServletRequest request
    ) {
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        String identityKey = (visitorId != null && !visitorId.isBlank())
            ? visitorId.trim() + "_" + clientIp
            : "ip_" + clientIp;

        PostResponse post = postService.recordView(slug, identityKey);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    /**
     * Lấy chi tiết bài viết công khai theo ID.
     */
    @GetMapping("/public/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getPublicPostById(
        @PathVariable UUID id
    ) {
        PostResponse post = postService.getPostById(id);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    /**
     * Lấy danh sách tất cả bài viết cho quản lý Khoa / Admin.
     */
    @GetMapping("/management")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<PostResponse>>> getAllPostsForManagement(
        @RequestParam(required = false) PostCategory category,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String search
    ) {
        List<PostResponse> posts = postService.getAllPostsForManagement(category, status, search);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    /**
     * Xem chi tiết bài viết quản lý theo ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> getPostById(
        @PathVariable UUID id
    ) {
        PostResponse post = postService.getPostById(id);
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    /**
     * Tạo mới bài viết cẩm nang / sự kiện / ngày hội việc làm.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
        @AuthenticationPrincipal CustomUserDetail userDetail,
        @Valid @RequestBody CreatePostRequest request
    ) {
        PostResponse post = postService.createPost(userDetail.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success("Tạo bài viết thành công", post));
    }

    /**
     * Cập nhật bài viết cẩm nang / sự kiện.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
        @PathVariable UUID id,
        @Valid @RequestBody UpdatePostRequest request
    ) {
        PostResponse post = postService.updatePost(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bài viết thành công", post));
    }

    /**
     * Xóa bài viết.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePost(
        @PathVariable UUID id
    ) {
        postService.deletePost(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa bài viết thành công", null));
    }

    /**
     * Ghim / Bỏ ghim bài viết lên đầu trang.
     */
    @PatchMapping("/{id}/toggle-pin")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> togglePinPost(
        @PathVariable UUID id
    ) {
        PostResponse post = postService.togglePinPost(id);
        return ResponseEntity.ok(ApiResponse.success("Thay đổi trạng thái ghim bài viết thành công", post));
    }

    /**
     * Cập nhật trạng thái bài viết (PUBLISHED, DRAFT, ARCHIVED).
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SCHOOL_OFFICE', 'ADMIN')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePostStatus(
        @PathVariable UUID id,
        @RequestParam String status
    ) {
        PostResponse post = postService.updatePostStatus(id, status);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái bài viết thành công", post));
    }
}
