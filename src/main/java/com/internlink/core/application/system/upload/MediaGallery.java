package com.internlink.core.application.system.upload;

import com.internlink.core.shared.exception.BadRequestException;

import java.util.*;

/** Quy tắc thuần cho thư viện ảnh có thứ tự (không truy cập DB). */
public final class MediaGallery {
    public static final int MAX_IMAGES = 20;
    public static final int MAX_ATTACHMENTS = 20;
    public static final long MAX_TOTAL_ATTACHMENT_BYTES = 100L * 1024 * 1024;
    private static final int MAX_CAPTION = 300;

    private MediaGallery() {}

    /** Giữ các khoá hợp lệ, cắt caption, bỏ phần tử trùng URL; giữ nguyên thứ tự. */
    public static List<Map<String, Object>> normalize(List<Map<String, Object>> items) {
        if (items == null) return List.of();
        if (items.size() > MAX_IMAGES)
            throw new BadRequestException("Tối đa " + MAX_IMAGES + " ảnh trong thư viện ảnh");
        var seen = new HashSet<String>();
        var out = new ArrayList<Map<String, Object>>();
        for (var item : items) {
            if (item == null) continue;
            String url = text(item.get("url"));
            if (url.isBlank() || !seen.add(url)) continue;
            var clean = new LinkedHashMap<String, Object>();
            clean.put("url", url);
            putIfPresent(clean, "mediaId", text(item.get("mediaId")));
            putIfPresent(clean, "fileName", text(item.get("fileName")));
            String caption = text(item.get("caption")).trim();
            if (caption.length() > MAX_CAPTION) caption = caption.substring(0, MAX_CAPTION);
            putIfPresent(clean, "caption", caption);
            out.add(clean);
        }
        return out;
    }

    /** Ảnh bìa mặc định là ảnh đầu tiên khi người dùng không chọn. */
    public static String coverFor(List<Map<String, Object>> gallery, String cover) {
        if (cover != null && !cover.isBlank()) return cover;
        if (gallery == null || gallery.isEmpty()) return cover;
        return text(gallery.get(0).get("url"));
    }

    /** Ảnh bìa nội bộ phải nằm trong gallery (khi gallery có ảnh); URL ngoài cũ được giữ. */
    public static void requireCoverInGallery(List<Map<String, Object>> gallery, String cover) {
        if (gallery == null || gallery.isEmpty() || cover == null || cover.isBlank()) return;
        if (external(cover)) return;
        boolean found = gallery.stream().anyMatch(item -> cover.equals(text(item.get("url"))));
        if (!found) throw new BadRequestException("Ảnh bìa phải được chọn từ thư viện ảnh");
    }

    static boolean external(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }
}
