package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NotificationPushDeliveryPolicyTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final ContentAuthorVisibilityRepository authors = mock(ContentAuthorVisibilityRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final PushPayloadFactory payloads = new PushPayloadFactory(new ObjectMapper());
    private final NotificationPushDeliveryPolicy policy = new NotificationPushDeliveryPolicy(payloads, notifications,
            new NotificationSourceDeliveryPolicy(new KeywordNotificationDeliveryPolicy(posts, authors, blocks),
                    mock(MentionNotificationDeliveryPolicy.class)));
    private final User author = user(1L);
    private final User receiver = user(2L);

    static Stream<Arguments> hiddenSources() {
        return Stream.of("MISSING", "DELETED", "SECRET", "BLINDED", "PRIVATE_BOARD", "INACTIVE_BOARD",
                "UNLISTED_BOARD", "MOVED_TO_PRIVATE_BOARD", "HIDDEN_AUTHOR", "BLOCKED")
                .flatMap(state -> Stream.of(Arguments.of(state, false), Arguments.of(state, true)));
    }

    @ParameterizedTest
    @MethodSource("hiddenSources")
    void rechecksCurrentSourceAfterPushWasQueued(String state, boolean legacy) {
        Board board = Board.builder().boardName("Public").boardUrl("public").creator(author).build();
        Post post = Post.builder().title("Old title").contents("Body").user(author).board(board).build();
        when(posts.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
        when(authors.findVisibleUserIds(List.of(1L))).thenReturn(Set.of(1L));
        if (legacy) {
            when(notifications.findById(7L)).thenReturn(Optional.of(notification(NotificationType.KEYWORD)));
        }
        String payload = legacy ? legacyPayload() : keywordPayload();
        assertThat(policy.canDeliver(2L, payload)).isTrue();
        switch (state) {
            case "MISSING" -> when(posts.findByIdWithRelations(10L)).thenReturn(Optional.empty());
            case "DELETED" -> ReflectionTestUtils.setField(post, "isDeleted", true);
            case "SECRET" -> ReflectionTestUtils.setField(post, "isSecret", true);
            case "BLINDED" -> ReflectionTestUtils.setField(post, "isBlinded", true);
            case "PRIVATE_BOARD" -> ReflectionTestUtils.setField(board, "isPublic", false);
            case "INACTIVE_BOARD" -> ReflectionTestUtils.setField(board, "isActive", false);
            case "UNLISTED_BOARD" -> ReflectionTestUtils.setField(board, "isListed", false);
            case "MOVED_TO_PRIVATE_BOARD" -> ReflectionTestUtils.setField(post, "board",
                    Board.builder().boardName("Private").boardUrl("private").creator(author).isPublic(false).build());
            case "HIDDEN_AUTHOR" -> when(authors.findVisibleUserIds(List.of(1L))).thenReturn(Set.of());
            case "BLOCKED" -> when(blocks.existsEitherDirection(2L, 1L)).thenReturn(true);
        }
        assertThat(policy.canDeliver(2L, payload)).isFalse();
    }

    @Test
    void currentMetadataDoesNotDependOnArchivedNotificationOrParseTargetUrl() {
        Board board = Board.builder().boardName("Public").boardUrl("public").creator(author).build();
        when(posts.findByIdWithRelations(10L)).thenReturn(Optional.of(
                Post.builder().title("Title").contents("Body").user(author).board(board).build()));
        when(authors.findVisibleUserIds(List.of(1L))).thenReturn(Set.of(1L));
        assertThat(policy.canDeliver(2L, keywordPayload())).isTrue();
        verify(posts).findByIdWithRelations(10L);
        verifyNoInteractions(notifications);
    }

    @Test
    void missingLegacyNotificationIsAStaleSnapshot() {
        assertThat(policy.canDeliver(2L, legacyPayload())).isFalse();
        verifyNoInteractions(posts, authors, blocks);
    }

    @Test
    void legacyNotificationMustBelongToThePushReceiver() {
        when(notifications.findById(7L)).thenReturn(Optional.of(notification(NotificationType.KEYWORD)));
        assertThat(policy.canDeliver(99L, legacyPayload())).isFalse();
        verifyNoInteractions(posts, authors, blocks);
    }

    @Test
    void legacyNonKeywordKeepsItsExistingSourceHistoryContract() {
        when(notifications.findById(7L)).thenReturn(Optional.of(notification(NotificationType.COMMENT)));
        assertThat(policy.canDeliver(2L, legacyPayload())).isTrue();
        verifyNoInteractions(posts, authors, blocks);
    }

    @Test
    void currentNonKeywordDoesNotRequireSourceOrArchivedNotification() {
        String payload = payloads.create(new PushDispatchCommand(2L, null, 7L, "Content", "system", null, "SYSTEM", null));
        assertThat(policy.canDeliver(2L, payload)).isTrue();
        verifyNoInteractions(notifications, posts, authors, blocks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "0", "1.5", "9223372036854775808", "\"10\""})
    void malformedKeywordSourceIdCannotFallBackToUrl(String sourceIdJson) {
        String payload = "{\"notificationType\":\"keyword\",\"sourceType\":\"POST\",\"actorUserId\":1,\"sourceId\":"
                + sourceIdJson + ",\"url\":\"/board/public/post/10\"}";
        assertThat(policy.canDeliver(2L, payload)).isFalse();
        verifyNoInteractions(posts, notifications);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "not-json", "{\"tag\":\"notification-9223372036854775808\"}",
            "{\"tag\":\"notification--1\"}", "{\"notificationType\":\"keyword\",\"sourceType\":\"COMMENT\",\"sourceId\":10}"})
    void unresolvableSnapshotsAreSkipped(String payload) {
        assertThat(policy.canDeliver(2L, payload)).isFalse();
        verifyNoInteractions(posts, notifications);
    }

    private String keywordPayload() {
        // The URL deliberately points elsewhere: only immutable source fields identify the post.
        return payloads.create(new PushDispatchCommand(2L, 1L, 7L, "Old title", "keyword",
                "/board/other/post/999", "POST", 10L));
    }

    private String legacyPayload() {
        return "{\"title\":\"NoviIs\",\"body\":\"Old title\",\"url\":\"/board/other/post/999\","
                + "\"tag\":\"notification-7\",\"actorUserId\":1}";
    }

    private Notification notification(NotificationType type) {
        return Notification.builder().user(receiver).actor(author).notificationType(type)
                .sourceType("POST").sourceId(10L).content("Old title").build();
    }

    private User user(Long id) {
        User user = User.builder().displayName("User " + id).build();
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }
}
