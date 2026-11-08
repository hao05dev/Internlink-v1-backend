package com.internlink.core.presentation.system.dto.request;

import com.internlink.core.shared.enums.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePostRequest {

    @NotBlank(message = "Tiêu đề bài viết không được để trống")
    private String title;

    private String slug;

    @NotNull(message = "Danh mục bài viết không được để trống")
    private PostCategory category;

    @NotBlank(message = "Tóm tắt bài viết không được để trống")
    private String summary;

    @NotBlank(message = "Nội dung bài viết không được để trống")
    private String content;

    private String coverImageUrl;

    private List<Map<String, Object>> attachmentUrls;

    private List<Map<String, Object>> galleryImages;

    private List<String> tags;

    private Boolean isPinned;

    private String visibility;

    private String status;
}
