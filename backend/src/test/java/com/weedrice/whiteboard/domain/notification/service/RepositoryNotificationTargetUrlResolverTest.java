package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryNotificationTargetUrlResolverTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Test
    @DisplayName("POST와 COMMENT 알림의 내부 이동 경로를 batch로 계산한다")
    void resolveAll_buildsPostAndCommentTargetUrls() {
        RepositoryNotificationTargetUrlResolver resolver =
                new RepositoryNotificationTargetUrlResolver(postRepository, commentRepository);
        Notification postNotification = notification(1L, "POST", 10L);
        Notification commentNotification = notification(2L, "COMMENT", 20L);
        Post post = post(10L, "free");
        Comment comment = comment(20L, post(11L, "notice"));

        when(postRepository.findByPostIdInAndIsDeletedFalse(any())).thenReturn(List.of(post));
        when(commentRepository.findByCommentIdInAndIsDeletedFalse(any())).thenReturn(List.of(comment));

        Map<Long, String> targetUrls = resolver.resolveAll(List.of(postNotification, commentNotification));

        assertThat(targetUrls)
                .containsEntry(1L, "/board/free/post/10")
                .containsEntry(2L, "/board/notice/post/11#comment-20");
        verify(postRepository).findByPostIdInAndIsDeletedFalse(any());
        verify(commentRepository).findByCommentIdInAndIsDeletedFalse(any());
    }

    private Notification notification(Long notificationId, String sourceType, Long sourceId) {
        Notification notification = Notification.builder()
                .user(User.builder().build())
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
