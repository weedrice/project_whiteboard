package com.weedrice.whiteboard.domain.file.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "files", indexes = {
        @Index(name = "idx_files_uploader", columnList = "uploader_id"),
        @Index(name = "idx_files_related", columnList = "related_type, related_id")
})
public class File extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "file_path", length = 255, nullable = false)
    private String filePath;

    @Column(name = "original_name", length = 255, nullable = false)
    private String originalName;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "related_type", length = 50)
    private String relatedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_status", length = 30, nullable = false)
    private FileStorageStatus storageStatus;

    @Column(name = "delete_requested_at")
    private LocalDateTime deleteRequestedAt;

    @Column(name = "delete_retry_count", nullable = false)
    private Integer deleteRetryCount;

    @Column(name = "delete_last_error", length = 1000)
    private String deleteLastError;

    @Builder
    public File(String filePath, String originalName, Long fileSize, String mimeType, User uploader, Long relatedId,
            String relatedType, FileStorageStatus storageStatus, LocalDateTime deleteRequestedAt, Integer deleteRetryCount,
            String deleteLastError) {
        this.filePath = filePath;
        this.originalName = originalName;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.uploader = uploader;
        this.relatedId = relatedId;
        this.relatedType = relatedType;
        this.storageStatus = storageStatus != null ? storageStatus : FileStorageStatus.ACTIVE;
        this.deleteRequestedAt = deleteRequestedAt;
        this.deleteRetryCount = deleteRetryCount != null ? deleteRetryCount : 0;
        this.deleteLastError = deleteLastError;
    }

    public void updateRelatedInfo(Long relatedId, String relatedType) {
        this.relatedId = relatedId;
        this.relatedType = relatedType;
    }

    public boolean isUnassociated() {
        return this.relatedId == null && this.relatedType == null;
    }

    public boolean isAssociatedWith(Long relatedId, String relatedType) {
        return this.relatedId != null
                && this.relatedId.equals(relatedId)
                && this.relatedType != null
                && this.relatedType.equals(relatedType);
    }

    public boolean isDeletionRequested() {
        return this.storageStatus == FileStorageStatus.PENDING_DELETE
                || this.storageStatus == FileStorageStatus.DELETE_FAILED;
    }

    public void markDeletionPending() {
        this.storageStatus = FileStorageStatus.PENDING_DELETE;
        this.deleteRequestedAt = LocalDateTime.now();
        this.deleteLastError = null;
    }

    public void markDeletionFailed(String deleteLastError) {
        this.storageStatus = FileStorageStatus.DELETE_FAILED;
        this.deleteRequestedAt = LocalDateTime.now();
        this.deleteRetryCount = this.deleteRetryCount == null ? 1 : this.deleteRetryCount + 1;
        this.deleteLastError = truncate(deleteLastError);
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
