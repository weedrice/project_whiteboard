package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPostFile;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostFileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class FileTemporaryCleanupTtlContractTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private ScheduledPostFileRepository scheduledPostFileRepository;

    private User uploader;

    @BeforeEach
    void setUp() {
        uploader = User.builder()
                .loginId("ttl-uploader")
                .email("ttl-uploader@example.com")
                .password("password")
                .displayName("TTL Uploader")
                .build();
        entityManager.persist(uploader);
    }

    @Test
    void cleanupCandidatesRespectStrictCutoffAndProtectedAssociations() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 6, 0, 0);
        File expiredTemporary = persistFile("expired.jpg", null, null, FileStorageStatus.ACTIVE);
        File boundaryTemporary = persistFile("boundary.jpg", null, null, FileStorageStatus.ACTIVE);
        File freshTemporary = persistFile("fresh.jpg", null, null, FileStorageStatus.ACTIVE);
        File draftFile = persistFile("draft.jpg", 91L, "DRAFT_POST", FileStorageStatus.ACTIVE);
        File postFile = persistFile("post.jpg", 55L, "POST_CONTENT", FileStorageStatus.ACTIVE);
        File scheduledFile = persistFile("scheduled.jpg", null, null, FileStorageStatus.ACTIVE);
        File alreadyPending = persistFile("pending.jpg", null, null, FileStorageStatus.PENDING_DELETE);
        entityManager.flush();
        scheduledPostFileRepository.saveAndFlush(
                new ScheduledPostFile(77L, scheduledFile.getFileId(), 1));

        setCreatedAt(expiredTemporary, cutoff.minusSeconds(1));
        setCreatedAt(boundaryTemporary, cutoff);
        setCreatedAt(freshTemporary, cutoff.plusSeconds(1));
        setCreatedAt(draftFile, cutoff.minusDays(2));
        setCreatedAt(postFile, cutoff.minusDays(2));
        setCreatedAt(scheduledFile, cutoff.minusDays(2));
        setCreatedAt(alreadyPending, cutoff.minusDays(2));
        entityManager.flush();
        entityManager.clear();

        List<FileRepository.FileCleanupCandidateProjection> candidates =
                fileRepository.findTemporaryFileCleanupCandidates(cutoff, PageRequest.of(0, 50));

        assertThat(candidates)
                .extracting(FileRepository.FileCleanupCandidateProjection::getFileId)
                .containsExactly(expiredTemporary.getFileId())
                .doesNotContain(
                        boundaryTemporary.getFileId(),
                        freshTemporary.getFileId(),
                        draftFile.getFileId(),
                        postFile.getFileId(),
                        scheduledFile.getFileId(),
                        alreadyPending.getFileId());
    }

    @Test
    void stalePendingUploadCleanupRespectsStrictCutoff() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 6, 0, 0);
        LocalDateTime requestedAt = cutoff.plusDays(1);
        File expired = persistFile(
                "expired-pending.jpg", null, null, FileStorageStatus.PENDING_UPLOAD);
        File boundary = persistFile(
                "boundary-pending.jpg", null, null, FileStorageStatus.PENDING_UPLOAD);
        File associated = persistFile(
                "associated-pending.jpg", 91L, "DRAFT_POST", FileStorageStatus.PENDING_UPLOAD);
        entityManager.flush();
        setCreatedAt(expired, cutoff.minusSeconds(1));
        setCreatedAt(boundary, cutoff);
        setCreatedAt(associated, cutoff.minusDays(1));
        entityManager.flush();
        entityManager.clear();

        int updated = fileRepository.requestDeletionForStalePendingUploads(cutoff, requestedAt);

        assertThat(updated).isEqualTo(1);
        assertThat(entityManager.find(File.class, expired.getFileId()).getStorageStatus())
                .isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(entityManager.find(File.class, boundary.getFileId()).getStorageStatus())
                .isEqualTo(FileStorageStatus.PENDING_UPLOAD);
        assertThat(entityManager.find(File.class, associated.getFileId()).getStorageStatus())
                .isEqualTo(FileStorageStatus.PENDING_UPLOAD);
    }

    @Test
    void deletionRequestIsIdempotent() {
        File temporary = persistFile("idempotent.jpg", null, null, FileStorageStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();
        LocalDateTime firstRequestedAt = LocalDateTime.of(2026, 8, 6, 1, 0);
        LocalDateTime secondRequestedAt = firstRequestedAt.plusMinutes(5);

        int firstUpdated = fileRepository.requestDeletionForTemporaryFiles(
                List.of(temporary.getFileId()), firstRequestedAt);
        int secondUpdated = fileRepository.requestDeletionForTemporaryFiles(
                List.of(temporary.getFileId()), secondRequestedAt);

        File persisted = entityManager.find(File.class, temporary.getFileId());
        assertThat(firstUpdated).isEqualTo(1);
        assertThat(secondUpdated).isZero();
        assertThat(persisted.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(persisted.getDeleteRequestedAt()).isEqualTo(firstRequestedAt);
    }

    private File persistFile(
            String originalName,
            Long relatedId,
            String relatedType,
            FileStorageStatus storageStatus) {
        File file = File.builder()
                .originalName(originalName)
                .filePath("path/to/" + originalName)
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .storageStatus(storageStatus)
                .build();
        entityManager.persist(file);
        return file;
    }

    private void setCreatedAt(File target, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery(
                        "UPDATE files SET created_at = :createdAt, modified_at = :createdAt "
                                + "WHERE file_id = :fileId")
                .setParameter("createdAt", createdAt)
                .setParameter("fileId", target.getFileId())
                .executeUpdate();
    }
}
