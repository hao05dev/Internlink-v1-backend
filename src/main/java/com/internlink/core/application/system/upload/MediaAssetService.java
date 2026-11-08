package com.internlink.core.application.system.upload;

import com.internlink.core.infrastructure.integration.storage.*;
import com.internlink.core.infrastructure.persistence.jdbc.MediaAssetRepository;
import com.internlink.core.shared.exception.BadRequestException;
import com.internlink.core.shared.exception.ResourceNotFoundException;
import com.internlink.core.shared.security.ResourceAuthorization;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaAssetService {
    private final MediaAssetRepository assets;
    private final MediaAccessPolicy access;
    private final MediaContentValidator validator;
    private final ClamAvScanner scanner;
    private final R2StorageService r2;

    public record Uploaded(UUID id, String url, String fileName, String mimeType, long sizeBytes) {}

    public record Download(Resource resource, String mime, String name) {}

    @Transactional
    public Uploaded upload(MultipartFile file, MediaPurpose purpose, UUID context, String scope)
            throws IOException {
        var owner = access.actor();
        context = access.requireUpload(owner, purpose, context, scope);
        var content = validator.validate(file, purpose);
        scanner.scan(content.bytes());
        var stored =
                r2.storeBytes(
                        content.bytes(),
                        content.name(),
                        content.mime(),
                        StorageFolders.media(purpose, context, owner.getId(), scope));
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK)
                            try {
                                r2.delete(stored.getProviderFileId());
                            } catch (RuntimeException ignored) {
                            }
                    }
                });
        var asset =
                new MediaAssetRepository.Asset(
                        UUID.randomUUID(),
                        owner.getId(),
                        purpose,
                        context,
                        scope,
                        stored.getProviderFileId(),
                        content.name(),
                        content.mime(),
                        stored.getSizeBytes(),
                        stored.getChecksum());
        assets.insert(asset);
        return new Uploaded(asset.id(), asset.url(), asset.name(), asset.mime(), asset.size());
    }

    /** Xoá ảnh/tệp vừa tải lên nhưng chưa được bài viết/tin nào dùng (chỉ chủ sở hữu hoặc admin). */
    @Transactional
    public void delete(UUID id) {
        var actor = access.actor();
        var asset = assets.get(id);
        if (!actor.getId().equals(asset.owner()) && !ResourceAuthorization.isAdmin(actor))
            throw new ResourceNotFoundException("MediaAsset", "id", id);
        if (assets.referenced(asset))
            throw new BadRequestException("Ảnh/tệp đang được sử dụng, hãy gỡ khỏi bài viết rồi lưu trước");
        assets.delete(id);
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            r2.delete(asset.key());
                        } catch (RuntimeException ignored) {
                            // R2MaintenanceService sẽ dọn object mồ côi sau.
                        }
                    }
                });
    }

    @Transactional(readOnly = true)
    public Download download(UUID id, boolean publicEndpoint) {
        var asset = assets.get(id);
        if ((publicEndpoint && asset.purpose().privateImage())
                || !access.canRead(access.optionalActor(), asset))
            throw new ResourceNotFoundException("MediaAsset", "id", id);
        return new Download(r2.loadAsResource(asset.key()), asset.mime(), asset.name());
    }
}
