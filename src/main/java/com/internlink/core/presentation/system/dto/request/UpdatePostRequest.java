package com.internlink.core.presentation.system.dto.request;

import com.internlink.core.shared.enums.PostCategory;
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
public class UpdatePostRequest {

    private String title;
    private String slug;
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
}
