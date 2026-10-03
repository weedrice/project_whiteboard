package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostFileRepository;
import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostFileService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class FileAssociationReservationTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private FileRepository fileRepository;
    @Autowired private ScheduledPostFileRepository reservationRepository;

    private FileAssociationService service;
    private ScheduledPostFileService reservations;
    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder().loginId("reservation-owner").email("reservation-owner@example.test")
                .password("test-only").displayName("Reservation owner").build();
        entityManager.persistAndFlush(owner);
        service = new FileAssociationService(fileRepository, null, null, Clock.systemUTC());
        ReflectionTestUtils.setField(service, "entityManager", entityManager.getEntityManager());
        reservations = new ScheduledPostFileService(fileRepository, reservationRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "draft", "batch", "USER_PROFILE", "BOARD_ICON", "EMOTICON_IMAGE"})
    void unrelatedTargetsCannotClaimReservedTemporaryFile(String target) {
        File file = file(null, null);
        reserve(file, null);

        assertThatThrownBy(() -> associate(target, List.of(file.getFileId())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);

        entityManager.flush();
        entityManager.clear();
        assertThat(fileRepository.findById(file.getFileId()).orElseThrow().isUnassociated()).isTrue();
        assertThat(reservationRepository.existsByIdFileIdAndIdScheduledPostIdNot(file.getFileId(), 0L)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyTheMatchingPublicationCanAttachAndRepeatTheAssociation(boolean fromDraft) {
        Long draftId = fromDraft ? 55L : null;
        File file = file(draftId, fromDraft ? FileRelatedType.DRAFT_POST : null);
        reserve(file, draftId);

        service.attachFilesToPost(List.of(file.getFileId()), owner.getUserId(), 100L, draftId, 77L);
        service.attachFilesToPost(List.of(file.getFileId()), owner.getUserId(), 100L, draftId, 77L);
        assertThat(file.isAssociatedWith(100L, FileRelatedType.POST_CONTENT)).isTrue();
        assertThat(file.getStorageStatus()).isEqualTo(FileStorageStatus.ACTIVE);
        assertThatThrownBy(() -> service.attachFilesToPost(List.of(file.getFileId()), owner.getUserId(), 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);

        reservations.removeReferences(77L);
        service.attachFilesToPost(List.of(file.getFileId()), owner.getUserId(), 100L);
        assertThat(file.isAssociatedWith(100L, FileRelatedType.POST_CONTENT)).isTrue();
    }

    @Test
    void anotherPublicationCannotClaimTheReservation() {
        File file = file(null, null);
        reserve(file, null);

        assertThatThrownBy(() -> service.attachFilesToPost(
                List.of(file.getFileId()), owner.getUserId(), 100L, null, 88L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);
        assertThat(file.isUnassociated()).isTrue();
    }

    @Test
    void cancellationReleasesTheFileForAnOrdinaryPost() {
        File file = file(null, null);
        reserve(file, null);
        reservations.removeReferences(77L);

        service.attachFilesToPost(List.of(file.getFileId()), owner.getUserId(), 100L);

        entityManager.flush();
        entityManager.clear();
        assertThat(fileRepository.findById(file.getFileId()).orElseThrow()
                .isAssociatedWith(100L, FileRelatedType.POST_CONTENT)).isTrue();
    }

    @Test
    void draftRetentionExcludesReservedFilesAndPreservesTheRemainingOrder() {
        File temporary = file(null, null);
        File reserved = file(null, null);
        File draft = file(55L, FileRelatedType.DRAFT_POST);
        reserve(reserved, null);

        assertThat(service.retainValidDraftFileIds(
                List.of(draft.getFileId(), reserved.getFileId(), temporary.getFileId()), owner.getUserId(), 55L))
                .containsExactly(draft.getFileId(), temporary.getFileId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"post", "draft", "batch"})
    void aReservedFileRejectsTheWholeBatchBeforeAnyOtherFileMoves(String target) {
        File temporary = file(null, null);
        File reserved = file(null, null);
        reserve(reserved, null);

        assertThatThrownBy(() -> associate(target, List.of(temporary.getFileId(), reserved.getFileId())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);
        entityManager.flush();
        entityManager.clear();
        assertThat(fileRepository.findById(temporary.getFileId()).orElseThrow().isUnassociated()).isTrue();
        assertThat(fileRepository.findById(reserved.getFileId()).orElseThrow().isUnassociated()).isTrue();
    }

    @Test
    void ownershipAndStorageChecksRemainAheadOfReservationErrors() {
        File reserved = file(null, null);
        reserve(reserved, null);
        assertThatThrownBy(() -> service.attachFilesToPost(List.of(reserved.getFileId()), 999L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        reserved.markDeletionPending(LocalDateTime.now());
        entityManager.flush();
        assertThatThrownBy(() -> service.attachFilesToPost(List.of(reserved.getFileId()), owner.getUserId(), 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    void syncQueryLocksRequestedAndExistingFilesTogetherInIdOrder() {
        File requestedFirst = file(null, null);
        File existing = file(100L, FileRelatedType.POST_CONTENT);
        File requestedLast = file(null, null);

        assertThat(fileRepository.findActiveForAssociationForUpdate(
                List.of(requestedLast.getFileId(), requestedFirst.getFileId()), 100L, FileRelatedType.POST_CONTENT))
                .extracting(File::getFileId)
                .containsExactly(requestedFirst.getFileId(), existing.getFileId(), requestedLast.getFileId());
    }

    @Test
    void lockingRefreshRejectsAFileWhoseEarlierManagedStateIsStale() {
        File file = file(null, null);
        fileRepository.associateIfUnassociated(file.getFileId(), owner.getUserId(), 200L, FileRelatedType.POST_CONTENT);
        assertThat(file.isUnassociated()).isTrue();

        assertThatThrownBy(() -> service.attachFilesToPost(List.of(file.getFileId()), owner.getUserId(), 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FILE_ALREADY_ASSOCIATED);
        assertThat(file.isAssociatedWith(200L, FileRelatedType.POST_CONTENT)).isTrue();
    }

    private File file(Long relatedId, String relatedType) {
        File file = File.builder().originalName("reserved.jpg").filePath("test/reserved.jpg")
                .fileSize(100L).mimeType("image/jpeg").uploader(owner)
                .relatedId(relatedId).relatedType(relatedType).build();
        return entityManager.persistAndFlush(file);
    }

    private void reserve(File file, Long draftId) {
        reservations.replaceReferences(77L, owner.getUserId(), draftId, List.of(file.getFileId()));
    }

    private void associate(String target, List<Long> fileIds) {
        switch (target) {
            case "post" -> service.attachFilesToPost(fileIds, owner.getUserId(), 100L);
            case "draft" -> service.syncDraftFiles(fileIds, owner.getUserId(), 55L);
            case "batch" -> service.associateFilesWithEntity(fileIds, owner.getUserId(), 100L, FileRelatedType.EMOTICON_IMAGE);
            default -> service.associateFileWithEntity(fileIds.getFirst(), owner.getUserId(), 100L, target);
        }
    }
}
