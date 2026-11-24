package com.internlink.core.infrastructure.integration.storage;

import com.internlink.core.shared.enums.StorageProvider;
import com.internlink.core.shared.exception.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class R2StorageService implements StorageService {
    private final R2StorageProperties settings;
    private final R2ClientProvider clients;
    private final S3Client testClient;

    @Autowired
    public R2StorageService(R2StorageProperties settings, R2ClientProvider clients) {
        this.settings = settings;
        this.clients = clients;
        this.testClient = null;
    }

    R2StorageService(R2StorageProperties settings, S3Client client) {
        this.settings = settings;
        this.clients = null;
        this.testClient = client;
    }

    private S3Client client() {
        return testClient != null ? testClient : clients.client();
    }

    public String key(String folder, String name) {
        String original = safeName(name);
        String ext =
                original.contains(".")
                        ? original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                        : "";
        if (!ext.matches("\\.[a-z0-9]{1,10}")) ext = "";
        String prefix = prefix();
        return (prefix.isEmpty() ? "" : prefix + "/")
                + safeFolder(folder)
                + "/"
                + UUID.randomUUID()
                + ext;
    }

    public String prefix() {
        return settings.getPrefix().replaceAll("[^a-zA-Z0-9/_-]", "_").replaceAll("^/+|/+$", "");
    }

    public String presign(String key, String mime) {
        validateKey(key);
        return clients.presigner()
                .presignPutObject(
                        PutObjectPresignRequest.builder()
                                .signatureDuration(
                                        Duration.ofSeconds(
                                                Math.max(
                                                        60,
                                                        Math.min(
                                                                900,
                                                                settings.getPresignedSeconds()))))
                                .putObjectRequest(
                                        PutObjectRequest.builder()
                                                .bucket(settings.getBucket())
                                                .key(key)
                                                .contentType(mime)
                                                .build())
                                .build())
                .url()
                .toString();
    }

    public byte[] verifiedStaging(String key, long expectedSize, String mime) {
        validateKey(key);
        try {
            var head =
                    client().headObject(
                                    HeadObjectRequest.builder()
                                            .bucket(settings.getBucket())
                                            .key(key)
                                            .build());
            if (head.contentLength() == null
                    || head.contentLength() != expectedSize
                    || !mime.equals(head.contentType()))
                throw new BadRequestException("Kích thước hoặc MIME trên R2 không khớp");
            try (var stream =
                    client().getObject(
                                    GetObjectRequest.builder()
                                            .bucket(settings.getBucket())
                                            .key(key)
                                            .ifMatch(head.eTag())
                                            .build())) {
                byte[] bytes = stream.readNBytes(settings.getMaxFileBytes() + 1);
                if (bytes.length != expectedSize || bytes.length > settings.getMaxFileBytes())
                    throw new BadRequestException("Tệp trên R2 không hợp lệ");
                return bytes;
            }
        } catch (IOException | SdkException ex) {
            throw new IllegalStateException("Không xác minh được tệp R2; có thể thử hoàn tất lại");
        }
    }

    @Override
    public StoredFileInfo store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty() || file.getSize() > settings.getMaxFileBytes())
            throw new BadRequestException("Tệp rỗng hoặc vượt giới hạn lưu trữ");
        try (var input = file.getInputStream()) {
            return storeBytes(
                    input.readNBytes(settings.getMaxFileBytes() + 1),
                    safeName(file.getOriginalFilename()),
                    Objects.toString(file.getContentType(), "application/octet-stream"),
                    folder);
        } catch (IOException ex) {
            throw new IllegalStateException("Không đọc được tệp");
        }
    }

    public StoredFileInfo storeBytes(byte[] bytes, String name, String mime, String folder) {
        if (bytes.length == 0 || bytes.length > settings.getMaxFileBytes())
            throw new BadRequestException("Tệp rỗng hoặc vượt giới hạn lưu trữ");
        if (mime.contains("\r") || mime.contains("\n"))
            throw new BadRequestException("Loại tệp không hợp lệ");
        try {
            org.springframework.http.MediaType.parseMediaType(mime);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Loại tệp không hợp lệ");
        }
        String original = safeName(name), key = key(folder, original), checksum = checksum(bytes);
        try {
            client().putObject(
                            PutObjectRequest.builder()
                                    .bucket(settings.getBucket())
                                    .key(key)
                                    .contentType(mime)
                                    .metadata(Map.of("sha256", checksum))
                                    .build(),
                            RequestBody.fromBytes(bytes));
        } catch (SdkException ex) {
            throw new IllegalStateException("Không lưu được tệp vào R2");
        }
        return StoredFileInfo.builder()
                .provider(StorageProvider.R2)
                .providerFileId(key)
                .providerFolderId(settings.getBucket())
                .storagePathOrUrl("r2://" + settings.getBucket() + "/" + key)
                .originalName(original)
                .mimeType(mime)
                .sizeBytes((long) bytes.length)
                .checksum(checksum)
                .build();
    }

    public static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String safeFolder(String folder) {
        if (folder == null
                || folder.length() > 600
                || !folder.matches("[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*"))
            throw new BadRequestException("Thư mục lưu trữ không hợp lệ");
        return folder;
    }

    public static String safeName(String name) {
        String result = Objects.toString(name, "document").replace('\\', '/');
        result =
                result.substring(result.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\p{Cntrl}]", "_");
        if (result.isBlank()) result = "document";
        return result.length() > 180 ? result.substring(result.length() - 180) : result;
    }

    @Override
    public Resource loadAsResource(String key) {
        validateKey(key);
        try (var stream =
                client().getObject(
                                GetObjectRequest.builder()
                                        .bucket(settings.getBucket())
                                        .key(key)
                                        .build())) {
            if (stream.response().contentLength() != null
                    && stream.response().contentLength() > settings.getMaxFileBytes())
                throw new BadRequestException("Tệp vượt giới hạn tải xuống");
            byte[] bytes = stream.readNBytes(settings.getMaxFileBytes() + 1);
            if (bytes.length > settings.getMaxFileBytes())
                throw new BadRequestException("Tệp vượt giới hạn tải xuống");
            return new ByteArrayResource(bytes);
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException("Không tìm thấy tệp trên R2");
        } catch (IOException | SdkException ex) {
            throw new IllegalStateException("Không tải được tệp từ R2");
        }
    }

    /** Tải đúng một đoạn byte để trình xem PDF có thể dùng HTTP Range thay vì chờ toàn bộ tệp. */
    public Resource loadRangeAsResource(String key, long start, long end) {
        validateKey(key);
        if (start < 0 || end < start || end - start + 1 > settings.getMaxFileBytes())
            throw new BadRequestException("Khoảng byte tải xuống không hợp lệ");
        try (var stream =
                client().getObject(
                        GetObjectRequest.builder()
                                .bucket(settings.getBucket())
                                .key(key)
                                .range("bytes=" + start + "-" + end)
                                .build())) {
            int expected = Math.toIntExact(end - start + 1);
            byte[] bytes = stream.readNBytes(expected + 1);
            if (bytes.length != expected)
                throw new IllegalStateException("R2 trả về đoạn tệp không đầy đủ");
            return new ByteArrayResource(bytes);
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException("Không tìm thấy tệp trên R2");
        } catch (IOException | SdkException ex) {
            throw new IllegalStateException("Không tải được tệp từ R2");
        }
    }

    @Override
    public void delete(String key) {
        validateKey(key);
        try {
            client().deleteObject(
                            DeleteObjectRequest.builder()
                                    .bucket(settings.getBucket())
                                    .key(key)
                                    .build());
        } catch (SdkException ex) {
            throw new IllegalStateException("Không xóa được tệp khỏi R2");
        }
    }

    public record ObjectInfo(String key, long size, Instant modified) {}

    public record ObjectPage(List<ObjectInfo> items, String nextToken) {}

    public ObjectPage list(String token) {
        var page =
                client().listObjectsV2(
                                ListObjectsV2Request.builder()
                                        .bucket(settings.getBucket())
                                        .prefix(prefix() + "/")
                                        .maxKeys(200)
                                        .continuationToken(token)
                                        .build());
        return new ObjectPage(
                page.contents().stream()
                        .map(o -> new ObjectInfo(o.key(), o.size(), o.lastModified()))
                        .toList(),
                page.nextContinuationToken());
    }

    public Instant modified(String key) {
        validateKey(key);
        return client().headObject(
                        HeadObjectRequest.builder().bucket(settings.getBucket()).key(key).build())
                .lastModified();
    }

    private void validateKey(String key) {
        if (key == null
                || key.isBlank()
                || key.startsWith("/")
                || key.contains("..")
                || key.contains("\\")
                || key.contains("://")
                || (!prefix().isEmpty() && !key.startsWith(prefix() + "/")))
            throw new BadRequestException("Mã tệp R2 không hợp lệ");
    }

    @Override
    public StorageProvider getProviderType() {
        return StorageProvider.R2;
    }
}
