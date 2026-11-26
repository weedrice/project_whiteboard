package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.service.ScheduledPostFileService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.config.TimeConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, TimeConfig.class, ScheduledPostFileService.class, FileUploadDiscardService.class,
        FileTemporaryCleanupWorker.class, EmoticonFileReferenceService.class})
class FileScheduledReservationCleanupPostgresTest {

    private static final LocalDateTime UPLOADED_AT = LocalDateTime.of(2001, 1, 1, 0, 0);

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private ScheduledPostFileService reservations;
    @Autowired private FileUploadDiscardService discard;
    @Autowired private FileTemporaryCleanupWorker cleanup;
    @Autowired private FileRepository files;
    @Autowired private FileVariantRepository variants;
    @Autowired private EmoticonFileReferenceService emoticonReferences;
    @Autowired private Clock clock;

    private record Fixture(long userId, long boardId, long scheduleId, long fileId) {}
    private enum CleanupRequest { UPLOADER_DISCARD, AGE_LIMIT }
    private Fixture fixture;

    @BeforeEach
    void createFixture() {
        String name = "res-" + UUID.randomUUID().toString().substring(0, 12);
        fixture = transactions.execute(status -> {
            User user = User.builder().loginId(name).displayName(name).email(name + "@example.org")
                    .password("test-password").build();
            entityManager.persist(user);
            Board board = Board.builder().boardName(name).boardUrl(name).creator(user).build();
            entityManager.persist(board);
            ScheduledPost scheduled = ScheduledPost.builder().user(user).board(board).title(name)
                    .contents("content").scheduledAt(LocalDateTime.now().plusDays(1)).build();
            entityManager.persist(scheduled);
            File file = File.builder().uploader(user).filePath(name).originalName("attachment.png")
                    .mimeType("image/png").fileSize(20L).storageStatus(FileStorageStatus.ACTIVE).build();
            entityManager.persist(file);
            entityManager.flush();
            jdbc.update("UPDATE files SET created_at = ? WHERE file_id = ?", UPLOADED_AT, file.getFileId());
            return new Fixture(user.getUserId(), board.getBoardId(), scheduled.getScheduledPostId(), file.getFileId());
        });
    }

    @AfterEach
    void removeFixture() {
        if (fixture != null) {
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM scheduled_posts WHERE scheduled_post_id = ?", fixture.scheduleId());
                jdbc.update("DELETE FROM files WHERE file_id = ?", fixture.fileId());
                jdbc.update("DELETE FROM boards WHERE board_id = ?", fixture.boardId());
                jdbc.update("DELETE FROM users WHERE user_id = ?", fixture.userId());
            });
        }
    }

    @ParameterizedTest
    @EnumSource(CleanupRequest.class)
    void cleanupWaitingForReservationKeepsAttachment(CleanupRequest request) throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = transactions.execute(status -> {
                reserve();
                var waiting = executor.submit(() -> requestDeletion(request));
                awaitBlockedOnCurrentTransaction();
                return waiting;
            });
            assertThat(result.get(10, TimeUnit.SECONDS)).isZero();
        }
        assertState("ACTIVE", 1);
    }

    @Test
    void reservationWaitingForDiscardCannotReserveTheFile() {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = transactions.execute(status -> {
                assertThat(requestDeletion(CleanupRequest.UPLOADER_DISCARD)).isOne();
                var waiting = executor.submit(() -> transactions.executeWithoutResult(ignored -> reserve()));
                awaitBlockedOnCurrentTransaction();
                return waiting;
            });
            assertThatThrownBy(() -> result.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }
        assertState("PENDING_DELETE", 0);
    }

    @ParameterizedTest
    @EnumSource(value = FileStorageStatus.class, names = {"PENDING_DELETE", "DELETING", "DELETE_FAILED"})
    void reservedAttachmentFromPreviousCleanupIsNeverSentToStorage(FileStorageStatus previousStatus) {
        transactions.executeWithoutResult(status -> {
            reserve();
            jdbc.update("UPDATE files SET storage_status = ?, delete_requested_at = ? WHERE file_id = ?",
                    previousStatus.name(), UPLOADED_AT, fixture.fileId());
        });

        var page = PageRequest.of(0, 100);
        if (previousStatus == FileStorageStatus.DELETING) {
            assertThat(files.findPendingDeletionFileIds(LocalDateTime.now(), page)).doesNotContain(fixture.fileId());
        } else if (previousStatus == FileStorageStatus.DELETE_FAILED) {
            assertThat(files.findRetryableFailedDeletionFileIds(5, page)).doesNotContain(fixture.fileId());
        }
        FileStorageService storage = mock(FileStorageService.class);
        new FileDeletionWorker(files, variants, storage, transactions, emoticonReferences, clock)
                .processDeletion(fixture.fileId());

        verifyNoInteractions(storage);
        assertState(previousStatus == FileStorageStatus.PENDING_DELETE ? "ACTIVE" : previousStatus.name(), 1);
    }

    private void reserve() {
        reservations.replaceReferences(fixture.scheduleId(), fixture.userId(), null, List.of(fixture.fileId()));
    }

    private int requestDeletion(CleanupRequest request) {
        if (request == CleanupRequest.UPLOADER_DISCARD) {
            return discard.discardTemporaryUploads(fixture.userId(), List.of(fixture.fileId())).discardedCount();
        }
        return cleanup.requestDeletionBatch(UPLOADED_AT.plusDays(1), UPLOADED_AT, fixture.fileId() - 1,
                1, LocalDateTime.now()).requestedCount();
    }

    private void awaitBlockedOnCurrentTransaction() {
        await().pollInSameThread().atMost(5, TimeUnit.SECONDS).until(() ->
                Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM pg_locks l WHERE NOT l.granted
                            AND pg_backend_pid() = ANY(pg_blocking_pids(l.pid)))
                        """, Boolean.class)));
    }

    private void assertState(String expectedStatus, int expectedReservations) {
        assertThat(jdbc.queryForObject("SELECT storage_status FROM files WHERE file_id = ?",
                String.class, fixture.fileId())).isEqualTo(expectedStatus);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scheduled_post_files WHERE file_id = ?",
                Integer.class, fixture.fileId())).isEqualTo(expectedReservations);
    }
}
