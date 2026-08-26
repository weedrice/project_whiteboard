package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.inquiry.entity.Inquiry;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.repository.InquiryRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.post.service.PostReadAccessService;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryNotificationTargetUrlResolverTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostReadAccessService postReadAccessService;

    @Mock
    private ScheduledPostRepository scheduledPostRepository;

    @Mock
    private InquiryRepository inquiryRepository;

    @Test
    @DisplayName("POST와 COMMENT 알림의 내부 이동 경로를 batch로 계산한다")
    void resolveAll_buildsPostAndCommentTargetUrls() {
        RepositoryNotificationTargetUrlResolver resolver = resolver();
        Notification postNotification = notification(1L, "POST", 10L);
        Notification commentNotification = notification(2L, "COMMENT", 20L);
        Post post = post(10L, "free");
        Post commentPost = post(11L, "notice");
        Comment comment = comment(20L, commentPost);

        when(postRepository.findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(any()))
                .thenReturn(List.of(post, commentPost));
        when(commentRepository.findByCommentIdInAndIsDeletedFalse(any())).thenReturn(List.of(comment));
        when(postReadAccessService.findReadablePostIds(any(), any())).thenReturn(Set.of(10L, 11L));

        Map<Long, String> targetUrls = resolver.resolveAll(List.of(postNotification, commentNotification));

        assertThat(targetUrls)
                .containsEntry(1L, "/board/free/post/10")
                .containsEntry(2L, "/board/notice/post/11#comment-20");
        verify(postRepository).findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(any());
        verify(commentRepository).findByCommentIdInAndIsDeletedFalse(any());
    }

    @Test
    @DisplayName("수신자가 읽을 수 없는 게시글과 댓글에는 이동 경로를 제공하지 않는다")
    void resolveAll_omitsUnreadablePostAndCommentTargetUrls() {
        RepositoryNotificationTargetUrlResolver resolver = resolver();
        Notification postNotification = notification(1L, "POST", 10L);
        Notification commentNotification = notification(2L, "COMMENT", 20L);
        Post post = post(10L, "secret");
        Comment comment = comment(20L, post);

        when(postRepository.findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(any())).thenReturn(List.of(post));
        when(commentRepository.findByCommentIdInAndIsDeletedFalse(any())).thenReturn(List.of(comment));
        when(postReadAccessService.findReadablePostIds(any(), any())).thenReturn(Set.of());

        Map<Long, String> targetUrls = resolver.resolveAll(List.of(postNotification, commentNotification));

        assertThat(targetUrls).isEmpty();
    }

    @Test
    @DisplayName("MESSAGE 알림은 쪽지함 경로로 연결한다")
    void resolveAll_buildsMessageTargetUrl() {
        Notification messageNotification = notification(3L, "MESSAGE", 30L);

        Map<Long, String> targetUrls = resolver().resolveAll(List.of(messageNotification));

        assertThat(targetUrls).containsEntry(3L, "/mypage/messages");
    }

    @Test
    @DisplayName("문의 알림은 작성자와 슈퍼관리자에게 서로 다른 안전한 경로를 제공한다")
    void resolveAll_buildsRoleAwareInquiryTargetUrls() {
        Notification ownerNotification = notification(5L, "INQUIRY", 41L);
        Inquiry inquiry = new Inquiry(99L, InquiryCategory.ACCOUNT, "Account", java.time.LocalDateTime.now());
        ReflectionTestUtils.setField(inquiry, "inquiryId", 41L);
        Notification adminNotification = notification(6L, "INQUIRY", 41L);
        ReflectionTestUtils.setField(adminNotification.getUser(), "userId", 100L);
        adminNotification.getUser().grantSuperAdminRole();
        when(inquiryRepository.findAllById(any())).thenReturn(List.of(inquiry));

        Map<Long, String> targetUrls = resolver().resolveAll(List.of(ownerNotification, adminNotification));

        assertThat(targetUrls)
                .containsEntry(5L, "/inquiries/41")
                .containsEntry(6L, "/admin/inquiries/41");
        verify(inquiryRepository).findAllById(any());
        verify(inquiryRepository, never()).findById(any());
    }

    @Test
    @DisplayName("예약 발행 실패 알림은 소유자의 예약글 수정 경로로 연결한다")
    void resolveAll_buildsFailedScheduledPostTargetUrlForOwner() {
        Notification failedNotification = notification(4L, "SYSTEM", 40L);
        ReflectionTestUtils.setField(failedNotification, "messageKey", "notification.scheduled.failed");
        User owner = failedNotification.getUser();
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(owner)
                .board(Board.builder().boardName("free").boardUrl("free").creator(owner).build())
                .title("scheduled")
                .contents("contents")
                .scheduledAt(java.time.LocalDateTime.of(2026, 7, 20, 12, 0))
                .build();
        ReflectionTestUtils.setField(scheduledPost, "scheduledPostId", 40L);
        ReflectionTestUtils.setField(scheduledPost, "status", ScheduledPost.STATUS_FAILED);
        when(scheduledPostRepository.findByScheduledPostIdIn(Set.of(40L)))
                .thenReturn(List.of(scheduledPost));

        Map<Long, String> targetUrls = resolver().resolveAll(List.of(failedNotification));

        assertThat(targetUrls).containsEntry(4L, "/scheduled-posts/40/edit");
    }

    private RepositoryNotificationTargetUrlResolver resolver() {
        return new RepositoryNotificationTargetUrlResolver(
                postRepository,
                commentRepository,
                postReadAccessService,
                scheduledPostRepository,
                inquiryRepository);
    }

    private Notification notification(Long notificationId, String sourceType, Long sourceId) {
        User receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 99L);
        Notification notification = Notification.builder()
                .user(receiver)
                .notificationType(NotificationType.LIKE)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .content("content")
                .build();
        ReflectionTestUtils.setField(notification, "notificationId", notificationId);
        return notification;
    }

    private Post post(Long postId, String boardUrl) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(User.builder().build())
                .build();
        Post post = Post.builder()
                .board(board)
                .user(User.builder().build())
                .title("title")
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }

    private Comment comment(Long commentId, Post post) {
        Comment comment = Comment.builder()
                .post(post)
                .user(User.builder().build())
                .depth(0)
                .content("content")
                .build();
        ReflectionTestUtils.setField(comment, "commentId", commentId);
        return comment;
    }
}
