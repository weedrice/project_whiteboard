package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
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
                FIXED_CLOCK,
                transactionTemplate,
                metrics);
    }

    @Test
    void processPage_blindedPost_completesWithoutFanout() {
        Post post = visiblePost();
        post.blind("moderation", FIXED_NOW);
        KeywordNotificationFanoutJob job = givenDueJob(post);

        assertThat(processor.processPage(1L)).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(subscriptionRepository, never()).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, never()).enqueue(any());
    }

    @Test
    void processPage_inactiveBoard_completesWithoutFanout() {
        Post post = visiblePost();
        post.getBoard().deactivate();
        KeywordNotificationFanoutJob job = givenDueJob(post);

        assertThat(processor.processPage(1L)).isTrue();

        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(subscriptionRepository, never()).findMatchingTitleAfter(any(), any(), any());
        verify(deliveryJobService, never()).enqueue(any());
    }

    @Test
    void overlappingKeywords_enqueueOneJobAndOneKickoffPerReceiver() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        givenDueJob(post);
        User receiver = user(2L);
        var first = subscription(1L, receiver, "vue");
        var second = subscription(2L, receiver, "vue3");
        var otherReceiver = subscription(3L, user(3L), "vue");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(first, second, otherReceiver));

        processor.processPage(1L);

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

        processor.processPage(1L);
        assertThat(job.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.PENDING);
        assertThat(jobs).hasSize(100);
        jobs.values().forEach(NotificationDeliveryJob::complete);
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofHours(1)));
        processor.processPage(1L);

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
        KeywordNotificationFanoutJob fanout = givenDueJob(visiblePost());
        fanout.advance(100L, false, FIXED_NOW);
        var overlapping = subscription(101L, receiver, "vue3");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(100L), any()))
                .thenReturn(List.of(overlapping));

        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofHours(1)));
        processor.processPage(1L);

        assertThat(jobs).containsOnlyKeys(legacyEvent.getEventId(), laterLegacyEvent.getEventId());
        assertThat(overlapping.getLastNotifiedAt()).isEqualTo(FIXED_NOW);
        assertThat(fanout.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
        verify(durableKickoff, times(2)).process(any());
    }

    @Test
    void repeatedPage_doesNotRewindNewerCooldownOrCreateAnotherJob() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        givenDueJob(post);
        User receiver = user(2L);
        var subscription = subscription(1L, receiver, "vue");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(subscription));
        processor.processPage(1L);

        // Simulate an independent notification, then retry the old fan-out page.
        subscription.markNotified(FIXED_NOW.plusMinutes(20));
        givenDueJob(post);
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofHours(1)));
        processor.processPage(1L);

        assertThat(jobs).hasSize(1);
        assertThat(subscription.getLastNotifiedAt()).isEqualTo(FIXED_NOW.plusMinutes(20));
        verify(durableKickoff).process(any());
    }

    @Test
    void differentPostAfterCooldown_stillEnqueuesAnotherJob() {
        var jobs = useDurableDeliveryService(FIXED_CLOCK);
        Post post = visiblePost();
        givenDueJob(post);
        var subscription = subscription(1L, user(2L), "vue");
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(subscription));
        processor.processPage(1L);

        ReflectionTestUtils.setField(post, "postId", 11L);
        givenDueJob(post);
        recreateProcessor(Clock.offset(FIXED_CLOCK, java.time.Duration.ofMinutes(10)));
        processor.processPage(1L);

        assertThat(jobs.values()).extracting(NotificationDeliveryJob::getSourceId)
                .containsExactlyInAnyOrder(10L, 11L);
        verify(durableKickoff, times(2)).process(any());
    }

    @Test
    void subscriptionWithinCooldown_doesNotEnqueueOrChangeTimestamp() {
        givenDueJob(visiblePost());
        var subscription = subscription(1L, user(2L), "vue");
        subscription.markNotified(FIXED_NOW.minusMinutes(9));
        when(subscriptionRepository.findMatchingTitleAfter(any(), eq(0L), any()))
                .thenReturn(List.of(subscription));

        processor.processPage(1L);

        assertThat(subscription.getLastNotifiedAt()).isEqualTo(FIXED_NOW.minusMinutes(9));
        verify(deliveryJobService, never()).enqueue(any());
        verify(deliveryJobRepository, never()).findKeywordPostJobsForReceivers(any(), any());
    }

    private NotificationDeliveryJobKickoff durableKickoff;
    private NotificationDeliveryJobRepository durableRepository;

    private Map<UUID, NotificationDeliveryJob> useDurableDeliveryService(Clock clock) {
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
                userBlockService, clock, transactionTemplate, metrics);
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

    private KeywordNotificationFanoutJob givenDueJob(Post post) {
        KeywordNotificationFanoutJob job = KeywordNotificationFanoutJob.pending(post.getPostId(), FIXED_NOW);
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
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
