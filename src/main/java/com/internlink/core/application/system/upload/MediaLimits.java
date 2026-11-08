package com.internlink.core.application.system.upload;

import java.util.List;

/** Giới hạn upload trả cho frontend để không phải hard-code. */
public record MediaLimits(
        long maxFileBytes,
        long maxImageBytes,
        int maxGalleryItems,
        int maxAttachments,
        long maxTotalAttachmentBytes,
        List<String> accept) {
    static final long IMAGE_BYTES = 5L * 1024 * 1024;
    static final long ATTACHMENT_BYTES = 20L * 1024 * 1024;
    static final List<String> IMAGE_EXT = List.of(".png", ".jpg", ".jpeg");
    static final List<String> ATTACHMENT_EXT =
            List.of(".pdf", ".docx", ".xlsx", ".txt", ".png", ".jpg", ".jpeg");

    public static MediaLimits of(MediaPurpose purpose) {
        boolean attachment = purpose.attachment();
        return new MediaLimits(
                attachment ? ATTACHMENT_BYTES : IMAGE_BYTES,
                IMAGE_BYTES,
                MediaGallery.MAX_IMAGES,
                MediaGallery.MAX_ATTACHMENTS,
                MediaGallery.MAX_TOTAL_ATTACHMENT_BYTES,
                attachment ? ATTACHMENT_EXT : IMAGE_EXT);
    }
}
