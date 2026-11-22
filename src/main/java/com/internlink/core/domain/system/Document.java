package com.internlink.core.domain.system;

import com.internlink.core.shared.domain.BaseEntity;
import com.internlink.core.shared.enums.ContextType;
import com.internlink.core.shared.enums.DocumentType;
import com.internlink.core.shared.enums.StorageProvider;
import com.internlink.core.domain.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;



@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 40)
    private ContextType contextType;

    @Column(name = "context_id", nullable = false)
    private UUID contextId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "storage_provider", nullable = false, length = 30)
    private StorageProvider storageProvider = StorageProvider.LOCAL;

    @Column(name = "provider_file_id", unique = true)
    private String providerFileId;

    @Column(name = "provider_folder_id")
    private String providerFolderId;

    @Column(name = "external_url", columnDefinition = "text")
    private String externalUrl;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "checksum", length = 128)
    private String checksum;

    @Builder.Default
    @Column(name = "visibility", nullable = false, length = 30)
    private String visibility = "PRIVATE";

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    /** Kết quả GVHD duyệt file hồ sơ: PENDING, APPROVED, REVISION_REQUIRED. */
    @Builder.Default
    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "PENDING";

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;
}
