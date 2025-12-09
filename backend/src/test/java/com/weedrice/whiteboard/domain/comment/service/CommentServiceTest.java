package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentLikeRepository commentLikeRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CommentVersionRepository commentVersionRepository;
    @Mock
    private CommentClosureRepository commentClosureRepository;
    @Mock
    private PointService pointService;

    @InjectMocks
    private CommentService commentService;

    private User user;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testuser").displayName("Test User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        post = Post.builder().title("Test Post").contents("Test Contents").user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "commentCount", 0);


        comment = Comment.builder().content("Test Comment").user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 1L);
        ReflectionTestUtils.setField(comment, "likeCount", 0);
    }

    @Test
    @DisplayName("댓글 생성 성공")
    void createComment_success() {
        // given
        Long userId = 1L;
        Long postId = 1L;
        String content = "New Comment";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);
        doNothing().when(pointService).addPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());

        // when
        Comment createdComment = commentService.createComment(userId, postId, null, content);

        // then
        assertThat(createdComment.getContent()).isEqualTo("Test Comment");
        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
        verify(commentVersionRepository).save(any());
        verify(commentClosureRepository).createSelfClosure(any());
    }

    @Test
    @DisplayName("댓글 수정 성공")
    void updateComment_success() {
        // given
        Long userId = 1L;
        Long commentId = 1L;
        String updatedContent = "Updated Comment";
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        Comment updatedComment = commentService.updateComment(userId, commentId, updatedContent);

        // then
        assertThat(updatedComment.getContent()).isEqualTo(updatedContent);
        verify(commentVersionRepository).save(any());
    }

    @Test
    @DisplayName("댓글 좋아요 성공")
    void likeComment_success() {
        // given
        Long userId = 1L;
        Long commentId = 1L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsById(any(CommentLikeId.class))).thenReturn(false);

        // when
        commentService.likeComment(userId, commentId);

        // then
        verify(commentLikeRepository).save(any());
        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("댓글 좋아요 취소 성공")
    void unlikeComment_success() {
        // given
        Long userId = 1L;
        Long commentId = 1L;
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsById(any(CommentLikeId.class))).thenReturn(true);

        // when
        commentService.unlikeComment(userId, commentId);

        // then
        verify(commentLikeRepository).deleteById(any());
    }
}
