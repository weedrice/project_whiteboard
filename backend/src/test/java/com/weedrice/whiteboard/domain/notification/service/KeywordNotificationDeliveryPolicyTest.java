package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class KeywordNotificationDeliveryPolicyTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final ContentAuthorVisibilityRepository authors = mock(ContentAuthorVisibilityRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final KeywordNotificationDeliveryPolicy policy = new KeywordNotificationDeliveryPolicy(posts, authors, blocks);
    private final User author = user(1L);
    private final User receiver = user(2L);
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 5, 12, 0);

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "DELETED", "BLINDED", "SECRET", "PRIVATE_BOARD", "INACTIVE_BOARD", "UNLISTED_BOARD", "HIDDEN_AUTHOR", "BLOCKED", "AUTHOR_CHANGED"})
    void queuedKeywordIsCompletedWithoutCreatingNotificationWhenSourceBecameHidden(String state) {
        NotificationDeliveryJob job = job(NotificationType.KEYWORD);
        Board board = Board.builder().boardName("Public").boardUrl("public").creator(author).build();
        Post post = Post.builder().title("Keyword title").contents("Body").user(author).board(board).build();
        switch (state) {
            case "DELETED" -> ReflectionTestUtils.setField(post, "isDeleted", true);
            case "BLINDED" -> ReflectionTestUtils.setField(post, "isBlinded", true);
            case "SECRET" -> ReflectionTestUtils.setField(post, "isSecret", true);
            case "PRIVATE_BOARD" -> ReflectionTestUtils.setField(board, "isPublic", false);
            case "INACTIVE_BOARD" -> ReflectionTestUtils.setField(board, "isActive", false);
            case "UNLISTED_BOARD" -> ReflectionTestUtils.setField(board, "isListed", false);
            case "AUTHOR_CHANGED" -> ReflectionTestUtils.setField(post, "userId", 3L);
        }
        when(posts.findByIdWithRelations(10L)).thenReturn(state.equals("MISSING") ? Optional.empty() : Optional.of(post));
        when(authors.findVisibleUserIds(List.of(1L))).thenReturn(state.equals("HIDDEN_AUTHOR") ? Set.of() : Set.of(1L));
        when(blocks.existsEitherDirection(2L, 1L)).thenReturn(state.equals("BLOCKED"));
        NotificationDeliveryJobRepository jobs = mock(NotificationDeliveryJobRepository.class);
        NotificationCommandService commands = mock(NotificationCommandService.class);
        NotificationDeliveryPublisher publisher = mock(NotificationDeliveryPublisher.class);
        MessageChangeDeliveryPublisher messages = mock(MessageChangeDeliveryPublisher.class);
        EntityManager em = mock(EntityManager.class);
        NotificationDeliveryJobTransaction transaction = new NotificationDeliveryJobTransaction(
                jobs, commands, publisher, messages, em, new NotificationPayloadValidator(), Clock.systemUTC(), new NotificationSourceDeliveryPolicy(policy, mock(MentionNotificationDeliveryPolicy.class)));
        job.claim(now);
        when(jobs.findByIdForUpdate(9L)).thenReturn(Optional.of(job));

        assertThat(transaction.deliver(9L, now)).isEqualTo(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        assertThat(job.getStatus()).isEqualTo(NotificationDeliveryJob.Status.COMPLETED);
        assertThat(job.getRetryCount()).isZero();
        verifyNoInteractions(commands, publisher, messages, em);
        assertThat(transaction.deliver(9L, now)).isEqualTo(DeliveryJobTransitionResult.LEASE_LOST);
    }

    @Test
    void visibleKeywordStillDeliversAndUsesCurrentBidirectionalBlockRelation() {
        Board board = Board.builder().boardName("Public").boardUrl("public").creator(author).build();
        Post post = Post.builder().title("Keyword title").contents("Body").user(author).board(board).build();
        when(posts.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
        when(authors.findVisibleUserIds(List.of(1L))).thenReturn(Set.of(1L));
        assertThat(policy.canDeliverKeyword(NotificationSourceType.POST, 10L, 1L, 2L)).isTrue();
        verify(blocks).existsEitherDirection(2L, 1L);
    }

    @Test
    void otherNotificationTypesKeepExistingDeliveryContract() {
        assertThat(new NotificationSourceDeliveryPolicy(policy, mock(MentionNotificationDeliveryPolicy.class))
                .canDeliver(job(NotificationType.MESSAGE))).isTrue();
        verifyNoInteractions(posts, authors, blocks);
    }

    private NotificationDeliveryJob job(NotificationType type) {
        return NotificationDeliveryJob.from(NotificationEvent.localized(UUID.randomUUID(), receiver, author, null,
                type, NotificationSourceType.POST, 10L, "notification.keyword.matched", "Keyword title"), now);
    }

    private User user(Long id) {
        User result = User.builder().displayName("User " + id).build();
        ReflectionTestUtils.setField(result, "userId", id);
        return result;
    }
}
