package com.weedrice.whiteboard.domain.file.repository;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class FileRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FileRepository fileRepository;

    private User uploader;
    private File file;

    @BeforeEach
    void setUp() {
        uploader = User.builder()
                .loginId("uploader")
                .email("uploader@test.com")
                .password("password")
                .displayName("Uploader")
                .build();
        entityManager.persist(uploader);

        file = File.builder()
                .originalName("test.jpg")
                .filePath("path/to/test.jpg")
                .fileSize(1024L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(1L)
                .relatedType("POST_CONTENT")
                .build();
        entityManager.persist(file);
        entityManager.flush();
    }

    @Test
    @DisplayName("관련 ID와 타입으로 파일 조회")
    void findByRelatedIdAndRelatedType_success() {
        // when
        List<File> files = fileRepository.findByRelatedIdAndRelatedType(1L, "POST_CONTENT");

        // then
        assertThat(files).isNotEmpty();
        assertThat(files.get(0).getRelatedId()).isEqualTo(1L);
        assertThat(files.get(0).getRelatedType()).isEqualTo("POST_CONTENT");
    }

    @Test
    @DisplayName("관련 ID 목록과 타입으로 파일 조회")
    void findByRelatedIdInAndRelatedType_success() {
        // given
        List<Long> relatedIds = List.of(1L, 2L);

        // when
        List<File> files = fileRepository.findByRelatedIdInAndRelatedType(relatedIds, "POST_CONTENT");

        // then
        assertThat(files).isNotEmpty();
    }

    @Test
    @DisplayName("파일 ID 목록으로 active 파일을 조회한다")
    void findByFileIdInAndStorageStatus_success() {
        File pendingFile = File.builder()
                .originalName("pending.jpg")
                .filePath("path/to/pending.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.PENDING_DELETE)
                .build();
        entityManager.persist(pendingFile);
        entityManager.flush();

        List<File> files = fileRepository.findByFileIdInAndStorageStatus(
                List.of(file.getFileId(), pendingFile.getFileId()),
                FileStorageStatus.ACTIVE);

        assertThat(files).extracting(File::getFileId).containsExactly(file.getFileId());
    }

    @Test
    @DisplayName("임시 파일이나 원본 초안 파일만 조건부로 연결한다")
    void associateIfUnassociatedOrSourceDraft_updatesOnlyAllowedFiles() {
        File temporaryFile = File.builder()
                .originalName("temporary.jpg")
                .filePath("path/to/temporary.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        entityManager.persist(temporaryFile);
        File draftFile = File.builder()
                .originalName("draft.jpg")
                .filePath("path/to/draft.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(99L)
                .relatedType("DRAFT_POST")
                .build();
        entityManager.persist(draftFile);
        File otherDraftFile = File.builder()
                .originalName("other-draft.jpg")
                .filePath("path/to/other-draft.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(98L)
                .relatedType("DRAFT_POST")
                .build();
        entityManager.persist(otherDraftFile);
        entityManager.flush();
        entityManager.clear();

        int temporaryUpdated = fileRepository.associateIfUnassociatedOrSourceDraft(
                temporaryFile.getFileId(), uploader.getUserId(), 100L, "POST_CONTENT", "DRAFT_POST",
                99L,
                LocalDateTime.now());
        int draftUpdated = fileRepository.associateIfUnassociatedOrSourceDraft(
                draftFile.getFileId(), uploader.getUserId(), 100L, "POST_CONTENT", "DRAFT_POST",
                99L,
                LocalDateTime.now());
        int otherDraftUpdated = fileRepository.associateIfUnassociatedOrSourceDraft(
                otherDraftFile.getFileId(), uploader.getUserId(), 100L, "POST_CONTENT", "DRAFT_POST",
                99L,
                LocalDateTime.now());
        int alreadyAssociatedUpdated = fileRepository.associateIfUnassociatedOrSourceDraft(
                file.getFileId(), uploader.getUserId(), 100L, "POST_CONTENT", "DRAFT_POST",
                99L,
                LocalDateTime.now());

        assertThat(temporaryUpdated).isEqualTo(1);
        assertThat(draftUpdated).isEqualTo(1);
        assertThat(otherDraftUpdated).isZero();
        assertThat(alreadyAssociatedUpdated).isZero();
        entityManager.clear();
        File updatedTemporaryFile = entityManager.find(File.class, temporaryFile.getFileId());
        File updatedDraftFile = entityManager.find(File.class, draftFile.getFileId());
        File unchangedOtherDraftFile = entityManager.find(File.class, otherDraftFile.getFileId());
        assertThat(updatedTemporaryFile.isAssociatedWith(100L, "POST_CONTENT")).isTrue();
        assertThat(updatedDraftFile.isAssociatedWith(100L, "POST_CONTENT")).isTrue();
        assertThat(unchangedOtherDraftFile.isAssociatedWith(98L, "DRAFT_POST")).isTrue();
    }

    @Test
    @DisplayName("이미지 파일이 있는 관련 ID 조회")
    void findRelatedIdsWithImages_success() {
        // given
        List<Long> relatedIds = List.of(1L);

        // when
        List<Long> idsWithImages = fileRepository.findRelatedIdsWithImages(relatedIds, "POST_CONTENT");

        // then
        assertThat(idsWithImages).isNotEmpty();
    }

    @Test
    @DisplayName("관련 ID가 null이고 생성일이 이전인 파일 조회")
    void findByRelatedIdIsNullAndCreatedAtBefore_success() {
        // given
        File orphanFile = File.builder()
                .originalName("orphan.jpg")
                .filePath("path/to/orphan.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .relatedId(null)
                .relatedType(null)
                .build();
        entityManager.persist(orphanFile);
        entityManager.flush();

        // when
        List<File> files = fileRepository.findByRelatedIdIsNullAndCreatedAtBefore(LocalDateTime.now().plusDays(1));

        // then
        assertThat(files).isNotEmpty();
    }

    @Test
    @DisplayName("임시 파일 정리 대상 ID를 페이지 단위로 조회한다")
    void findTemporaryFileIdsForCleanup_returnsPagedIds() {
        File orphanFile = File.builder()
                .originalName("cleanup.jpg")
                .filePath("path/to/cleanup.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        entityManager.persist(orphanFile);
        entityManager.flush();
        entityManager.clear();

        List<Long> fileIds = fileRepository.findTemporaryFileIdsForCleanup(
                LocalDateTime.now().plusDays(1),
                PageRequest.of(0, 10));

        assertThat(fileIds).contains(orphanFile.getFileId());
        assertThat(fileIds).doesNotContain(file.getFileId());
    }

    @Test
    @DisplayName("임시 파일 정리 후보는 createdAt/fileId 커서 이후 항목만 조회한다")
    void findTemporaryFileCleanupCandidatesAfter_usesCreatedAtAndFileIdCursor() {
        LocalDateTime firstCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 0);
        LocalDateTime nextCreatedAt = LocalDateTime.of(2026, 5, 6, 10, 1);
        File first = persistTemporaryFile("cursor-first.jpg");
        File sameCreatedAtNext = persistTemporaryFile("cursor-second.jpg");
        File later = persistTemporaryFile("cursor-later.jpg");
        File pendingDelete = persistTemporaryFile("cursor-pending.jpg");
        pendingDelete.markDeletionPending();
        setCreatedAt(first, firstCreatedAt);
        setCreatedAt(sameCreatedAtNext, firstCreatedAt);
        setCreatedAt(later, nextCreatedAt);
        setCreatedAt(pendingDelete, nextCreatedAt);
        entityManager.flush();
        entityManager.clear();

        List<FileRepository.FileCleanupCandidateProjection> candidates =
                fileRepository.findTemporaryFileCleanupCandidatesAfter(
                        LocalDateTime.of(2026, 5, 7, 10, 0),
                        firstCreatedAt,
                        first.getFileId(),
                        PageRequest.of(0, 10));

        assertThat(candidates).extracting(FileRepository.FileCleanupCandidateProjection::getFileId)
                .containsExactly(sameCreatedAtNext.getFileId(), later.getFileId())
                .doesNotContain(first.getFileId(), file.getFileId(), pendingDelete.getFileId());
    }

    @Test
    @DisplayName("특정 관련 ID, 타입, MIME 타입으로 첫 번째 파일 조회")
    void findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWith_success() {
        // when
        Optional<File> found = fileRepository.findFirstByRelatedIdAndRelatedTypeAndMimeTypeStartingWith(
                1L, "POST_CONTENT", "image/");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getMimeType()).startsWith("image/");
    }

    @Test
    @DisplayName("relatedId별 첫 활성 이미지 파일 ID를 최솟값으로 조회한다")
    void findFirstImageFileIdsByRelatedIds_returnsMinimumActiveImageFileIdPerRelatedId() {
        File laterImageForFirstPost = persistRelatedFile(
                "later-image.jpg",
                "image/png",
                1L,
                "POST_CONTENT",
                FileStorageStatus.ACTIVE);
        File pendingImageForSecondPost = persistRelatedFile(
                "pending-image.jpg",
                "image/jpeg",
                2L,
                "POST_CONTENT",
                FileStorageStatus.PENDING_DELETE);
        File activeImageForSecondPost = persistRelatedFile(
                "active-image.webp",
                "image/webp",
                2L,
                "POST_CONTENT",
                FileStorageStatus.ACTIVE);
        File nonImageFile = persistRelatedFile(
                "document.txt",
                "text/plain",
                3L,
                "POST_CONTENT",
                FileStorageStatus.ACTIVE);
        File otherTypeImage = persistRelatedFile(
                "profile.jpg",
                "image/jpeg",
                4L,
                "USER_PROFILE",
                FileStorageStatus.ACTIVE);
        entityManager.flush();
        entityManager.clear();

        List<FileRepository.FirstImageFileIdProjection> projections =
                fileRepository.findFirstImageFileIdsByRelatedIdsAndRelatedTypeAndMimeTypeStartingWithAndStorageStatus(
                        List.of(1L, 2L, 3L, 4L),
                        "POST_CONTENT",
                        "image/",
                        FileStorageStatus.ACTIVE);

        Map<Long, Long> fileIdsByRelatedId = new LinkedHashMap<>();
        projections.forEach(projection -> fileIdsByRelatedId.put(projection.getRelatedId(), projection.getFileId()));
        assertThat(fileIdsByRelatedId)
                .containsEntry(1L, file.getFileId())
                .containsEntry(2L, activeImageForSecondPost.getFileId())
                .doesNotContainEntry(1L, laterImageForFirstPost.getFileId())
                .doesNotContainEntry(2L, pendingImageForSecondPost.getFileId())
                .doesNotContainEntry(3L, nonImageFile.getFileId())
                .doesNotContainEntry(4L, otherTypeImage.getFileId());
    }

    @Test
    @DisplayName("삭제 후보 조회는 pending delete와 stale deleting만 반환한다")
    void findPendingDeletionCandidates_returnsPendingAndStaleDeleting() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(30);
        File failedFile = File.builder()
                .originalName("failed.jpg")
                .filePath("path/to/failed.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.DELETE_FAILED)
                .deleteRequestedAt(LocalDateTime.now().minusHours(2))
                .deleteRetryCount(1)
                .build();
        entityManager.persist(failedFile);

        File freshDeletingFile = File.builder()
                .originalName("fresh-deleting.jpg")
                .filePath("path/to/fresh-deleting.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.DELETING)
                .deleteRequestedAt(staleBefore.plusMinutes(1))
                .build();
        entityManager.persist(freshDeletingFile);

        File staleDeletingFile = File.builder()
                .originalName("stale-deleting.jpg")
                .filePath("path/to/stale-deleting.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.DELETING)
                .deleteRequestedAt(staleBefore.minusMinutes(1))
                .build();
        entityManager.persist(staleDeletingFile);

        File pendingFile = File.builder()
                .originalName("pending.jpg")
                .filePath("path/to/pending.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.PENDING_DELETE)
                .deleteRequestedAt(LocalDateTime.now().minusHours(1))
                .build();
        entityManager.persist(pendingFile);
        entityManager.flush();
        entityManager.clear();

        List<File> candidates = fileRepository.findPendingDeletionCandidates(staleBefore, PageRequest.of(0, 10));
        List<String> candidatePaths = candidates.stream().map(File::getFilePath).toList();

        assertThat(candidatePaths)
                .containsExactlyInAnyOrder("path/to/pending.jpg", "path/to/stale-deleting.jpg");
    }

    @Test
    @DisplayName("삭제 후보 조회는 재시도 한도를 넘긴 failed 파일을 제외한다")
    void findRetryableFailedDeletionCandidates_excludesFailedFilesOverRetryLimit() {
        File retryableFailedFile = File.builder()
                .originalName("retryable.jpg")
                .filePath("path/to/retryable.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.DELETE_FAILED)
                .deleteRequestedAt(LocalDateTime.now().minusHours(2))
                .deleteRetryCount(4)
                .build();
        entityManager.persist(retryableFailedFile);

        File exhaustedFailedFile = File.builder()
                .originalName("exhausted.jpg")
                .filePath("path/to/exhausted.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .storageStatus(FileStorageStatus.DELETE_FAILED)
                .deleteRequestedAt(LocalDateTime.now().minusHours(1))
                .deleteRetryCount(5)
                .build();
        entityManager.persist(exhaustedFailedFile);
        entityManager.flush();
        entityManager.clear();

        List<File> candidates = fileRepository.findRetryableFailedDeletionCandidates(5, PageRequest.of(0, 10));
        List<String> candidatePaths = candidates.stream().map(File::getFilePath).toList();

        assertThat(candidatePaths).contains("path/to/retryable.jpg");
        assertThat(candidatePaths).doesNotContain("path/to/exhausted.jpg");
    }

    @Test
    @DisplayName("임시 파일 정리 요청은 비연결 활성 파일만 일괄 갱신한다")
    void requestDeletionForTemporaryFiles_updatesOnlyCleanupTargets() {
        File cleanupTarget = File.builder()
                .originalName("cleanup-target.jpg")
                .filePath("path/to/cleanup-target.jpg")
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        entityManager.persist(cleanupTarget);
        entityManager.flush();

        LocalDateTime deleteRequestedAt = LocalDateTime.now();

        int updated = fileRepository.requestDeletionForTemporaryFiles(
                List.of(cleanupTarget.getFileId(), file.getFileId()),
                deleteRequestedAt);

        entityManager.clear();

        File updatedTarget = entityManager.find(File.class, cleanupTarget.getFileId());
        File untouchedAssociatedFile = entityManager.find(File.class, file.getFileId());

        assertThat(updated).isEqualTo(1);
        assertThat(updatedTarget.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(updatedTarget.getDeleteRequestedAt()).isNotNull();
        assertThat(untouchedAssociatedFile.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    @DisplayName("오래된 pending upload 임시 파일만 삭제 대기 상태로 갱신한다")
    void requestDeletionForStalePendingUploads_updatesOnlyStaleUnassociatedPendingUploads() {
        LocalDateTime oldCreatedAt = LocalDateTime.of(2026, 5, 1, 10, 0);
        LocalDateTime freshCreatedAt = LocalDateTime.of(2026, 5, 2, 10, 0);
        LocalDateTime cutoffCreatedAt = LocalDateTime.of(2026, 5, 2, 0, 0);
        LocalDateTime deleteRequestedAt = LocalDateTime.of(2026, 5, 3, 10, 0);
        File stalePendingUpload = persistRelatedFile(
                "stale-pending.jpg",
                "image/jpeg",
                null,
                null,
                FileStorageStatus.PENDING_UPLOAD);
        File freshPendingUpload = persistRelatedFile(
                "fresh-pending.jpg",
                "image/jpeg",
                null,
                null,
                FileStorageStatus.PENDING_UPLOAD);
        File associatedPendingUpload = persistRelatedFile(
                "associated-pending.jpg",
                "image/jpeg",
                1L,
                "POST_CONTENT",
                FileStorageStatus.PENDING_UPLOAD);
        File activeTemporaryFile = persistRelatedFile(
                "active-temporary.jpg",
                "image/jpeg",
                null,
                null,
                FileStorageStatus.ACTIVE);
        entityManager.flush();
        setCreatedAt(stalePendingUpload, oldCreatedAt);
        setCreatedAt(freshPendingUpload, freshCreatedAt);
        setCreatedAt(associatedPendingUpload, oldCreatedAt);
        setCreatedAt(activeTemporaryFile, oldCreatedAt);
        entityManager.flush();
        entityManager.clear();

        int updated = fileRepository.requestDeletionForStalePendingUploads(cutoffCreatedAt, deleteRequestedAt);

        entityManager.clear();
        File updatedStale = entityManager.find(File.class, stalePendingUpload.getFileId());
        File untouchedFresh = entityManager.find(File.class, freshPendingUpload.getFileId());
        File untouchedAssociated = entityManager.find(File.class, associatedPendingUpload.getFileId());
        File untouchedActive = entityManager.find(File.class, activeTemporaryFile.getFileId());

        assertThat(updated).isEqualTo(1);
        assertThat(updatedStale.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(updatedStale.getDeleteRequestedAt()).isEqualTo(deleteRequestedAt);
        assertThat(untouchedFresh.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_UPLOAD);
        assertThat(untouchedAssociated.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_UPLOAD);
        assertThat(untouchedActive.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
    }

    private File persistTemporaryFile(String originalName) {
        File temporaryFile = File.builder()
                .originalName(originalName)
                .filePath("path/to/" + originalName)
                .fileSize(512L)
                .mimeType("image/jpeg")
                .uploader(uploader)
                .build();
        entityManager.persist(temporaryFile);
        return temporaryFile;
    }

    private File persistRelatedFile(
            String originalName,
            String mimeType,
            Long relatedId,
            String relatedType,
            FileStorageStatus storageStatus) {
        File relatedFile = File.builder()
                .originalName(originalName)
                .filePath("path/to/" + originalName)
                .fileSize(512L)
                .mimeType(mimeType)
                .uploader(uploader)
                .relatedId(relatedId)
                .relatedType(relatedType)
                .storageStatus(storageStatus)
                .build();
        entityManager.persist(relatedFile);
        return relatedFile;
    }

    private void setCreatedAt(File target, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE files SET created_at = :createdAt, modified_at = :createdAt WHERE file_id = :fileId")
                .setParameter("createdAt", createdAt)
                .setParameter("fileId", target.getFileId())
                .executeUpdate();
    }
}
