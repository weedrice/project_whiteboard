package com.weedrice.whiteboard.domain.file.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileVariantCleanupWorkerTest {
    @Mock private FileVariantRepository fileVariantRepository;
    @Mock private FileVariantStateCommand stateCommand;
    @Mock private FileUploadStateCommand uploadStateCommand;
    @Mock private Clock clock;
    @Mock private FileRepository fileRepository;
    @Mock private FileImageVariantGenerator variantGenerator;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private Instant now = Instant.parse("2026-11-18T00:00:00Z");
    private FileVariantCleanupWorker worker;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenAnswer(invocation -> now);
        worker = new FileVariantCleanupWorker(fileVariantRepository, stateCommand, uploadStateCommand,
                clock, fileRepository, variantGenerator, new FileVariantCleanupMetrics(registry));
    }

    @ParameterizedTest
    @CsvSource({"0, true, 1", "0, false, 0", "9, true, 0"})
    void cleanupRetryMetricCountsOnlyCommittedRetriesBelowTheLimit(
            int previousRetryCount, boolean failureRecorded, int expectedRetries) {
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        var claim = new FileVariantStateCommand.FileVariantCleanupSnapshot(3L, previousRetryCount);
        when(fileVariantRepository.findCleanupCandidateIds(any(), any(), eq(10), any())).thenReturn(List.of(3L));
        when(stateCommand.claimCleanup(eq(3L), any(), any(), eq(10))).thenReturn(claim);
        when(stateCommand.deleteClaimed(claim)).thenThrow(new IllegalStateException("unavailable"));
        when(stateCommand.recordCleanupFailure(eq(claim), eq("IllegalStateException"), any())).thenReturn(failureRecorded);

        assertThat(worker.cleanupStaleVariants()).isZero();
        assertThat(registry.get("noviis.file.variant.cleanup").tag("outcome", "retry").counter().count())
                .isEqualTo(expectedRetries);
    }

    @Test
    void failureDoesNotMarkCompletionAndDoesNotBlockNextFile() {
        File failing = file(1L);
        File succeeding = file(2L);
        candidates(failing, succeeding);
        when(variantGenerator.generateMissingVariants(failing)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
        when(variantGenerator.generateMissingVariants(succeeding)).thenReturn(result(2));

        assertThat(worker.reconcileMissingVariants()).isEqualTo(2);

        verify(uploadStateCommand, never()).markVariantReconciled(eq(1L), anyInt(), anyInt(), anyInt(), anyInt());
        verify(uploadStateCommand).markVariantReconciled(2L, 1600, 800, 2, 1);
        assertThat(counter("failure")).isEqualTo(1);
        assertThat(counter("success")).isEqualTo(1);
        assertThat(backoffFiles()).isEqualTo(1);
    }

    @Test
    void skipsRetryUntilDueAndClearsBackoffAfterRecovery() {
        File file = file(1L);
        candidates(file);
        when(variantGenerator.generateMissingVariants(file))
                .thenThrow(new BusinessException(ErrorCode.FILE_LOAD_ERROR)).thenReturn(result(1));
        worker.reconcileMissingVariants();
        now = now.plus(Duration.ofMinutes(59));

        assertThat(worker.reconcileMissingVariants()).isZero();
        verify(variantGenerator).generateMissingVariants(file);
        assertThat(counter("deferred")).isEqualTo(1);
        assertThat(backoffFiles()).isEqualTo(1);

        now = now.plus(Duration.ofMinutes(1));
        assertThat(worker.reconcileMissingVariants()).isEqualTo(1);
        verify(variantGenerator, times(2)).generateMissingVariants(file);
        assertThat(backoffFiles()).isZero();
        assertThat(counter("success")).isEqualTo(1);
    }

    @Test
    void retriesWithExponentialBackoffCappedAtOneDay() {
        File file = file(1L);
        candidates(file);
        when(variantGenerator.generateMissingVariants(file)).thenThrow(new BusinessException(ErrorCode.INVALID_FILE_TYPE));
        worker.reconcileMissingVariants();

        int attempts = 1;
        for (int hours : new int[] {1, 2, 4, 8, 16, 24, 24}) {
            now = now.plus(Duration.ofHours(hours)).minusSeconds(1);
            worker.reconcileMissingVariants();
            verify(variantGenerator, times(attempts)).generateMissingVariants(file);
            now = now.plusSeconds(1);
            worker.reconcileMissingVariants();
            verify(variantGenerator, times(++attempts)).generateMissingVariants(file);
        }
        assertThat(counter("failure")).isEqualTo(8);
        verify(uploadStateCommand, never()).markVariantReconciled(anyLong(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    void failedCompletionIsCountedAsFailureAndRetried() {
        File file = file(1L);
        candidates(file);
        when(variantGenerator.generateMissingVariants(file)).thenReturn(result(2));
        doThrow(new IllegalStateException("database unavailable")).when(uploadStateCommand)
                .markVariantReconciled(1L, 1600, 800, 2, 1);

        assertThat(worker.reconcileMissingVariants()).isZero();
        assertThat(counter("failure")).isEqualTo(1);
        assertThat(counter("success")).isZero();
        assertThat(backoffFiles()).isEqualTo(1);
    }

    @Test
    void deferredFileAdvancesCursorAndDoesNotBlockLaterFile() {
        File failing = file(1L);
        File succeeding = file(2L);
        when(fileRepository.findActiveImagesMissingVariantsAfter(anyLong(), anyInt(), any()))
                .thenReturn(List.of(failing), List.of(failing, succeeding), List.of());
        when(variantGenerator.generateMissingVariants(failing)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
        when(variantGenerator.generateMissingVariants(succeeding)).thenReturn(result(1));
        worker.reconcileMissingVariants();

        assertThat(worker.reconcileMissingVariants()).isEqualTo(1);
        worker.reconcileMissingVariants();

        verify(variantGenerator).generateMissingVariants(failing);
        verify(fileRepository).findActiveImagesMissingVariantsAfter(eq(2L), eq(1), any());
        assertThat(counter("deferred")).isEqualTo(1);
    }

    @Test
    void emptyBatchResetsCursorAndUpdatesExpiredBackoffGauge() {
        File file = file(1L);
        when(fileRepository.findActiveImagesMissingVariantsAfter(anyLong(), anyInt(), any()))
                .thenReturn(List.of(file), List.of(), List.of());
        when(variantGenerator.generateMissingVariants(file)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));
        worker.reconcileMissingVariants();
        now = now.plus(Duration.ofHours(1));

        assertThat(worker.reconcileMissingVariants()).isZero();
        assertThat(backoffFiles()).isZero();
        worker.reconcileMissingVariants();

        verify(fileRepository, times(2)).findActiveImagesMissingVariantsAfter(eq(0L), eq(1), any());
    }

    @Test
    void failureLogIdentifiesErrorCodeWithoutExceptionMessageOrStackTrace() {
        File file = file(1L);
        candidates(file);
        when(variantGenerator.generateMissingVariants(file))
                .thenThrow(new BusinessException(ErrorCode.INVALID_FILE_TYPE, "private-original-name-secret"));
        Logger logger = (Logger) LoggerFactory.getLogger(FileVariantCleanupWorker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            worker.reconcileMissingVariants();
            assertThat(appender.list).hasSize(1);
            ILoggingEvent event = appender.list.getFirst();
            assertThat(event.getFormattedMessage()).contains("errorCode=INVALID_FILE_TYPE", "nextAttemptAt=")
                    .doesNotContain("private-original-name-secret", "original.jpg");
            assertThat(event.getThrowableProxy()).isNull();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private void candidates(File... files) {
        when(fileRepository.findActiveImagesMissingVariantsAfter(anyLong(), anyInt(), any()))
                .thenReturn(List.of(files));
    }

    private File file(long id) {
        File file = File.builder().filePath("original.jpg").originalName("original.jpg")
                .fileSize(100L).mimeType("image/jpeg").build();
        ReflectionTestUtils.setField(file, "fileId", id);
        return file;
    }

    private FileImageVariantGenerator.ReconciliationResult result(int generated) {
        return new FileImageVariantGenerator.ReconciliationResult(generated, 1600, 800, 2);
    }

    private double counter(String outcome) {
        return registry.get("noviis.file.variant.reconciliation").tag("outcome", outcome).counter().count();
    }

    private double backoffFiles() {
        return registry.get("noviis.file.variant.reconciliation.backoff_files").gauge().value();
    }
}
