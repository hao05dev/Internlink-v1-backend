package com.internlink.core.presentation.system.dto.response;

import com.internlink.core.shared.enums.PostCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private UUID id;
    private UUID departmentId;
    private String departmentName;
    private UUID authorUserId;
    private String authorName;
    private String slug;
    private String title;
    private PostCategory category;
    private String summary;
    private String content;
    private String coverImageUrl;
    private List<Map<String, Object>> attachmentUrls;
    private List<Map<String, Object>> galleryImages;
    private List<String> tags;
    private Boolean isPinned;
    private String visibility;
    private String status;
    private Integer viewCount;
    private OffsetDateTime publishedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
