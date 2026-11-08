package com.internlink.core.application.system.upload;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;

import org.junit.jupiter.api.Test;

import java.util.*;

class MediaGalleryPolicyTest {
    final MediaAssetRepository assets = mock(MediaAssetRepository.class);
    final MediaReferencePolicy policy = new MediaReferencePolicy(assets);
    final UUID author = UUID.randomUUID();

    MediaAssetRepository.Asset asset(MediaPurpose purpose, UUID context, long size) {
        var a = new MediaAssetRepository.Asset(UUID.randomUUID(), author, purpose, context, null,
                "key", "a.png", "image/png", size, "sum");
        when(assets.get(a.id())).thenReturn(a);
        return a;
    }

    static Map<String, Object> item(MediaAssetRepository.Asset a) {
        return Map.of("mediaId", a.id().toString(), "url", a.url(), "fileName", a.name());
    }

    @Test
    void acceptsOrderedGalleryWithCoverChosenFromIt() {
        var first = asset(MediaPurpose.POST_IMAGE, author, 10);
        var legacyCover = asset(MediaPurpose.POST_COVER, author, 10);
        var gallery = List.of(item(first), item(legacyCover));
        assertThatCode(() -> policy.post(author, legacyCover.url(), gallery, List.of(), ""))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCoverOutsideGalleryAndForeignImages() {
        var mine = asset(MediaPurpose.POST_IMAGE, author, 10);
        var other = asset(MediaPurpose.POST_IMAGE, author, 10);
        assertThatThrownBy(() -> policy.post(author, other.url(), List.of(item(mine)), List.of(), ""))
                .hasMessageContaining("thư viện ảnh");
        var foreign = asset(MediaPurpose.POST_IMAGE, UUID.randomUUID(), 10);
        assertThatThrownBy(() -> policy.post(author, null, List.of(item(foreign)), List.of(), ""))
                .hasMessageContaining("đúng tác giả");
        var wrongPurpose = asset(MediaPurpose.POST_ATTACHMENT, author, 10);
        assertThatThrownBy(() -> policy.post(author, null, List.of(item(wrongPurpose)), List.of(), ""))
                .hasMessageContaining("mục đích");
    }

    @Test
    void enforcesGalleryCountAndTotalAttachmentSize() {
        var img = asset(MediaPurpose.POST_IMAGE, author, 10);
        var tooMany = Collections.nCopies(21, item(img));
        assertThatThrownBy(() -> policy.post(author, null, tooMany, List.of(), ""))
                .hasMessageContaining("Tối đa 20");
        var files = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 6; i++)
            files.add(Map.of("fileUrl", asset(MediaPurpose.POST_ATTACHMENT, author, 20L << 20).url()));
        assertThatThrownBy(() -> policy.post(author, null, List.of(), files, ""))
                .hasMessageContaining("100 MiB");
    }

    @Test
    void normalizeKeepsOrderDropsDuplicatesAndDefaultsCoverToFirst() {
        var raw = List.<Map<String, Object>>of(
                Map.of("url", "/a", "caption", "x".repeat(400), "evil", "1"),
                Map.of("url", "/b"),
                Map.of("url", "/a"));
        var clean = MediaGallery.normalize(raw);
        assertThat(clean).extracting(m -> m.get("url")).containsExactly("/a", "/b");
        assertThat(clean.get(0)).doesNotContainKey("evil");
        assertThat((String) clean.get(0).get("caption")).hasSize(300);
        assertThat(MediaGallery.coverFor(clean, "")).isEqualTo("/a");
        assertThat(MediaGallery.coverFor(clean, "/b")).isEqualTo("/b");
    }
}
