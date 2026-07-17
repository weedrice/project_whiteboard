package com.weedrice.whiteboard.domain.file.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "file_variants",
        indexes = {
                @Index(name = "idx_file_variants_file", columnList = "file_id"),
                @Index(name = "idx_file_variants_storage_status_created",
                        columnList = "storage_status, created_at, file_variant_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_file_variants_file_type", columnNames = {"file_id", "variant_type"})
        })
public class FileVariant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_variant_id")
    private Long fileVariantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private File file;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant_type", length = 30, nullable = false)
    private FileVariantType variantType;

    @Column(name = "file_path", length = 255, nullable = false)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @Column(name = "width", nullable = false)
    private Integer width;

    @Column(name = "height", nullable = false)
    private Integer height;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_status", length = 30, nullable = false)
    private FileStorageStatus storageStatus;

    @Column(name = "cleanup_retry_count", nullable = false)
    private Integer cleanupRetryCount;

    @Column(name = "cleanup_next_attempt_at")
    private LocalDateTime cleanupNextAttemptAt;

    @Column(name = "cleanup_last_error", length = 100)
    private String cleanupLastError;

    @Builder
    public FileVariant(
            File file,
            FileVariantType variantType,
            String filePath,
            Long fileSize,
            String mimeType,
            Integer width,
            Integer height,
            FileStorageStatus storageStatus) {
        this.file = file;
        this.variantType = variantType;
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.storageStatus = storageStatus == null ? FileStorageStatus.ACTIVE : storageStatus;
        this.cleanupRetryCount = 0;
    }

    public void activate() {
        this.storageStatus = FileStorageStatus.ACTIVE;
    }

    public void requestDeletion() {
        this.storageStatus = FileStorageStatus.PENDING_DELETE;
    }

    public void recordCleanupFailure(String error, LocalDateTime nextAttemptAt) {
        cleanupRetryCount = cleanupRetryCount == null ? 1 : cleanupRetryCount + 1;
        cleanupNextAttemptAt = nextAttemptAt;
        cleanupLastError = error == null ? null : error.substring(0, Math.min(100, error.length()));
    }
}
