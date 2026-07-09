package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.notification.repository.UserKeywordSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KeywordNotificationEventListenerTest {

    @Test
    void handlePostPublished_runsAsyncAfterCommitInNewTransaction() throws NoSuchMethodException {
        Method method = KeywordNotificationEventListener.class.getMethod(
                "handlePostPublished",
                com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent.class);

        Async async = method.getAnnotation(Async.class);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("taskExecutor");
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void handlePostPublished_skipsBlockedKeywordSubscriber() {
        PostRepository postRepository = mock(PostRepository.class);
        UserKeywordSubscriptionRepository keywordSubscriptionRepository = mock(UserKeywordSubscriptionRepository.class);
        NotificationCommandService notificationCommandService = mock(NotificationCommandService.class);
        UserBlockService userBlockService = mock(UserBlockService.class);
        KeywordNotificationEventListener listener = new KeywordNotificationEventListener(
                postRepository,
                keywordSubscriptionRepository,
                notificationCommandService,
                userBlockService);
        User author = user(1L);
        User subscriber = user(2L);
        Post post = post(author);
        UserKeywordSubscription subscription = UserKeywordSubscription.builder()
                .user(subscriber)
                .keyword("launch")
                .build();

        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(keywordSubscriptionRepository.findMatchingTitle("launch news")).thenReturn(List.of(subscription));
        when(userBlockService.isEitherDirectionBlocked(2L, 1L)).thenReturn(true);

        listener.handlePostPublished(new PostPublishedEvent(100L, 10L));

        verify(notificationCommandService, never()).handleNotificationEvent(any());
    }

    private static User user(Long userId) {
        User user = User.builder()
                .loginId("user" + userId)
                .email("user" + userId + "@example.com")
                .displayName("User " + userId)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private static Post post(User author) {
        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .isPublic(true)
                .build();
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("launch news")
                .contents("body")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);
        return post;
    }
}
