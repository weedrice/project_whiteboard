package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @InjectMocks
    private CommentService commentService;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentLikeRepository commentLikeRepository;
    @Mock
    private CommentVersionRepository commentVersionRepository;
    @Mock
    private CommentClosureRepository commentClosureRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PointService pointService;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private GlobalConfigService globalConfigService;

    @Test
    @DisplayName("댓글 생성 성공 - 루트 댓글")
    void createComment_root_success() {
        // given
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Post post = Post.builder().user(user).build(); // Post 작성자에게 알림
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> {
            Comment c = i.getArgument(0);
            ReflectionTestUtils.setField(c, "commentId", 10L);
            return c;
        });
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        // when
        Comment result = commentService.createComment(1L, 1L, null, "content");

        // then
        assertThat(result).isNotNull();
        assertThat(result.getDepth()).isZero();
        verify(commentClosureRepository).createSelfClosure(10L);
        verify(pointService).addPoint(eq(1L), eq(10), anyString(), eq(10L), eq("COMMENT"));
    }

    @Test
    @DisplayName("댓글 생성 성공 - 대댓글")
    void createComment_child_success() {
        // given
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Post post = Post.builder().build();
        Comment parent = Comment.builder().depth(0).user(user).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(i -> {
            Comment c = i.getArgument(0);
            ReflectionTestUtils.setField(c, "commentId", 10L);
            return c;
        });

        // when
        Comment result = commentService.createComment(1L, 1L, 5L, "content");

        // then
        assertThat(result.getDepth()).isEqualTo(1);
        verify(commentClosureRepository).createClosures(10L, 5L);
    }

    @Test
    @DisplayName("댓글 조회 - 차단된 사용자 마스킹")
    void getComments_masked() {
        // given
        User blocker = User.builder().build(); // 조회자
        ReflectionTestUtils.setField(blocker, "userId", 1L);
        User blockedUser = User.builder().displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedUser, "userId", 2L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).title("Title").build();
        ReflectionTestUtils.setField(post, "postId", 100L);
        ReflectionTestUtils.setField(post, "board", board);

        Comment comment = Comment.builder().user(blockedUser).post(post).content("Bad Content").depth(0).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", java.time.LocalDateTime.now());

        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(2L));
        when(commentRepository.findParentsWithChildrenOrNotDeleted(anyLong(), any())).thenReturn(new PageImpl<>(List.of(comment)));
        when(commentRepository.findAllDescendants(anyList())).thenReturn(List.of(comment));

        // when
        Page<CommentResponse> result = commentService.getComments(100L, 1L, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getContent()).isEqualTo("차단된 사용자의 댓글입니다.");
        assertThat(response.getAuthor().getDisplayName()).isEqualTo("차단된 사용자");
    }

    @Test
    @DisplayName("댓글 좋아요 성공")
    void likeComment_success() {
        // given
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Comment comment = Comment.builder().user(user).build(); // 작성자에게 알림
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsById(any())).thenReturn(false);

        // when
        commentService.likeComment(1L, 10L);

        // then
        verify(commentLikeRepository).save(any());
        assertThat(comment.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글 수정 권한 없음")
    void updateComment_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        User other = User.builder().build();
        ReflectionTestUtils.setField(other, "userId", 2L);

        Comment comment = Comment.builder().user(other).content("Old").build();
        
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "New"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }
    
    @Test
    @DisplayName("댓글 삭제 성공")
    void deleteComment_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        
        Post post = Post.builder().build();
        post.incrementCommentCount(); // 1

        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        commentService.deleteComment(1L, 10L);

        assertThat(comment.getIsDeleted()).isTrue();
        // post comment count should decrease
        // But post object is mock/builder, verify method call if possible or state
        // Post logic is inside entity, so state check is valid
        // post.commentCount starts at 0 -> increment -> 1. delete -> decrement -> 0.
        // wait, builder default is 0. increment -> 1.
        // decrement logic in entity: this.commentCount--.
    }
}