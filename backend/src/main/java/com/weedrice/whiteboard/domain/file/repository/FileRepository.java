package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<File, Long> {
    interface FileCleanupCandidateProjection {
        Long getFileId();

        LocalDateTime getCreatedAt();
    }

    @Query("""
            SELECT f
            FROM File f
            WHERE f.relatedId IS NULL
              AND f.createdAt < :dateTime
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            """)
    List<File> findByRelatedIdIsNullAndCreatedAtBeforeAndStorageStatus(@Param("dateTime") LocalDateTime dateTime,
            @Param("storageStatus") FileStorageStatus storageStatus);

    default List<File> findByRelatedIdIsNullAndCreatedAtBefore(LocalDateTime dateTime) {
        return findByRelatedIdIsNullAndCreatedAtBeforeAndStorageStatus(dateTime, FileStorageStatus.ACTIVE);
    }

    @Query("""
            SELECT f.fileId
            FROM File f
            WHERE f.relatedId IS NULL
              AND f.relatedType IS NULL
              AND f.createdAt < :dateTime
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            ORDER BY f.createdAt ASC, f.fileId ASC
            """)
    List<Long> findTemporaryFileIdsForCleanup(@Param("dateTime") LocalDateTime dateTime,
            @Param("storageStatus") FileStorageStatus storageStatus,
            Pageable pageable);

    default List<Long> findTemporaryFileIdsForCleanup(LocalDateTime dateTime, Pageable pageable) {
        return findTemporaryFileIdsForCleanup(dateTime, FileStorageStatus.ACTIVE, pageable);
    }

    @Query("""
            SELECT f.fileId AS fileId,
                   f.createdAt AS createdAt
            FROM File f
            WHERE f.relatedId IS NULL
              AND f.relatedType IS NULL
              AND f.createdAt < :dateTime
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
              AND (:lastCreatedAt IS NULL
                   OR f.createdAt > :lastCreatedAt
                   OR (f.createdAt = :lastCreatedAt AND f.fileId > :lastFileId))
            ORDER BY f.createdAt ASC, f.fileId ASC
            """)
    List<FileCleanupCandidateProjection> findTemporaryFileCleanupCandidatesAfter(
            @Param("dateTime") LocalDateTime dateTime,
            @Param("storageStatus") FileStorageStatus storageStatus,
            @Param("lastCreatedAt") LocalDateTime lastCreatedAt,
            @Param("lastFileId") Long lastFileId,
            Pageable pageable);

    default List<FileCleanupCandidateProjection> findTemporaryFileCleanupCandidatesAfter(
            LocalDateTime dateTime,
            LocalDateTime lastCreatedAt,
            Long lastFileId,
            Pageable pageable) {
        return findTemporaryFileCleanupCandidatesAfter(
                dateTime,
                FileStorageStatus.ACTIVE,
                lastCreatedAt,
                lastFileId,
                pageable);
    }

    @Query("""
            SELECT f
            FROM File f
            WHERE f.fileId = :fileId
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            """)
    Optional<File> findByFileIdAndStorageStatus(@Param("fileId") Long fileId,
            @Param("storageStatus") FileStorageStatus storageStatus);

    @Query("""
            SELECT f
            FROM File f
            WHERE f.fileId IN :fileIds
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            """)
    List<File> findByFileIdInAndStorageStatus(@Param("fileIds") List<Long> fileIds,
            @Param("storageStatus") FileStorageStatus storageStatus);

    @Query("""
            SELECT f
            FROM File f
            WHERE f.relatedId = :relatedId
              AND f.relatedType = :relatedType
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            """)
    List<File> findByRelatedIdAndRelatedTypeAndStorageStatus(@Param("relatedId") Long relatedId,
            @Param("relatedType") String relatedType,
            @Param("storageStatus") FileStorageStatus storageStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f
            FROM File f
            WHERE f.relatedId = :relatedId
              AND f.relatedType = :relatedType
              AND (f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                   OR f.storageStatus IS NULL)
            ORDER BY f.fileId ASC
            """)
    List<File> findActiveByRelatedIdAndRelatedTypeForUpdate(@Param("relatedId") Long relatedId,
            @Param("relatedType") String relatedType);

    default List<File> findByRelatedIdAndRelatedType(Long relatedId, String relatedType) {
        return findByRelatedIdAndRelatedTypeAndStorageStatus(relatedId, relatedType, FileStorageStatus.ACTIVE);
    }

    @Query("""
            SELECT f
            FROM File f
            WHERE f.relatedId IN :relatedIds
              AND f.relatedType = :relatedType
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            """)
    List<File> findByRelatedIdInAndRelatedTypeAndStorageStatus(@Param("relatedIds") List<Long> relatedIds,
            @Param("relatedType") String relatedType,
            @Param("storageStatus") FileStorageStatus storageStatus);

    default List<File> findByRelatedIdInAndRelatedType(List<Long> relatedIds, String relatedType) {
        return findByRelatedIdInAndRelatedTypeAndStorageStatus(relatedIds, relatedType, FileStorageStatus.ACTIVE);
    }

    @Query("""
            SELECT DISTINCT f.relatedId
            FROM File f
            WHERE f.relatedId IN :relatedIds
              AND f.relatedType = :relatedType
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
              AND f.mimeType LIKE 'image/%'
            """)
    List<Long> findRelatedIdsWithImages(
            @Param("relatedIds") List<Long> relatedIds,
            @Param("relatedType") String relatedType,
            @Param("storageStatus") FileStorageStatus storageStatus);

    default List<Long> findRelatedIdsWithImages(List<Long> relatedIds, String relatedType) {
        return findRelatedIdsWithImages(relatedIds, relatedType, FileStorageStatus.ACTIVE);
    }

    @Query("""
            SELECT f
            FROM File f
            WHERE f.relatedId IN :relatedIds
              AND f.relatedType = :relatedType
              AND f.mimeType LIKE CONCAT(:mimeTypePrefix, '%')
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            ORDER BY f.relatedId ASC, f.fileId ASC
            """)
    List<File> findByRelatedIdInAndRelatedTypeAndMimeTypeStartingWithAndStorageStatusOrderByRelatedIdAscFileIdAsc(
            @Param("relatedIds") List<Long> relatedIds,
            @Param("relatedType") String relatedType,
            @Param("mimeTypePrefix") String mimeTypePrefix,
            @Param("storageStatus") FileStorageStatus storageStatus);

    @Query("""
            SELECT f
            FROM File f
            WHERE f.relatedId = :relatedId
              AND f.relatedType = :relatedType
              AND f.mimeType LIKE CONCAT(:mimeTypePrefix, '%')
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            ORDER BY f.fileId ASC
            """)
    Optional<File> findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
            @Param("relatedId") Long relatedId,
            @Param("relatedType") String relatedType,
            @Param("mimeTypePrefix") String mimeTypePrefix,
            @Param("storageStatus") FileStorageStatus storageStatus);

    default Optional<File> findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWith(
            Long relatedId,
            String relatedType,
            String mimeTypePrefix) {
        return findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
                relatedId, relatedType, mimeTypePrefix, FileStorageStatus.ACTIVE);
    }

    @Query("""
            SELECT f
            FROM File f
            WHERE f.fileId = :fileId
              AND f.relatedId = :relatedId
              AND f.relatedType = :relatedType
              AND (f.storageStatus = :storageStatus
                   OR (:storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                       AND f.storageStatus IS NULL))
            """)
    Optional<File> findByFileIdAndRelatedIdAndRelatedTypeAndStorageStatus(
            @Param("fileId") Long fileId,
            @Param("relatedId") Long relatedId,
            @Param("relatedType") String relatedType,
            @Param("storageStatus") FileStorageStatus storageStatus);

    default Optional<File> findByFileIdAndRelatedIdAndRelatedType(Long fileId, Long relatedId, String relatedType) {
        return findByFileIdAndRelatedIdAndRelatedTypeAndStorageStatus(
                fileId, relatedId, relatedType, FileStorageStatus.ACTIVE);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f
            FROM File f
            WHERE f.fileId = :fileId
            """)
    Optional<File> findByIdForUpdate(@Param("fileId") Long fileId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT f
            FROM File f
            WHERE f.fileId = :fileId
              AND (
                    f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE
                    OR (
                        f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.DELETE_FAILED
                        AND COALESCE(f.deleteRetryCount, 0) < :maxRetryCount
                    )
                    OR (
                        f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.DELETING
                        AND (f.deleteRequestedAt IS NULL OR f.deleteRequestedAt < :staleBefore)
                    )
              )
            """)
    Optional<File> findDeletionClaimCandidateForUpdate(
            @Param("fileId") Long fileId,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE File f
            SET f.relatedId = :relatedId,
                f.relatedType = :relatedType
            WHERE f.fileId = :fileId
              AND f.uploader.userId = :ownerUserId
              AND f.relatedId IS NULL
              AND f.relatedType IS NULL
              AND (f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                   OR f.storageStatus IS NULL)
            """)
    int associateIfUnassociated(@Param("fileId") Long fileId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("relatedId") Long relatedId,
            @Param("relatedType") String relatedType);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE File f
            SET f.relatedId = :relatedId,
                f.relatedType = :relatedType,
                f.modifiedAt = :modifiedAt
            WHERE f.fileId = :fileId
              AND f.uploader.userId = :ownerUserId
              AND (f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                   OR f.storageStatus IS NULL)
              AND (
                    (f.relatedId IS NULL AND f.relatedType IS NULL)
                    OR f.relatedType = :draftRelatedType
              )
            """)
    int associateIfUnassociatedOrDraft(@Param("fileId") Long fileId,
            @Param("ownerUserId") Long ownerUserId,
            @Param("relatedId") Long relatedId,
            @Param("relatedType") String relatedType,
            @Param("draftRelatedType") String draftRelatedType,
            @Param("modifiedAt") LocalDateTime modifiedAt);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE File f
            SET f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE,
                f.deleteRequestedAt = :deleteRequestedAt,
                f.deleteLastError = NULL
            WHERE f.fileId = :fileId
              AND f.relatedId IS NULL
              AND f.relatedType IS NULL
              AND (f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                   OR f.storageStatus IS NULL)
            """)
    int requestDeletionIfTemporary(@Param("fileId") Long fileId,
            @Param("deleteRequestedAt") LocalDateTime deleteRequestedAt);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE File f
            SET f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE,
                f.deleteRequestedAt = :deleteRequestedAt,
                f.deleteLastError = NULL
            WHERE f.fileId IN :fileIds
              AND f.relatedId IS NULL
              AND f.relatedType IS NULL
              AND (f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.ACTIVE
                   OR f.storageStatus IS NULL)
            """)
    int requestDeletionForTemporaryFiles(@Param("fileIds") List<Long> fileIds,
            @Param("deleteRequestedAt") LocalDateTime deleteRequestedAt);

    @Query("""
            SELECT f
            FROM File f
            WHERE f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE
               OR (
                    f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.DELETING
                    AND (f.deleteRequestedAt IS NULL OR f.deleteRequestedAt < :staleBefore)
               )
            ORDER BY COALESCE(f.deleteRequestedAt, f.createdAt) ASC, f.fileId ASC
            """)
    List<File> findPendingDeletionCandidates(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);

    @Query("""
            SELECT f
            FROM File f
            WHERE f.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.DELETE_FAILED
              AND COALESCE(f.deleteRetryCount, 0) < :maxRetryCount
            ORDER BY COALESCE(f.deleteRequestedAt, f.createdAt) ASC, f.fileId ASC
            """)
    List<File> findRetryableFailedDeletionCandidates(@Param("maxRetryCount") int maxRetryCount, Pageable pageable);
}
