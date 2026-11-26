package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, FileVariantStateCommand.class})
class FileVariantDeletionPostgresTest {
    @Autowired private FileRepository files;
    @Autowired private FileVariantRepository variants;
    @Autowired private UserRepository users;
    @Autowired private FileVariantStateCommand command;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private FileStorageService storage;

    private final byte[] contents = {1, 2, 3};
    private final Clock clock = Clock.systemUTC();
    private File file;
    private FileVariant variant;
    private Long uploaderId;
    private FileUploadStateCommand uploads;

    @BeforeEach
    void createPendingVariant() {
        String name = "var-" + UUID.randomUUID().toString().substring(0, 8);
        file = transactions.execute(status -> {
            User uploader = users.saveAndFlush(User.builder().loginId(name).displayName(name)
                    .email(name + "@example.org").password("test-password").build());
            uploaderId = uploader.getUserId();
            return files.saveAndFlush(File.builder().filePath(name + ".png").originalName("image.png")
                    .mimeType("image/png").fileSize(20L).uploader(uploader)
                    .storageStatus(FileStorageStatus.PENDING_UPLOAD).build());
        });
        variant = command.createPending(file, FileVariantType.THUMBNAIL, name + "-thumb.png",
                contents.length, "image/png", 1, 1);
        uploads = new FileUploadStateCommand(files, users, mock(UserWritableResolver.class), transactions, clock);
    }

    @AfterEach
    void removeFixtures() {
        transactions.executeWithoutResult(status -> {
            if (file != null) {
                variants.deleteByFileFileId(file.getFileId());
                variants.flush();
                files.deleteById(file.getFileId());
                files.flush();
            }
            if (uploaderId != null) {
                users.deleteById(uploaderId);
            }
        });
    }

    @Test
    void deletionWaitsForStorageAndRemovesTheActivatedVariant() throws Exception {
        Long fileId = file.getFileId();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var storageStarted = new CountDownLatch(1);
            var deletion = executor.submit(() -> {
                assertThat(storageStarted.await(10, TimeUnit.SECONDS)).isTrue();
                uploads.requestPendingUploadDeletion(fileId);
                return null;
            });
            doAnswer(call -> {
                storageStarted.countDown();
                awaitBlockedOnThisTransaction();
                return null;
            }).when(storage).storeBytesAs(contents, variant.getMimeType(), variant.getFilePath());

            command.storeAndActivate(fileId, variant.getFileVariantId(), contents);
            deletion.get(10, TimeUnit.SECONDS);
        }
        assertThat(files.findById(fileId).orElseThrow().getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_DELETE);
        assertThat(variants.findById(variant.getFileVariantId()).orElseThrow().getStorageStatus())
                .isEqualTo(FileStorageStatus.ACTIVE);

        var worker = new FileDeletionWorker(files, variants, storage, transactions,
                mock(EmoticonFileReferenceService.class), clock);
        worker.processDeletion(fileId);

        var calls = inOrder(storage);
        calls.verify(storage).storeBytesAs(contents, variant.getMimeType(), variant.getFilePath());
        calls.verify(storage).deleteFileOrThrow(variant.getFilePath());
        calls.verify(storage).deleteFileOrThrow(file.getFilePath());
        assertThat(files.existsById(fileId)).isFalse();
        assertThat(variants.existsById(variant.getFileVariantId())).isFalse();
    }

    @Test
    void overlappingCleanupClaimsDeleteOnceAndPreserveAReplacementAtTheSamePath() throws Exception {
        var now = variant.getCreatedAt().plusHours(3);
        var cutoff = now.minusHours(2);
        var first = command.claimCleanup(variant.getFileVariantId(), cutoff, now, 10);
        var delayed = command.claimCleanup(variant.getFileVariantId(), cutoff, now, 10);
        assertThat(first).isNotNull();
        assertThat(delayed).isEqualTo(first);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var deletionStarted = new CountDownLatch(1);
            var competing = executor.submit(() -> {
                assertThat(deletionStarted.await(10, TimeUnit.SECONDS)).isTrue();
                return command.deleteClaimed(delayed);
            });
            doAnswer(call -> {
                deletionStarted.countDown();
                awaitBlockedOnThisTransaction();
                return null;
            }).when(storage).deleteFileOrThrow(variant.getFilePath());

            assertThat(command.deleteClaimed(first)).isTrue();
            assertThat(competing.get(10, TimeUnit.SECONDS)).isFalse();
        }

        FileVariant replacement = command.createPending(file, variant.getVariantType(), variant.getFilePath(),
                contents.length, variant.getMimeType(), 1, 1);
        command.storeAndActivate(file.getFileId(), replacement.getFileVariantId(), contents);
        assertThat(command.deleteClaimed(delayed)).isFalse();

        org.mockito.Mockito.verify(storage).deleteFileOrThrow(variant.getFilePath());
        assertThat(variants.findById(replacement.getFileVariantId()).orElseThrow().getStorageStatus())
                .isEqualTo(FileStorageStatus.ACTIVE);
    }

    @Test
    void deletionCommittedWhileAWriterWaitsPreventsLateObjectWritesAndNewIntents() {
        Long fileId = file.getFileId();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writing = transactions.execute(status -> {
                uploads.requestPendingUploadDeletion(fileId);
                files.flush();
                var result = executor.submit(() -> command.storeAndActivate(fileId, variant.getFileVariantId(), contents));
                awaitBlockedOnThisTransaction();
                return result;
            });
            assertThatThrownBy(() -> writing.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class).hasCauseInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> command.createPending(file, FileVariantType.MEDIUM, "late.png",
                3, "image/png", 2, 2)).isInstanceOf(IllegalStateException.class);
        assertThat(variants.findByFileFileId(fileId)).singleElement()
                .satisfies(pending -> assertThat(pending.getStorageStatus()).isEqualTo(FileStorageStatus.PENDING_UPLOAD));
        verifyNoInteractions(storage);
    }

    private void awaitBlockedOnThisTransaction() {
        // Wait for the other session's actual lock request on this transaction.
        await().pollInSameThread().atMost(10, TimeUnit.SECONDS).until(() ->
                Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT EXISTS (SELECT FROM pg_locks
                           WHERE NOT granted AND pg_backend_pid() = ANY(pg_blocking_pids(pid)))
                        """, Boolean.class)));
    }
}
