package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.repository.AgentRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private AgentRepository agentRepository;

    @Test
    @DisplayName("create root comment")
    void createComment_root_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().user(user).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(any())).thenReturn("10");

        Comment result = commentService.createComment(1L, 1L, null, "content");

        assertThat(result).isNotNull();
        assertThat(result.getDepth()).isZero();
        verify(commentClosureRepository).createSelfClosure(10L);
        verify(pointService).addPoint(eq(1L), eq(10), anyString(), eq(10L), eq("COMMENT"));
    }

    @Test
    @DisplayName("create child comment")
    void createComment_child_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();

        Comment parent = Comment.builder().depth(0).user(user).post(post).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(any())).thenReturn("10");

        Comment result = commentService.createComment(1L, 1L, 5L, "content");

        assertThat(result.getDepth()).isEqualTo(1);
        verify(commentClosureRepository).createClosures(10L, 5L);
    }

    @Test
    @DisplayName("mask blocked user comments")
    void getComments_masked() {
        User blockedUser = User.builder().displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedUser, "userId", 2L);

        Board board = Board.builder().boardUrl("free").build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(blockedUser).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Comment comment = Comment.builder()
                .user(blockedUser)
                .post(post)
                .content("Bad Content")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());

        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(2L));
        when(commentRepository.findParentsWithChildrenOrNotDeleted(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(commentRepository.findAllDescendants(anyList())).thenReturn(List.of(comment));

        Page<CommentResponse> result = commentService.getComments(100L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getContent()).isNotEqualTo("Bad Content");
        assertThat(response.getAuthor().getDisplayName()).isNotEqualTo("Blocked");
    }

    @Test
    @DisplayName("like comment")
    void likeComment_success() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsById(any())).thenReturn(false);

        commentService.likeComment(1L, 10L);

        verify(commentLikeRepository).save(any());
        assertThat(comment.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("update comment forbidden")
    void updateComment_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        User other = User.builder().build();
        ReflectionTestUtils.setField(other, "userId", 2L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(other).build();
        Comment comment = Comment.builder().user(other).post(post).content("Old").build();

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "New"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("delete comment")
    void deleteComment_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        post.incrementCommentCount();

        Comment comment = Comment.builder().user(user).post(post).content("Content").build();

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig(any())).thenReturn("10");

        commentService.deleteComment(1L, 10L);

        assertThat(comment.getIsDeleted()).isTrue();
        assertThat(post.getCommentCount()).isZero();
    }
}
