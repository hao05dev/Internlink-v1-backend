package com.internlink.core.presentation.system.controller;

import com.internlink.core.application.system.upload.MediaAssetService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MediaAssetControllerTest {
    private final FakeMediaAssetService service = new FakeMediaAssetService();
    private final MediaAssetController controller = new MediaAssetController(service);
    private final UUID id = UUID.randomUUID();
    private final MediaAssetService.Metadata file = new MediaAssetService.Metadata(
            "media/file.pdf", "application/pdf", "KeHoach.pdf", 10, "abc123");

    @Test
    void previewIsInlineAndPubliclyCacheable() {
        service.file = file;

        var response = controller.publicPreview(id, new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(response.getHeaders().getCacheControl()).contains("public").contains("max-age");
        assertThat(response.getHeaders().getFirst(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }

    @Test
    void downloadRemainsAnAttachment() {
        service.file = file;

        var response = controller.publicDownload(id, new MockHttpServletRequest());

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void legacyImageUrlRemainsInlineForExistingPages() {
        service.file = new MediaAssetService.Metadata(
                "media/logo.png", "image/png", "logo.png", 10, "image123");

        var response = controller.publicFile(id, new MockHttpServletRequest());

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    }

    @Test
    void singleRangeReturnsOnlyRequestedBytes() {
        service.file = file;
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.RANGE, "bytes=2-5");

        var response = controller.publicPreview(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(response.getHeaders().getContentLength()).isEqualTo(4);
        assertThat(service.rangeLoads).isEqualTo(1);
        assertThat(service.fullLoads).isZero();
    }

    @Test
    void matchingEtagAvoidsLoadingR2Content() {
        service.file = file;
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"abc123\"");

        var response = controller.publicPreview(id, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(service.fullLoads).isZero();
        assertThat(service.rangeLoads).isZero();
    }

    private static final class FakeMediaAssetService extends MediaAssetService {
        private Metadata file;
        private int fullLoads;
        private int rangeLoads;

        private FakeMediaAssetService() {
            super(null, null, null, null, null);
        }

        @Override
        public Metadata metadata(UUID id, boolean publicEndpoint) {
            return file;
        }

        @Override
        public org.springframework.core.io.Resource content(Metadata file) {
            fullLoads++;
            return new ByteArrayResource(new byte[Math.toIntExact(file.size())]);
        }

        @Override
        public org.springframework.core.io.Resource content(Metadata file, long start, long end) {
            rangeLoads++;
            return new ByteArrayResource(new byte[Math.toIntExact(end - start + 1)]);
        }
    }
}
