package com.weedrice.whiteboard.domain.post.scheduled.service;

import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.dto.PostCreateRequest;
import com.weedrice.whiteboard.domain.post.dto.PostCreateResponse;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostCommandService;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledPostPublishWorkerTest {

    @Mock ScheduledPostRepository repository;
    @Mock PostCommandService postCommandService;
    @Mock ScheduledPostPayloadMapper payloadMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ScheduledPostFileService scheduledPostFileService;

    @Test
    void scheduledPayloadCarriesSourceDraftIdToPublication() {
        User user = User.builder().email("scheduled@example.com").displayName("scheduled-user").build();
        Board board = Board.builder().boardName("Scheduled Board").boardUrl("scheduled-board").build();
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user)
                .board(board)
                .title("Scheduled title")
                .contents("Scheduled contents")
                .fileIdsJson("[10,11]")
                .draftId(77L)
                .scheduledAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .build();

        PostCreateRequest request = new ScheduledPostPayloadMapper(new ObjectMapper())
                .toPostCreateRequest(scheduledPost);

        assertThat(request.getDraftId()).isEqualTo(77L);
        assertThat(request.getFileIds()).containsExactly(10L, 11L);
    }

    @Test
    void publicationStepsUseIndependentTransactions() throws Exception {
        assertRequiresNew("claim", Long.class, LocalDateTime.class, LocalDateTime.class);
        assertRequiresNew("publishClaimed", Long.class, LocalDateTime.class);
        assertRequiresNew("markFailed", Long.class, LocalDateTime.class, RuntimeException.class);
    }

    @Test
    void publishClaimedRollsBackWhenLeaseCompletionUpdateDoesNotOwnOneRow() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        ScheduledPost scheduledPost = scheduledPost();
        PostCreateRequest request = new PostCreateRequest();
        PostCreateResponse response = PostCreateResponse.builder().postId(91L).build();
        when(repository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                7L, ScheduledPost.STATUS_PUBLISHING, claimedAt)).thenReturn(Optional.of(scheduledPost));
        when(payloadMapper.toPostCreateRequest(scheduledPost)).thenReturn(request);
        when(postCommandService.createScheduledPostWithResponse(any(), any(), any(), any())).thenReturn(response);
        when(repository.markPublished(any(), any(), any(), any())).thenReturn(0);

        ScheduledPostPublishWorker worker = worker();

        assertThatThrownBy(() -> worker.publishClaimed(7L, claimedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease changed");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void markFailedRollsBackWhenLeaseFailureUpdateDoesNotOwnOneRow() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        ScheduledPost scheduledPost = scheduledPost();
        when(repository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                7L, ScheduledPost.STATUS_PUBLISHING, claimedAt)).thenReturn(Optional.of(scheduledPost));
        when(repository.markFailed(any(), any(), any())).thenReturn(0);

        ScheduledPostPublishWorker worker = worker();

        assertThatThrownBy(() -> worker.markFailed(7L, claimedAt, new IllegalStateException("boom")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease changed");
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishClaimedFailsWhenClaimedLeaseCanNoLongerBeLoaded() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        when(repository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                7L, ScheduledPost.STATUS_PUBLISHING, claimedAt)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> worker().publishClaimed(7L, claimedAt))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease changed");
        verify(postCommandService, never()).createScheduledPostWithResponse(any(), any(), any(), any());
    }

    @Test
    void markFailedFailsWhenClaimedLeaseCanNoLongerBeLoaded() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        when(repository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                7L, ScheduledPost.STATUS_PUBLISHING, claimedAt)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> worker().markFailed(7L, claimedAt, new IllegalStateException("boom")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease changed");
        verify(repository, never()).markFailed(any(), any(), any());
    }

    @Test
    void publishedNotificationTargetsCreatedPost() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        ScheduledPost scheduledPost = scheduledPost();
        when(repository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                7L, ScheduledPost.STATUS_PUBLISHING, claimedAt)).thenReturn(Optional.of(scheduledPost));
        when(payloadMapper.toPostCreateRequest(scheduledPost)).thenReturn(new PostCreateRequest());
        when(postCommandService.createScheduledPostWithResponse(any(), any(), any(), any()))
                .thenReturn(PostCreateResponse.builder().postId(91L).build());
        when(repository.markPublished(any(), any(), any(), any())).thenReturn(1);

        worker().publishClaimed(7L, claimedAt);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(eventCaptor.getValue().getSourceId()).isEqualTo(91L);
        assertThat(eventCaptor.getValue().getMessageKey()).isEqualTo("notification.scheduled.published");
    }

    @Test
    void failedNotificationTargetsEditableScheduledPost() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 14, 12, 0);
        ScheduledPost scheduledPost = scheduledPost();
        ReflectionTestUtils.setField(scheduledPost, "scheduledPostId", 7L);
        when(repository.findByScheduledPostIdAndStatusAndProcessingStartedAt(
                7L, ScheduledPost.STATUS_PUBLISHING, claimedAt)).thenReturn(Optional.of(scheduledPost));
        when(repository.markFailed(any(), any(), any())).thenReturn(1);

        worker().markFailed(7L, claimedAt, new IllegalStateException("boom"));

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getSourceType()).isEqualTo(NotificationSourceType.SYSTEM);
        assertThat(eventCaptor.getValue().getSourceId()).isEqualTo(7L);
        assertThat(eventCaptor.getValue().getMessageKey()).isEqualTo("notification.scheduled.failed");
    }

    private ScheduledPostPublishWorker worker() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T12:01:00Z"), ZoneOffset.UTC);
        return new ScheduledPostPublishWorker(
                repository, postCommandService, payloadMapper, eventPublisher, clock, scheduledPostFileService);
    }

    private ScheduledPost scheduledPost() {
        User user = User.builder().email("scheduled@example.com").displayName("scheduled-user").build();
        Board board = Board.builder().boardName("Scheduled Board").boardUrl("scheduled-board").build();
        return ScheduledPost.builder()
                .user(user)
                .board(board)
                .title("Scheduled title")
                .contents("Scheduled contents")
                .scheduledAt(LocalDateTime.of(2026, 7, 14, 12, 0))
                .build();
    }

    private void assertRequiresNew(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = ScheduledPostPublishWorker.class.getMethod(methodName, parameterTypes);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
