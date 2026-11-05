package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MentionNotificationDeliveryPolicyTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final ContentAuthorVisibilityRepository authors = mock(ContentAuthorVisibilityRepository.class);
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final InquiryLegacyWritePolicy inquiry = mock(InquiryLegacyWritePolicy.class);
    private final MentionNotificationReadPolicy reads = new MentionNotificationReadPolicy(posts, comments, blocks, admins,
            new PostAccessPolicy(new BoardAccessPolicy(admins), inquiry), authors);
    private final NotificationSourceDeliveryPolicy delivery = new NotificationSourceDeliveryPolicy(
            mock(KeywordNotificationDeliveryPolicy.class), new MentionNotificationDeliveryPolicy(reads, users));
    private final PushPayloadFactory payloads = new PushPayloadFactory(new ObjectMapper());
    private final NotificationPushDeliveryPolicy pushes = new NotificationPushDeliveryPolicy(payloads, notifications, delivery);
    private final User actor = user(1L);
    private final User receiver = user(2L);
    private final User parentAuthor = user(3L);
    private Board board;
    private Post post;
    private Comment comment;

    private void source(NotificationSourceType type) {
        board = Board.builder().boardName("Board").boardUrl("board").creator(parentAuthor).build();
        ReflectionTestUtils.setField(board, "boardId", 100L);
        post = Post.builder().title("Title").contents("Body").board(board)
                .user(type == NotificationSourceType.POST ? actor : parentAuthor).build();
        ReflectionTestUtils.setField(post, "postId", 10L);
        comment = Comment.builder().user(actor).post(post).content("Mention").depth(0).build();
        when(posts.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
        when(comments.findNonDeletedByIdWithRelations(20L)).thenReturn(Optional.of(comment));
        when(users.findAllById(List.of(2L))).thenReturn(List.of(receiver));
        when(authors.findVisibleUserIds(List.of(1L))).thenReturn(Set.of(1L));
    }

    @ParameterizedTest
    @EnumSource(value = NotificationSourceType.class, names = {"POST", "COMMENT"})
    void secretAndPrivateTransitionsRecheckCurrentReceiverPermission(NotificationSourceType type) {
        source(type);
        assertAllStages(type, true);
        ReflectionTestUtils.setField(post, "isSecret", true);
        assertAllStages(type, false);
        when(admins.findActiveUserIdsByBoardIdAndUserIds(100L, List.of(2L))).thenReturn(List.of(2L));
        assertAllStages(type, true);
        ReflectionTestUtils.setField(board, "isPublic", false);
        assertAllStages(type, true);
        when(admins.findActiveUserIdsByBoardIdAndUserIds(100L, List.of(2L))).thenReturn(List.of());
        assertAllStages(type, false);
        ReflectionTestUtils.setField(receiver, "isSuperAdmin", true);
        assertAllStages(type, true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING_COMMENT", "DELETED_COMMENT", "BLINDED_COMMENT", "MISSING_POST", "DELETED_POST",
            "BLINDED_POST", "BLOCKED_ACTOR", "BLOCKED_PARENT", "HIDDEN_ACTOR", "INACTIVE_RECEIVER", "DELETED_RECEIVER", "WRONG_ACTOR"})
    void unavailableCommentMentionNeverReachesAnyDeliveryStage(String state) {
        source(NotificationSourceType.COMMENT);
        switch (state) {
            case "MISSING_COMMENT" -> when(comments.findNonDeletedByIdWithRelations(20L)).thenReturn(Optional.empty());
            case "DELETED_COMMENT" -> ReflectionTestUtils.setField(comment, "isDeleted", true);
            case "BLINDED_COMMENT" -> ReflectionTestUtils.setField(comment, "isBlinded", true);
            case "MISSING_POST" -> when(posts.findByIdWithRelations(10L)).thenReturn(Optional.empty());
            case "DELETED_POST" -> ReflectionTestUtils.setField(post, "isDeleted", true);
            case "BLINDED_POST" -> ReflectionTestUtils.setField(post, "isBlinded", true);
            case "BLOCKED_ACTOR" -> when(blocks.findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L))).thenReturn(List.of(2L));
            case "BLOCKED_PARENT" -> when(blocks.findBlockedCandidateUserIdsEitherDirection(3L, List.of(2L))).thenReturn(List.of(2L));
            case "HIDDEN_ACTOR" -> when(authors.findVisibleUserIds(List.of(1L))).thenReturn(Set.of());
            case "INACTIVE_RECEIVER" -> ReflectionTestUtils.setField(receiver, "status", User.STATUS_SUSPENDED);
            case "DELETED_RECEIVER" -> ReflectionTestUtils.setField(receiver, "deletedAt", LocalDateTime.now());
            case "WRONG_ACTOR" -> ReflectionTestUtils.setField(comment, "userId", 3L);
        }
        assertAllStages(NotificationSourceType.COMMENT, false);
    }

    @Test
    void ownSecretAndInactivePublicParentRemainReadableButOrdinaryPrivateParentDoesNot() {
        source(NotificationSourceType.COMMENT);
        ReflectionTestUtils.setField(post, "userId", 2L);
        ReflectionTestUtils.setField(post, "isSecret", true);
        ReflectionTestUtils.setField(board, "isActive", false);
        assertAllStages(NotificationSourceType.COMMENT, true);
        ReflectionTestUtils.setField(board, "isPublic", false);
        assertAllStages(NotificationSourceType.COMMENT, false);
    }

    @Test
    void parentAuthorBanAndUnlistedBoardDoNotDenyDirectMentionAccess() {
        source(NotificationSourceType.COMMENT);
        ReflectionTestUtils.setField(board, "isListed", false);
        ReflectionTestUtils.setField(parentAuthor, "status", User.STATUS_SUSPENDED);
        assertAllStages(NotificationSourceType.COMMENT, true);
        verify(authors, never()).findVisibleUserIds(List.of(3L));
    }

    @Test
    void changedSourcePostAuthorInvalidatesOriginalMentionActor() {
        source(NotificationSourceType.POST);
        ReflectionTestUtils.setField(post, "userId", 3L);
        assertAllStages(NotificationSourceType.POST, false);
    }

    @Test
    void unreadableMentionCompletesJobWithoutCreatingOrPublishingNotification() {
        source(NotificationSourceType.POST);
        ReflectionTestUtils.setField(post, "isSecret", true);
        NotificationDeliveryJob job = job(NotificationSourceType.POST);
        LocalDateTime claimedAt = LocalDateTime.now();
        job.claim(claimedAt);
        NotificationDeliveryJobRepository jobs = mock(NotificationDeliveryJobRepository.class);
        NotificationCommandService commands = mock(NotificationCommandService.class);
        NotificationDeliveryPublisher publisher = mock(NotificationDeliveryPublisher.class);
        MessageChangeDeliveryPublisher messages = mock(MessageChangeDeliveryPublisher.class);
        EntityManager em = mock(EntityManager.class);
        when(jobs.findByIdForUpdate(9L)).thenReturn(Optional.of(job));
        NotificationDeliveryJobTransaction transaction = new NotificationDeliveryJobTransaction(jobs, commands,
                publisher, messages, em, new NotificationPayloadValidator(), Clock.systemUTC(), delivery);
        assertThat(transaction.deliver(9L, claimedAt)).isEqualTo(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        assertThat(job.getStatus()).isEqualTo(NotificationDeliveryJob.Status.COMPLETED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(transaction.deliver(9L, claimedAt)).isEqualTo(DeliveryJobTransitionResult.LEASE_LOST);
        verifyNoInteractions(commands, publisher, messages, em);
    }

    private void assertAllStages(NotificationSourceType type, boolean expected) {
        Long id = sourceId(type);
        Post source = reads.resolveSourcePost(type, id, 1L);
        assertThat(source != null && !reads.readableRecipients(source, 1L, List.of(receiver)).isEmpty()).isEqualTo(expected);
        assertThat(delivery.canDeliver(job(type))).isEqualTo(expected);
        String currentPayload = payloads.create(new PushDispatchCommand(2L, 1L, 7L, "Mention", "mention",
                "/mypage/notifications", type.name(), id));
        assertThat(pushes.canDeliver(2L, currentPayload)).isEqualTo(expected);
        when(notifications.findById(7L)).thenReturn(Optional.of(Notification.builder().user(receiver).actor(actor)
                .notificationType(NotificationType.MENTION).sourceType(type.name()).sourceId(id).content("Mention").build()));
        assertThat(pushes.canDeliver(2L, "{\"tag\":\"notification-7\",\"actorUserId\":1}")).isEqualTo(expected);
    }

    private NotificationDeliveryJob job(NotificationSourceType type) {
        return NotificationDeliveryJob.from(NotificationEvent.localized(UUID.randomUUID(), receiver, actor, null,
                NotificationType.MENTION, type, sourceId(type), "notification.mention.created", "Actor"), LocalDateTime.now());
    }

    private Long sourceId(NotificationSourceType type) {
        return type == NotificationSourceType.POST ? 10L : 20L;
    }

    private User user(Long id) {
        User result = User.builder().displayName("User " + id).build();
        ReflectionTestUtils.setField(result, "userId", id);
        return result;
    }
}
