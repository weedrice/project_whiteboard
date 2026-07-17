package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FileVariantRepository extends JpaRepository<FileVariant, Long> {

    Optional<FileVariant> findByFileFileIdAndVariantType(Long fileId, FileVariantType variantType);

    Optional<FileVariant> findByFileFileIdAndVariantTypeAndStorageStatus(
            Long fileId,
            FileVariantType variantType,
            com.weedrice.whiteboard.domain.file.entity.FileStorageStatus storageStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM FileVariant v WHERE v.fileVariantId = :fileVariantId")
    Optional<FileVariant> findByIdForUpdate(@Param("fileVariantId") Long fileVariantId);

    @Query("""
            SELECT v.fileVariantId
            FROM FileVariant v
            WHERE ((v.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_UPLOAD
                   AND v.createdAt < :cutoff)
               OR v.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE)
              AND (v.cleanupNextAttemptAt IS NULL OR v.cleanupNextAttemptAt <= :now)
              AND COALESCE(v.cleanupRetryCount, 0) < :maxRetryCount
            ORDER BY COALESCE(v.cleanupNextAttemptAt, v.createdAt) ASC, v.fileVariantId ASC
            """)
    List<Long> findCleanupCandidateIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now,
            @Param("maxRetryCount") int maxRetryCount,
            Pageable pageable);

    @Query("""
            SELECT COUNT(v) FROM FileVariant v
            WHERE v.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE
              AND COALESCE(v.cleanupRetryCount, 0) >= :maxRetryCount
            """)
    long countCleanupDeadLetters(@Param("maxRetryCount") int maxRetryCount);

    @Query("""
            SELECT MIN(COALESCE(v.cleanupNextAttemptAt, v.createdAt)) FROM FileVariant v
            WHERE ((v.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_UPLOAD
                    AND v.createdAt < :cutoff)
                OR v.storageStatus = com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_DELETE)
              AND COALESCE(v.cleanupRetryCount, 0) < :maxRetryCount
            """)
    Optional<LocalDateTime> findOldestCleanupDueAt(
            @Param("cutoff") LocalDateTime cutoff, @Param("maxRetryCount") int maxRetryCount);

    List<FileVariant> findByFileFileId(Long fileId);

    void deleteByFileFileId(Long fileId);
}
