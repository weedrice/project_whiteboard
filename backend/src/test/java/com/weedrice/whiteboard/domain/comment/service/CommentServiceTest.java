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
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
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
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private GlobalConfigService globalConfigService;

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

    @Test
    @DisplayName("댓글 목록 조회 성공")
    void getComments_success() {
        // given
        Long postId = 1L;
        Long currentUserId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Comment> commentPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.singletonList(comment), pageable, 1);

        when(userBlockService.getBlockedUserIds(currentUserId)).thenReturn(java.util.Collections.emptyList());
        when(commentRepository.findParentsWithChildrenOrNotDeleted(postId, pageable)).thenReturn(commentPage);
        when(commentRepository.findAllDescendants(any())).thenReturn(java.util.Collections.emptyList());

        // when
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.CommentResponse> response = commentService
                .getComments(postId, currentUserId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(commentRepository).findParentsWithChildrenOrNotDeleted(postId, pageable);
    }

    @Test
    @DisplayName("댓글 조회 성공")
    void getComment_success() {
        // given
        Long commentId = 1L;
        when(commentRepository.findByIdWithRelations(commentId)).thenReturn(Optional.of(comment));

        // when
        com.weedrice.whiteboard.domain.comment.dto.CommentResponse response = commentService.getComment(commentId);

        // then
        assertThat(response).isNotNull();
        verify(commentRepository).findByIdWithRelations(commentId);
    }

    @Test
    @DisplayName("답글 목록 조회 성공")
    void getReplies_success() {
        // given
        Long parentId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Comment> repliesPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.singletonList(comment), pageable, 1);

        when(commentRepository.findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(parentId, false, pageable))
                .thenReturn(repliesPage);

        // when
        com.weedrice.whiteboard.domain.comment.dto.CommentListResponse response = commentService.getReplies(parentId,
                pageable);

        // then
        assertThat(response).isNotNull();
        verify(commentRepository).findByParent_CommentIdAndIsDeletedOrderByCreatedAtAsc(parentId, false, pageable);
    }

    @Test
    @DisplayName("내 댓글 목록 조회 성공")
    void getMyComments_success() {
        // given
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Comment> commentPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.singletonList(comment), pageable, 1);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(commentRepository.findByUserAndIsDeletedOrderByCreatedAtDesc(user, false, pageable))
                .thenReturn(commentPage);

        // when
        org.springframework.data.domain.Page<com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse> response = commentService
                .getMyComments(userId, pageable);

        // then
        assertThat(response).isNotNull();
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("댓글 삭제 성공")
    void deleteComment_success() {
        // given
        Long userId = 1L;
        Long commentId = 1L;
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // when
        commentService.deleteComment(userId, commentId);

        // then
        verify(commentRepository).findById(commentId);
    }
}
