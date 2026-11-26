package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.KeywordFanoutPage;
import com.weedrice.whiteboard.domain.notification.repository.KeywordNotificationFanoutJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class KeywordNotificationFanoutProcessorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T00:00:00Z"),
            ZoneOffset.UTC);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock private KeywordNotificationFanoutJobRepository jobRepository;
    @Mock private UserKeywordSubscriptionRepository subscriptionRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private NotificationDeliveryJobService deliveryJobService;
    @Mock private NotificationDeliveryJobRepository deliveryJobRepository;
    @Mock private UserBlockService userBlockService;
    @Mock private ContentAuthorVisibilityRepository authorVisibilityRepository;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private KeywordNotificationFanoutMetrics metrics;

    private KeywordNotificationFanoutProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new KeywordNotificationFanoutProcessor(
                jobRepository,
                subscriptionRepository,
                postRepository,
                userRepository,
                agentRepository,
                deliveryJobService,
                deliveryJobRepository,
                userBlockService,
                authorVisibilityRepository,
                FIXED_CLOCK,
                transactionTemplate,
                metrics);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedAttemptOnlyChargesTheSamePendingPage(boolean anotherWorkerAdvanced) {
        KeywordFanoutPage page = new KeywordFanoutPage(1L, 100L, 0);
        KeywordNotificationFanoutJob first = KeywordNotificationFanoutJob.pending(10L, FIXED_NOW);
        first.advance(100L, false, FIXED_NOW);
        KeywordNotificationFanoutJob persisted = KeywordNotificationFanoutJob.pending(10L, FIXED_NOW);
        persisted.advance(anotherWorkerAdvanced ? 200L : 100L, false, FIXED_NOW);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(new SimpleTransactionStatus()));
        when(jobRepository.findDuePages(eq(FIXED_NOW), any())).thenReturn(List.of(page));
        when(jobRepository.findDuePageForUpdate(1L, 100L, 0, FIXED_NOW))
                .thenReturn(Optional.of(first))
                .thenReturn(anotherWorkerAdvanced ? Optional.empty() : Optional.of(persisted));
        when(postRepository.findByIdWithRelations(10L)).thenThrow(new IllegalStateException("page read failed"));

        assertThat(processor.processDueJobs()).isZero();

        verify(jobRepository, times(2)).findDuePageForUpdate(1L, 100L, 0, FIXED_NOW);
        assertThat(persisted.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.PENDING);
        assertThat(persisted.getRetryCount()).isEqualTo(anotherWorkerAdvanced ? 0 : 1);
        assertThat(persisted.getNextAttemptAt()).isEqualTo(anotherWorkerAdvanced ? FIXED_NOW : FIXED_NOW.plusMinutes(1));
        verify(metrics, times(anotherWorkerAdvanced ? 0 : 1)).recordRetry();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void fifthFailureUpdatesDeadLetterGaugeWithoutCountingAnotherRetry(CapturedOutput output) {
        var job = KeywordNotificationFanoutJob.pending(10L, FIXED_NOW);
        for (int attempt = 0; attempt < 4; attempt++) {
            job.fail("previous failure", FIXED_NOW);
        }
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(new SimpleTransactionStatus()));
        when(jobRepository.findDuePages(eq(FIXED_NOW), any())).thenReturn(List.of(page(job)));
        when(jobRepository.findDuePageForUpdate(1L, 0L, 4, FIXED_NOW))
                .thenThrow(new IllegalStateException("page unavailable"))
                .thenReturn(Optional.of(job));
        when(jobRepository.countByStatus(KeywordNotificationFanoutJob.Status.PENDING)).thenReturn(0L);
        when(jobRepository.countByStatus(KeywordNotificationFanoutJob.Status.FAILED)).thenReturn(1L);

        assertThat(processor.processDueJobs()).isZero();

        assertThat(job.getRetryCount()).isEqualTo(5);
        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.FAILED);
        assertThat(job.getLastError()).isEqualTo("IllegalStateException");
        assertThat(output.getAll()).contains("jobId=1, exceptionType=IllegalStateException, status=FAILED");
        verify(metrics).update(0L, 1L, 0L);
        verify(metrics, never()).recordRetry();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void recordingFailureAllowsOtherJobsToCompleteAndOriginalPageToRetry(CapturedOutput output) {
        KeywordFanoutPage first = new KeywordFanoutPage(1L, 0L, 0);
        KeywordFanoutPage second = new KeywordFanoutPage(2L, 0L, 0);
        var firstJob = KeywordNotificationFanoutJob.pending(10L, FIXED_NOW);
        var secondJob = KeywordNotificationFanoutJob.pending(20L, FIXED_NOW);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(new SimpleTransactionStatus()));
        when(jobRepository.findDuePages(eq(FIXED_NOW), any()))
                .thenReturn(List.of(first, second)).thenReturn(List.of(first));
        when(jobRepository.findDuePageForUpdate(1L, 0L, 0, FIXED_NOW))
                .thenThrow(new IllegalStateException("page unavailable"))
                .thenThrow(new TransientDataAccessResourceException("failure record unavailable"))
                .thenReturn(Optional.of(firstJob));
        when(jobRepository.findDuePageForUpdate(2L, 0L, 0, FIXED_NOW)).thenReturn(Optional.of(secondJob));
        when(jobRepository.countByStatus(KeywordNotificationFanoutJob.Status.PENDING)).thenReturn(1L, 0L);
        when(jobRepository.findOldestPendingAttemptAt()).thenReturn(Optional.of(FIXED_NOW.minusMinutes(2)))
                .thenReturn(Optional.empty());

        assertThat(processor.processDueJobs()).isOne();
        assertThat(secondJob.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(metrics).update(1L, 0L, 120L);
        assertThat(output.getAll()).contains("jobId=1, cursor=0, retryCount=0",
                "pageExceptionType=IllegalStateException, recordingExceptionType=TransientDataAccessResourceException");

        assertThat(processor.processDueJobs()).isOne();
        assertThat(firstJob.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        assertThat(firstJob.getRetryCount()).isZero();
        verify(metrics).update(0L, 0L, 0L);
        verify(metrics, never()).recordRetry();
    }

    @Test
    void processPage_blindedPost_completesWithoutFanout() {
        Post post = visiblePost();
        post.blind("moderation", FIXED_NOW);
        KeywordNotificationFanoutJob job = givenDueJob(post);

        assertThat(processor.processPage(page(job))).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(subscriptionRepository, never()).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, never()).enqueue(any());
    }

    @Test
    void processPage_inactiveBoard_completesWithoutFanout() {
        Post post = visiblePost();
        post.getBoard().deactivate();
        KeywordNotificationFanoutJob job = givenDueJob(post);

        assertThat(processor.processPage(page(job))).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(subscriptionRepository, never()).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, never()).enqueue(any());
    }

    @Test
    void processPage_hiddenAuthor_completesWithoutFanoutOrRetry() {
        KeywordNotificationFanoutJob job = givenDueJob(visiblePost());
        when(authorVisibilityRepository.findVisibleUserIds(List.of(1L))).thenReturn(Set.of());

        assertThat(processor.processPage(page(job))).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(job.getLastSubscriptionId()).isZero();
        assertThat(job.getProcessingStartedAt()).isNull();
        verify(subscriptionRepository, never()).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, never()).enqueue(any());
    }

    @Test
    void processPage_authorHiddenBetweenPages_stopsRemainingFanout() {
        KeywordNotificationFanoutJob job = givenDueJob(visiblePost());
        when(authorVisibilityRepository.findVisibleUserIds(List.of(1L)))
                .thenReturn(Set.of(1L)).thenReturn(Set.of());
        List<UserKeywordSubscription> firstPage = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(id -> subscription(id, user(id + 1), "vue")).toList();
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any())).thenReturn(firstPage);

        assertThat(processor.processPage(page(job))).isTrue();
        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.PENDING);
        assertThat(job.getLastSubscriptionId()).isEqualTo(100L);
        assertThat(processor.processPage(page(job))).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        assertThat(job.getLastSubscriptionId()).isEqualTo(100L);
        assertThat(job.getRetryCount()).isZero();
        verify(subscriptionRepository, times(1)).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, times(100)).enqueue(any());
    }

    @Test
    void processPage_missingAuthorId_completesWithoutFanout() {
        Post post = visiblePost();
        ReflectionTestUtils.setField(post, "userId", null);
        KeywordNotificationFanoutJob job = givenDueJob(post);

        assertThat(processor.processPage(page(job))).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(authorVisibilityRepository, never()).findVisibleUserIds(any());
        verify(subscriptionRepository, never()).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, never()).enqueue(any());
    }

    @Test
    void overlappingKeywords_enqueueOneJobAndOneKickoffPerReceiver() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        var job = givenDueJob(post);
        User receiver = user(2L);
        var first = subscription(1L, receiver, "vue");
        var second = subscription(2L, receiver, "vue3");
        var otherReceiver = subscription(3L, user(3L), "vue");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(first, second, otherReceiver));

        processor.processPage(page(job));

        assertThat(jobs.values()).extracting(NotificationDeliveryJob::getReceiverUserId)
                .containsExactlyInAnyOrder(2L, 3L);
        assertThat(first.getLastNotifiedAt()).isEqualTo(FIXED_NOW);
        assertThat(second.getLastNotifiedAt()).isEqualTo(FIXED_NOW);
        verify(durableKickoff, times(2)).process(any());
        verify(durableRepository).findKeywordPostJobsForReceivers(10L, List.of(2L, 3L));
    }

    @Test
    void anotherPageAfterWorkerRestart_reusesCompletedJobWithoutExtendingCooldown() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        KeywordNotificationFanoutJob job = givenDueJob(post);
        User receiver = user(2L);
        var firstPage = new ArrayList<UserKeywordSubscription>();
        firstPage.add(subscription(1L, receiver, "vue"));
        for (long id = 2; id <= 100; id++) {
            firstPage.add(subscription(id, user(id + 1), "vue"));
        }
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any())).thenReturn(firstPage);
        var overlapping = subscription(101L, receiver, "vue3");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(100L), any()))
                .thenReturn(List.of(overlapping));

        processor.processPage(page(job));
        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.PENDING);
        assertThat(jobs).hasSize(100);
        jobs.values().forEach(NotificationDeliveryJob::complete);
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofHours(1)));
        processor.processPage(page(job));

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        assertThat(jobs).hasSize(100);
        assertThat(overlapping.getLastNotifiedAt()).isEqualTo(FIXED_NOW);
        verify(durableKickoff, times(100)).process(any());
        verify(durableRepository).findKeywordPostJobsForReceivers(10L,
                java.util.stream.LongStream.rangeClosed(2, 101).boxed().toList());
        verify(durableRepository).findKeywordPostJobsForReceivers(10L, List.of(2L));
        verify(durableRepository, times(2)).findKeywordPostJobsForReceivers(any(), any());
    }

    @Test
    void resumedPage_reusesLegacyRandomIdDeliveryWithoutAnotherKickoff() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        User receiver = user(2L);
        NotificationEvent legacyEvent = NotificationEvent.localized(receiver, null, NotificationType.KEYWORD,
                NotificationSourceType.POST, 10L, "notification.keyword.matched", "vue3 release");
        NotificationDeliveryJob legacyJob = deliveryJobService.enqueue(legacyEvent);
        legacyJob.complete();
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofMinutes(1)));
        NotificationEvent laterLegacyEvent = NotificationEvent.localized(receiver, null, NotificationType.KEYWORD,
                NotificationSourceType.POST, 10L, "notification.keyword.matched", "vue3 release");
        deliveryJobService.enqueue(laterLegacyEvent).complete();
        KeywordNotificationFanoutJob job = givenDueJob(visiblePost());
        job.advance(100L, false, FIXED_NOW);
        var overlapping = subscription(101L, receiver, "vue3");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(100L), any()))
                .thenReturn(List.of(overlapping));

        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofHours(1)));
        processor.processPage(page(job));

        assertThat(jobs).containsOnlyKeys(legacyEvent.getEventId(), laterLegacyEvent.getEventId());
        assertThat(overlapping.getLastNotifiedAt()).isEqualTo(FIXED_NOW);
        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(durableKickoff, times(2)).process(any());
    }

    @Test
    void repeatedPage_doesNotRewindNewerCooldownOrCreateAnotherJob() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        var job = givenDueJob(post);
        User receiver = user(2L);
        var subscription = subscription(1L, receiver, "vue");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(subscription));
        processor.processPage(page(job));

        // Simulate an independent notification, then retry the old fan-out page.
        subscription.markNotified(FIXED_NOW.plusMinutes(20));
        job = givenDueJob(post);
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofHours(1)));
        processor.processPage(page(job));

        assertThat(jobs).hasSize(1);
        assertThat(subscription.getLastNotifiedAt()).isEqualTo(FIXED_NOW.plusMinutes(20));
        verify(durableKickoff).process(any());
    }

    @Test
    void differentPostAfterCooldown_stillEnqueuesAnotherJob() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        var job = givenDueJob(post);
        var subscription = subscription(1L, user(2L), "vue");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(subscription));
        processor.processPage(page(job));

        ReflectionTestUtils.setField(post, "postId", 11L);
        job = givenDueJob(post);
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofMinutes(10)));
        processor.processPage(page(job));

        assertThat(jobs.values()).extracting(NotificationDeliveryJob::getSourceId)
                .containsExactlyInAnyOrder(10L, 11L);
        verify(durableKickoff, times(2)).process(any());
    }

    @Test
    void subscriptionWithinCooldown_doesNotEnqueueOrChangeTimestamp() {
        when(authorVisibilityRepository.findVisibleUserIds(List.of(1L))).thenReturn(Set.of(1L));
        var job = givenDueJob(visiblePost());
        var subscription = subscription(1L, user(2L), "vue");
        subscription.markNotified(FIXED_NOW.minusMinutes(9));
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(subscription));

        processor.processPage(page(job));

        assertThat(subscription.getLastNotifiedAt()).isEqualTo(FIXED_NOW.minusMinutes(9));
        verify(deliveryJobService, never()).enqueue(any());
        verify(deliveryJobRepository, never()).findKeywordPostJobsForReceivers(any(), any());
    }

    private NotificationDeliveryJobKickoff durableKickoff;
    private NotificationDeliveryJobRepository durableRepository;

    private Map<UUID, NotificationDeliveryJob> useDurableDeliveryService(Clock clock) {
        when(authorVisibilityRepository.findVisibleUserIds(List.of(1L))).thenReturn(Set.of(1L));
        var jobs = new HashMap<UUID, NotificationDeliveryJob>();
        durableRepository = mock(NotificationDeliveryJobRepository.class);
        durableKickoff = mock(NotificationDeliveryJobKickoff.class);
        when(durableRepository.insertIfAbsent(any(), any())).thenAnswer(invocation -> {
            NotificationDeliveryJob candidate = invocation.getArgument(0);
            ReflectionTestUtils.setField(candidate, "jobId", (long) jobs.size() + 1);
            ReflectionTestUtils.setField(candidate, "createdAt", invocation.getArgument(1));
            return jobs.putIfAbsent(candidate.getEventId(), candidate) == null ? 1 : 0;
        });
        when(durableRepository.findByEventId(any())).thenAnswer(invocation ->
                Optional.ofNullable(jobs.get(invocation.getArgument(0))));
        when(durableRepository.findKeywordPostJobsForReceivers(any(), any())).thenAnswer(invocation -> {
            Collection<Long> receiverIds = invocation.getArgument(1);
            return jobs.values().stream()
                    .filter(job -> job.getNotificationType() == NotificationType.KEYWORD
                            && job.getSourceType() == NotificationSourceType.POST
                            && job.getSourceId().equals(invocation.getArgument(0))
                            && receiverIds.contains(job.getReceiverUserId()))
                    .sorted(Comparator.comparing(NotificationDeliveryJob::getCreatedAt)
                            .thenComparing(NotificationDeliveryJob::getJobId)).toList();
        });
        recreateProcessor(clock);
        return jobs;
    }

    private void recreateProcessor(Clock clock) {
        if (durableRepository != null) {
            deliveryJobService = new NotificationDeliveryJobService(
                    durableRepository, durableKickoff, clock, new NotificationPayloadValidator());
        }
        processor = new KeywordNotificationFanoutProcessor(
                jobRepository, subscriptionRepository, postRepository, userRepository, agentRepository,
                deliveryJobService, durableRepository != null ? durableRepository : deliveryJobRepository,
                userBlockService, authorVisibilityRepository, clock, transactionTemplate, metrics);
    }

    private UserKeywordSubscription subscription(Long id, User receiver, String keyword) {
        UserKeywordSubscription subscription = UserKeywordSubscription.builder()
                .user(receiver).keyword(keyword).build();
        ReflectionTestUtils.setField(subscription, "subscriptionId", id);
        return subscription;
    }

    private User user(Long id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }

    private KeywordFanoutPage page(KeywordNotificationFanoutJob job) {
        return new KeywordFanoutPage(1L, job.getLastSubscriptionId(), job.getRetryCount());
    }

    private KeywordNotificationFanoutJob givenDueJob(Post post) {
        KeywordNotificationFanoutJob job = KeywordNotificationFanoutJob.pending(post.getPostId(), FIXED_NOW);
        when(jobRepository.findDuePageForUpdate(eq(1L), any(), any(), any())).thenReturn(Optional.of(job));
        when(postRepository.findByIdWithRelations(post.getPostId())).thenReturn(Optional.of(post));
        return job;
    }

    private Post visiblePost() {
        User author = User.builder()
                .loginId("author")
                .password("password")
                .email("author@example.com")
                .displayName("Author")
                .build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder()
                .boardName("Public")
                .boardUrl("public")
                .creator(author)
                .isPublic(true)
                .build();
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("keyword title")
                .contents("contents")
                .build();
        ReflectionTestUtils.setField(post, "postId", 10L);
        return post;
    }
}
