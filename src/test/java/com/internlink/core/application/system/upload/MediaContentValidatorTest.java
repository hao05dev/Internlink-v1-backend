package com.internlink.core.application.system.upload;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

class MediaContentValidatorTest {
    final MediaContentValidator validator =
            new MediaContentValidator(new DocumentContentValidator());

    static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void decodesActualImageAndDoesNotTrustClientMime() throws Exception {
        var content =
                validator.validate(
                        new MockMultipartFile(
                                "file", "photo.png", "application/octet-stream", png()),
                        MediaPurpose.JOB_BANNER);
        assertThat(content.mime()).isEqualTo("image/png");
    }

    @Test
    void rejectsForgedImageHeadersAndMismatchedExtension() throws Exception {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        new MockMultipartFile(
                                                "file",
                                                "photo.png",
                                                "image/png",
                                                new byte[] {
                                                    (byte) 137, 80, 78, 71, 13, 10, 26, 10
                                                }),
                                        MediaPurpose.JOB_BANNER))
                .hasMessageContaining("Ảnh");
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        new MockMultipartFile(
                                                "file", "photo.jpg", "image/jpeg", png()),
                                        MediaPurpose.JOB_BANNER))
                .hasMessageContaining("Phần mở rộng");
    }

    @Test
    void rejectsSvgAndNonImageBanner() throws Exception {
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        new MockMultipartFile(
                                                "file",
                                                "photo.svg",
                                                "image/svg+xml",
                                                "<svg/>".getBytes()),
                                        MediaPurpose.POST_COVER))
                .hasMessageContaining("PNG");
    }

    @Test
    void jobAttachmentAcceptsSameDocumentTypesAsPosts() throws Exception {
        var content =
                validator.validate(
                        new MockMultipartFile(
                                "file",
                                "readme.txt",
                                "text/plain",
                                "Nội dung".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        MediaPurpose.JOB_ATTACHMENT);
        assertThat(content.mime()).isEqualTo("text/plain");
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        new MockMultipartFile(
                                                "file",
                                                "setup.exe",
                                                "application/octet-stream",
                                                new byte[] {77, 90, 0, 1}),
                                        MediaPurpose.JOB_ATTACHMENT))
                .hasMessageContaining("không được hỗ trợ");
    }
}
