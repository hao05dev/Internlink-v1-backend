package com.internlink.core.application.system.upload;

import com.internlink.core.infrastructure.integration.storage.R2StorageService;
import com.internlink.core.shared.exception.BadRequestException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

import javax.imageio.ImageIO;

@Component
@RequiredArgsConstructor
public class MediaContentValidator {
    private final DocumentContentValidator documents;

    public record Content(String name, String mime, byte[] bytes) {}

    public Content validate(MultipartFile file, MediaPurpose purpose) throws IOException {
        long max = purpose.attachment() ? 20L * 1024 * 1024 : 5L * 1024 * 1024;
        if (file == null || file.isEmpty() || file.getSize() > max)
            throw new BadRequestException(
                    "Tệp rỗng hoặc vượt giới hạn " + (max / 1024 / 1024) + " MiB");
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes((int) max + 1);
        }
        if (bytes.length == 0 || bytes.length > max)
            throw new BadRequestException("Dung lượng thực tế không hợp lệ");
        String name = R2StorageService.safeName(file.getOriginalFilename());
        String ext =
                name.contains(".")
                        ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                        : "";
        if (!Set.of("png", "jpg", "jpeg").contains(ext)) {
            if (!purpose.attachment()) throw new BadRequestException("Chỉ nhận ảnh PNG hoặc JPEG");
            String mime = DocumentContentValidator.MIME.get(ext);
            if (mime == null) throw new BadRequestException("Định dạng đính kèm không được hỗ trợ");
            documents.validate(name, mime, bytes);
            return new Content(name, mime, bytes);
        }
        if (bytes.length > 5 * 1024 * 1024) throw new BadRequestException("Ảnh tối đa 5 MiB");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BadRequestException("Nội dung không phải ảnh hợp lệ");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("png", "jpeg").contains(format)
                        || ("png".equals(ext) != "png".equals(format)))
                    throw new BadRequestException("Phần mở rộng không khớp ảnh");
                long width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1
                        || height < 1
                        || width > 8192
                        || height > 8192
                        || width * height > 16_000_000)
                    throw new BadRequestException("Ảnh vượt giới hạn kích thước điểm ảnh");
                var image = reader.read(0);
                if (image == null) throw new BadRequestException("Không giải mã được ảnh");
                return new Content(name, "png".equals(format) ? "image/png" : "image/jpeg", bytes);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            throw new BadRequestException("Ảnh bị lỗi hoặc không đầy đủ");
        }
    }
}
