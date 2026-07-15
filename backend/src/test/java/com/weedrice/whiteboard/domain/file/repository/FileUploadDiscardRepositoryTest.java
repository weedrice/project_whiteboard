package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class FileUploadDiscardRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FileRepository fileRepository;

    private User uploader;
    private User otherUploader;

    @BeforeEach
    void setUp() {
        uploader = persistUser("discard-owner", "discard-owner@test.com");
        otherUploader = persistUser("discard-other", "discard-other@test.com");
    }

    @Test
    @DisplayName("현재 사용자의 활성 비연결 파일만 폐기하고 반복 요청은 멱등적이다")
    void requestDeletionForOwnedTemporaryFiles_updatesOnlyOwnedActiveTemporaryFiles() {
        File ownedTemporary = persistFile(uploader, null, null, FileStorageStatus.ACTIVE, "owned-temp.jpg");
        File ownedAssociated = persistFile(uploader, 10L, "EMOTICON_IMAGE", FileStorageStatus.ACTIVE,
                "owned-associated.jpg");
        File otherTemporary = persistFile(otherUploader, null, null, FileStorageStatus.ACTIVE, "other-temp.jpg");
        File alreadyPendingDelete = persistFile(uploader, null, null, FileStorageStatus.PENDING_DELETE,
                "already-pending.jpg");
        File pendingUpload = persistFile(uploader, null, null, FileStorageStatus.PENDING_UPLOAD,
                "pending-upload.jpg");
        entityManager.flush();

        List<Long> requestedIds = List.of(
                ownedTemporary.getFileId(),
                ownedAssociated.getFileId(),
                otherTemporary.getFileId(),
                alreadyPendingDelete.getFileId(),
                pendingUpload.getFileId(),
                999_999L);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 7, 14, 10, 0);

        int firstUpdated = fileRepository.requestDeletionForOwnedTemporaryFiles(
                requestedIds,
                uploader.getUserId(),
                deleteRequestedAt);
        int secondUpdated = fileRepository.requestDeletionForOwnedTemporaryFiles(
                requestedIds,
                uploader.getUserId(),
                deleteRequestedAt.plusSeconds(1));

        assertThat(firstUpdated).isEqualTo(1);
        assertThat(secondUpdated).isZero();
        assertThat(find(ownedTemporary).getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(find(ownedTemporary).getDeleteRequestedAt()).isEqualTo(deleteRequestedAt);
        assertThat(find(ownedAssociated).getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(find(otherTemporary).getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThat(find(alreadyPendingDelete).getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(find(pendingUpload).getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_UPLOAD);
    }

    @Test
    @DisplayName("현재 사용자의 레거시 상태값 없는 비연결 파일도 폐기한다")
    void requestDeletionForOwnedTemporaryFiles_updatesOwnedLegacyNullStatusFile() {
        File legacyTemporary = persistFile(uploader, null, null, FileStorageStatus.ACTIVE, "legacy-temp.jpg");
        entityManager.flush();
        entityManager.getEntityManager()
                .createNativeQuery("ALTER TABLE files ALTER COLUMN storage_status DROP NOT NULL")
                .executeUpdate();
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE files SET storage_status = NULL WHERE file_id = :fileId")
                .setParameter("fileId", legacyTemporary.getFileId())
                .executeUpdate();
        entityManager.clear();

        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 7, 14, 11, 0);

        int updated = fileRepository.requestDeletionForOwnedTemporaryFiles(
                List.of(legacyTemporary.getFileId()),
                uploader.getUserId(),
                deleteRequestedAt);

        File discardedFile = entityManager.find(File.class, legacyTemporary.getFileId());
        assertThat(updated).isEqualTo(1);
        assertThat(discardedFile.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(discardedFile.getDeleteRequestedAt()).isEqualTo(deleteRequestedAt);
    }

    private User persistUser(String loginId, String email) {
        User user = User.builder()
                .loginId(loginId)
                .email(email)
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        return user;
    }

    private File persistFile(User owner, Long relatedId, String relatedType, FileStorageStatus status,
            String originalName) {
        File file = File.builder()
                .originalName(originalName)
                .filePath("path/to/" + originalName)
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(owner)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .storageStatus(status)
                .build();
        entityManager.persist(file);
        return file;
    }

    private File find(File file) {
        return entityManager.find(File.class, file.getFileId());
    }
}
