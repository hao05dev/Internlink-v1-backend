package com.internlink.core.infrastructure.integration.storage;

import com.internlink.core.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Đọc biểu mẫu chính thức (M-TT-01..05, Biểu mẫu 04) từ R2 theo khóa cố định
 * {@code <prefix>/templates/official/<fileName>}, có fallback đọc từ thư mục cục bộ uploads/templates/official.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OfficialTemplateStore {

    static final String FOLDER = "templates/official";

    private final R2StorageService r2;

    public Optional<Resource> find(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            return Optional.empty();
        }
        String key = key(fileName);
        try {
            return Optional.of(r2.loadAsResource(key));
        } catch (Exception ex) {
            log.debug("Không tìm thấy biểu mẫu trên R2 key={}: {}. Thử tìm ở ổ đĩa cục bộ.", key, ex.getMessage());
        }

        try {
            Path localPath = Path.of("uploads", "templates", "official", fileName);
            if (Files.exists(localPath) && Files.isReadable(localPath)) {
                return Optional.of(new FileSystemResource(localPath));
            }
        } catch (Exception e) {
            log.warn("Lỗi khi kiểm tra biểu mẫu từ ổ đĩa cục bộ: {}", e.getMessage());
        }

        return Optional.empty();
    }

    public String key(String fileName) {
        String prefix = r2.prefix();
        return (prefix.isEmpty() ? "" : prefix + "/") + FOLDER + "/" + fileName;
    }
}
