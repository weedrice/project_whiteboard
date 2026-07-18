package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.notification.repository.KeywordNotificationFanoutJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordNotificationFanoutProcessorTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-18T00:00:00Z"),
            ZoneOffset.UTC);
    private static final LocalDateTime FIXED_NOW = LocalDateTime.now(FIXED_CLOCK);

    @Mock private KeywordNotificationFanoutJobRepository jobRepository;
    @Mock private UserKeywordSubscriptionRepository subscriptionRepository;
    @Mock private PostRepository postRepository;
    @Mock private NotificationDeliveryJobService deliveryJobService;
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
                deliveryJobService,
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

    private KeywordNotificationFanoutJob givenDueJob(Post post) {
        KeywordNotificationFanoutJob job = KeywordNotificationFanoutJob.pending(10L, FIXED_NOW);
        when(jobRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(job));
        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
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
        return Post.builder()
                .board(board)
                .user(author)
                .title("keyword title")
                .contents("contents")
                .build();
    }
}
