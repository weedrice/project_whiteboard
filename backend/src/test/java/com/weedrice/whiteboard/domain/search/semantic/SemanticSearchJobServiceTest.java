package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchJobServiceTest {

    private SemanticSearchProperties properties;
    private EmbeddingClient embeddingClient;
    private SemanticSearchJobRepository jobRepository;
    private SemanticSearchJobCommandService jobCommandService;
    private SemanticSearchIndexService indexService;
    private SemanticSearchJobService jobService;

    @BeforeEach
    void setUp() {
        properties = new SemanticSearchProperties();
        properties.setEnabled(true);
        properties.getJob().setBatchSize(10);
        properties.getJob().setMaxRetryCount(3);
        embeddingClient = mock(EmbeddingClient.class);
        when(embeddingClient.isAvailable()).thenReturn(true);
        jobRepository = mock(SemanticSearchJobRepository.class);
        jobCommandService = mock(SemanticSearchJobCommandService.class);
        indexService = mock(SemanticSearchIndexService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-19T06:00:00Z"), ZoneId.of("Asia/Seoul"));
        jobService = new SemanticSearchJobService(
                properties,
                embeddingClient,
                jobRepository,
                jobCommandService,
                indexService,
                clock);
    }

    @Test
    void enqueue_preservesJobWhenDisabled() {
        properties.setEnabled(false);

        jobService.enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);

        verify(jobRepository).enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);
    }

    @Test
    void enqueueMethodsUseRequiresNewTransactions() throws NoSuchMethodException {
        assertRequiresNew("enqueue", String.class, Long.class, SemanticSearchIndexAction.class);
        assertRequiresNew("enqueueAll", String.class, Collection.class, SemanticSearchIndexAction.class);
        assertRequiresNew("enqueuePostComments", Long.class);
        assertRequiresNew("enqueueBoardContent", Long.class);
    }

    @Test
    void enqueueAll_delegatesToRepository() {
        jobService.enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);

        verify(jobRepository).enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);
    }

    @Test
    void processPendingJobs_skipsWhenDisabled() {
        properties.setEnabled(false);

        int processed = jobService.processPendingJobs();

        assertThat(processed).isZero();
        verify(jobRepository, never()).findPendingJobs(anyInt(), anyInt());
    }

    @Test
    void processPendingJobs_skipsWhenEmbeddingClientUnavailable() {
        when(embeddingClient.isAvailable()).thenReturn(false);

        int processed = jobService.processPendingJobs();

        assertThat(processed).isZero();
        verify(jobRepository, never()).findPendingJobs(anyInt(), anyInt());
    }

    @Test
    void enqueuePostComments_enqueuesActiveCommentsForPost() {
        when(jobRepository.findActiveCommentIdsByPostId(10L)).thenReturn(List.of(101L, 102L));

        int count = jobService.enqueuePostComments(10L);

        assertThat(count).isEqualTo(2);
        verify(jobRepository).enqueueAll("COMMENT", List.of(101L, 102L), SemanticSearchIndexAction.UPSERT);
    }

    @Test
    void enqueueBoardContent_enqueuesActivePostsAndCommentsForBoard() {
        when(jobRepository.findActivePostIdsByBoardId(20L)).thenReturn(List.of(201L));
        when(jobRepository.findActiveCommentIdsByBoardId(20L)).thenReturn(List.of(301L, 302L));

        int count = jobService.enqueueBoardContent(20L);

        assertThat(count).isEqualTo(3);
        verify(jobRepository).enqueueAll("POST", List.of(201L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(301L, 302L), SemanticSearchIndexAction.UPSERT);
    }

    @Test
    void processPendingJobs_marksCompletedAfterUpsert() {
        SemanticSearchJob pending = new SemanticSearchJob(1L, "POST", 10L, "UPSERT", null);
        when(jobRepository.findPendingJobs(3, 10)).thenReturn(List.of(pending));
        when(jobCommandService.claimForProcessing(eq(1L), eq(3), any(LocalDateTime.class))).thenReturn(1);
        when(jobCommandService.findClaimed(eq(1L), any(LocalDateTime.class)))
                .thenAnswer(invocation -> Optional.of(new SemanticSearchJob(
                        1L, "POST", 10L, "UPSERT", invocation.getArgument(1))));

        int processed = jobService.processPendingJobs();

        assertThat(processed).isEqualTo(1);
        verify(indexService).upsertPost(10L);
        verify(jobCommandService).markCompleted(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void processPendingJobs_retriesWhenIndexingFails() {
        SemanticSearchJob pending = new SemanticSearchJob(2L, "COMMENT", 20L, "UPSERT", null);
        when(jobRepository.findPendingJobs(3, 10)).thenReturn(List.of(pending));
        when(jobCommandService.claimForProcessing(eq(2L), eq(3), any(LocalDateTime.class))).thenReturn(1);
        when(jobCommandService.findClaimed(eq(2L), any(LocalDateTime.class)))
                .thenAnswer(invocation -> Optional.of(new SemanticSearchJob(
                        2L, "COMMENT", 20L, "UPSERT", invocation.getArgument(1))));
        doThrow(new IllegalStateException("provider down")).when(indexService).upsertComment(20L);

        int processed = jobService.processPendingJobs();

        assertThat(processed).isEqualTo(1);
        verify(jobCommandService).markFailedIfCurrent(eq(2L), any(LocalDateTime.class), eq(3),
                contains("provider down"));
    }

    @Test
    void processPendingJobs_retriesAndDoesNotCompleteWhenIndexPayloadTurnsStale() {
        SemanticSearchJob pending = new SemanticSearchJob(3L, "POST", 30L, "UPSERT", null);
        when(jobRepository.findPendingJobs(3, 10)).thenReturn(List.of(pending));
        when(jobCommandService.claimForProcessing(eq(3L), eq(3), any(LocalDateTime.class))).thenReturn(1);
        when(jobCommandService.findClaimed(eq(3L), any(LocalDateTime.class)))
                .thenAnswer(invocation -> Optional.of(new SemanticSearchJob(
                        3L, "POST", 30L, "UPSERT", invocation.getArgument(1))));
        doThrow(new IllegalStateException("Semantic search post payload changed before write"))
                .when(indexService).upsertPost(30L);

        int processed = jobService.processPendingJobs();

        assertThat(processed).isEqualTo(1);
        verify(jobCommandService, never()).markCompleted(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(jobCommandService).markFailedIfCurrent(eq(3L), any(LocalDateTime.class), eq(3),
                contains("payload changed"));
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = SemanticSearchJobService.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
