package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private CommentService commentService;
    private BoardAccessPolicy boardAccessPolicy;
    private PostAccessPolicy postAccessPolicy;

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
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private GlobalConfigService globalConfigService;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private AgentOwnershipService agentOwnershipService;
    @Mock
    private SanctionService sanctionService;

    @BeforeEach
    void setUp() {
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        postAccessPolicy = new PostAccessPolicy(boardAccessPolicy);
        commentService = new CommentService(
                commentRepository,
                postRepository,
                userRepository,
                commentLikeRepository,
                commentVersionRepository,
                commentClosureRepository,
                eventPublisher,
                pointService,
                pointHistoryRepository,
                userBlockService,
                globalConfigService,
                agentOwnershipService,
                sanctionService,
                postAccessPolicy);
    }

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
        when(postRepository.incrementCommentCount(1L)).thenReturn(1);
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
        verify(postRepository).incrementCommentCount(1L);
        verify(pointService).addPoint(eq(1L), eq(10), anyString(), eq(10L), eq("COMMENT"));
    }

    @Test
    @DisplayName("활성 BAN 사용자는 댓글을 작성할 수 없다")
    void createComment_bannedUser_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, null, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("create child comment")
    void createComment_child_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment parent = Comment.builder().depth(0).user(user).post(post).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementCommentCount(1L)).thenReturn(1);
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(any())).thenReturn("10");

        Comment result = commentService.createComment(1L, 1L, 5L, "content");

        assertThat(result.getDepth()).isEqualTo(1);
        verify(postRepository).incrementCommentCount(1L);
        verify(commentClosureRepository).createClosures(10L, 5L);
    }

    @Test
    @DisplayName("reject parent comment from another post")
    void createComment_parentFromAnotherPost_rejected() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Post otherPost = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(otherPost, "postId", 2L);

        Comment parent = Comment.builder().depth(0).user(user).post(otherPost).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, 5L, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("foreign agent로 댓글 작성 시 거부한다")
    void createCommentAsAgent_foreignAgent_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        User otherUser = User.builder().build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);

        Agent foreignAgent = Agent.builder()
                .user(otherUser)
                .agentTokenHash("hash")
                .name("foreign-agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(foreignAgent, "agentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentOwnershipService.resolveOwnedActiveAgent(1L, 10L))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> commentService.createCommentAsAgent(1L, 10L, 1L, null, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("agent reply notification uses agent name")
    void createCommentAsAgent_notificationUsesAgentName() {
        User owner = User.builder().displayName("owner").build();
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User actorUser = User.builder().displayName("user-owner").build();
        ReflectionTestUtils.setField(actorUser, "userId", 2L);

        Agent agent = Agent.builder()
                .user(actorUser)
                .agentTokenHash("hash")
                .name("agent-writer")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 99L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(owner).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment parent = Comment.builder().depth(0).user(owner).post(post).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actorUser));
        when(agentOwnershipService.resolveOwnedActiveAgent(2L, 99L)).thenReturn(agent);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(any())).thenReturn("10");

        commentService.createCommentAsAgent(2L, 99L, 1L, 5L, "content");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getActorAgent()).isNotNull();
        assertThat(notificationEvent.getContent()).isEqualTo("agent-writer님이 회원님의 댓글에 답글을 남겼습니다.");
    }

    @Test
    @DisplayName("mask blocked user comments")
    void getComments_masked() {
        User blockedUser = User.builder().displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedUser, "userId", 2L);

        User postOwner = User.builder().displayName("Owner").build();
        ReflectionTestUtils.setField(postOwner, "userId", 3L);

        Board board = Board.builder().boardUrl("free").creator(postOwner).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(postOwner).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Comment comment = Comment.builder()
                .user(blockedUser)
                .post(post)
                .content("Bad Content")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);

        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(2L));
        when(commentRepository.findParentsWithChildrenOrNotDeleted(anyLong(), any()))
                .thenReturn(new PageImpl<>(List.of(comment)));

        Page<CommentResponse> result = commentService.getComments(100L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getContent()).isNotEqualTo("Bad Content");
        assertThat(response.getAuthor().getDisplayName()).isNotEqualTo("Blocked");
    }

    @Test
    @DisplayName("mask blocked user replies")
    void getReplies_masked() {
        User blockedUser = User.builder().displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedUser, "userId", 2L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(viewer).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(viewer).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Comment parent = Comment.builder()
                .user(viewer)
                .post(post)
                .content("Parent")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(parent, "commentId", 9L);
        ReflectionTestUtils.setField(parent, "createdAt", LocalDateTime.now());

        Comment reply = Comment.builder()
                .user(blockedUser)
                .post(post)
                .parent(parent)
                .content("Reply")
                .depth(1)
                .build();
        ReflectionTestUtils.setField(reply, "commentId", 10L);
        ReflectionTestUtils.setField(reply, "createdAt", LocalDateTime.now());

        when(commentRepository.findByIdWithRelations(9L)).thenReturn(Optional.of(parent));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(2L));
        when(commentRepository.findRepliesWithRelations(9L, false, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(reply), PageRequest.of(0, 10), 1));

        var result = commentService.getReplies(9L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("차단된 사용자의 댓글입니다.");
        assertThat(result.getContent().get(0).getAuthor().getDisplayName()).isEqualTo("차단된 사용자");
    }

    @Test
    @DisplayName("get comments returns parent-only rows with reply metadata")
    void getComments_returnsParentOnlyRowsWithReplyMetadata() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 2L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(author).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(author).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Comment parent = Comment.builder()
                .user(author)
                .post(post)
                .content("Parent")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(parent, "commentId", 10L);
        ReflectionTestUtils.setField(parent, "createdAt", LocalDateTime.now());

        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of());
        when(commentRepository.findParentsWithChildrenOrNotDeleted(100L, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(parent), PageRequest.of(0, 10), 1));
        when(commentRepository.countVisibleRepliesByParentIds(List.of(10L)))
                .thenReturn(List.of(replyCountProjection(10L, 3L)));

        Page<CommentResponse> result = commentService.getComments(100L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getCommentId()).isEqualTo(10L);
        assertThat(response.getReplyCount()).isEqualTo(3L);
        assertThat(response.isHasReplies()).isTrue();
        assertThat(response.getChildren()).isEmpty();
        verify(commentRepository, never()).findAllDescendants(anyList());
    }

    @Test
    @DisplayName("get replies returns reply metadata for nested lazy loading")
    void getReplies_returnsReplyMetadata() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 2L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(author).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(author).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Comment parent = Comment.builder()
                .user(author)
                .post(post)
                .content("Parent")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(parent, "commentId", 9L);
        ReflectionTestUtils.setField(parent, "createdAt", LocalDateTime.now());

        Comment reply = Comment.builder()
                .user(author)
                .post(post)
                .parent(parent)
                .content("Reply")
                .depth(1)
                .build();
        ReflectionTestUtils.setField(reply, "commentId", 10L);
        ReflectionTestUtils.setField(reply, "createdAt", LocalDateTime.now());

        when(commentRepository.findByIdWithRelations(9L)).thenReturn(Optional.of(parent));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of());
        when(commentRepository.findRepliesWithRelations(9L, false, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(reply), PageRequest.of(0, 10), 1));
        when(commentRepository.countVisibleRepliesByParentIds(List.of(10L)))
                .thenReturn(List.of(replyCountProjection(10L, 1L)));

        var result = commentService.getReplies(9L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getCommentId()).isEqualTo(10L);
        assertThat(response.getReplyCount()).isEqualTo(1L);
        assertThat(response.isHasReplies()).isTrue();
        assertThat(response.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("mask blocked user when loading a single comment")
    void getComment_masked() {
        User blockedUser = User.builder().displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedUser, "userId", 2L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(viewer).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(viewer).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Comment comment = Comment.builder()
                .user(blockedUser)
                .post(post)
                .content("Blocked comment")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());

        when(commentRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(2L));

        CommentResponse result = commentService.getComment(10L, 1L);

        assertThat(result.getContent()).isNotEqualTo("Blocked comment");
        assertThat(result.getAuthor().getDisplayName()).isNotEqualTo("Blocked");
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
        when(commentLikeRepository.saveAndFlush(any())).thenReturn(CommentLike.builder().user(user).comment(comment).build());
        when(commentRepository.incrementLikeCount(10L)).thenReturn(1);

        commentService.likeComment(1L, 10L);

        verify(commentLikeRepository).saveAndFlush(any());
        verify(commentRepository).incrementLikeCount(10L);
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤? 醫뗭븘?????녿떎")
    void likeComment_bannedUser_forbidden() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> commentService.likeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(commentLikeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("private board comments are hidden from anonymous users")
    void getComments_privateBoardAnonymous_forbidden() {
        User owner = User.builder().displayName("Owner").build();
        ReflectionTestUtils.setField(owner, "userId", 1L);

        Board board = Board.builder().boardUrl("private").creator(owner).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", false);

        Post post = Post.builder().board(board).user(owner).build();
        ReflectionTestUtils.setField(post, "postId", 10L);

        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.getComments(10L, null, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("secret post comments are hidden from non-author users")
    void getComments_secretPostNonAuthor_forbidden() {
        User owner = User.builder().displayName("Owner").build();
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);

        Board board = Board.builder().boardUrl("free").creator(owner).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).user(owner).build();
        ReflectionTestUtils.setField(post, "postId", 10L);
        ReflectionTestUtils.setField(post, "isSecret", true);

        when(postRepository.findByIdWithRelations(10L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.getComments(10L, 2L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("like comment is blocked when the post is secret")
    void likeComment_secretPostNonAuthor_forbidden() {
        User owner = User.builder().displayName("Owner").build();
        ReflectionTestUtils.setField(owner, "userId", 1L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);

        Board board = Board.builder().boardUrl("free").creator(owner).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).user(owner).build();
        ReflectionTestUtils.setField(post, "isSecret", true);

        Comment comment = Comment.builder().user(owner).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.likeComment(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
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
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤????섏젙?????녿떎")
    void updateComment_bannedUser_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old").build();

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "New"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("delete comment")
    void deleteComment_success() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);
        when(pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                user,
                "EARN",
                "COMMENT",
                10L))
                .thenReturn(List.of(PointHistory.builder()
                        .user(user)
                        .type("EARN")
                        .amount(10)
                        .balanceAfter(100)
                        .description("댓글 작성")
                        .relatedId(10L)
                        .relatedType("COMMENT")
                        .build()));

        commentService.deleteComment(1L, 10L);

        assertThat(comment.getIsDeleted()).isTrue();
        verify(postRepository).decrementCommentCount(1L);
        verify(pointService).forceSubtractPoint(1L, 10, "댓글 삭제", 10L, "COMMENT");
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤????쒖젣?????녿떎")
    void deleteComment_bannedUser_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Content").build();

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postRepository, never()).decrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("delete comment uses point history reward instead of current config")
    void deleteComment_usesRecordedRewardAmount() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);
        when(pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                user,
                "EARN",
                "COMMENT",
                10L))
                .thenReturn(List.of(
                        PointHistory.builder()
                                .user(user)
                                .type("EARN")
                                .amount(10)
                                .balanceAfter(100)
                                .description("댓글 작성")
                                .relatedId(10L)
                                .relatedType("COMMENT")
                                .build(),
                        PointHistory.builder()
                                .user(user)
                                .type("EARN")
                                .amount(5)
                                .balanceAfter(105)
                                .description("댓글 작성")
                                .relatedId(10L)
                                .relatedType("COMMENT")
                                .build()));

        commentService.deleteComment(1L, 10L);

        verify(pointService).forceSubtractPoint(1L, 15, "댓글 삭제", 10L, "COMMENT");
    }

    @Test
    @DisplayName("delete comment skips point rollback when reward history is missing")
    void deleteComment_withoutRewardHistory_skipsRollback() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);
        when(pointHistoryRepository.findByUserAndTypeAndRelatedTypeAndRelatedIdOrderByCreatedAtAsc(
                user,
                "EARN",
                "COMMENT",
                10L))
                .thenReturn(List.of());

        commentService.deleteComment(1L, 10L);

        verify(postRepository).decrementCommentCount(1L);
        verify(pointService, never())
                .forceSubtractPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤? 醫뗭븘??痍⑥냼?????녿떎")
    void unlikeComment_bannedUser_forbidden() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> commentService.unlikeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(commentLikeRepository, never()).deleteByUserIdAndCommentId(anyLong(), anyLong());
    }

    private CommentRepository.ReplyCountProjection replyCountProjection(Long parentId, long replyCount) {
        return new CommentRepository.ReplyCountProjection() {
            @Override
            public Long getParentId() {
                return parentId;
            }

            @Override
            public long getReplyCount() {
                return replyCount;
            }
        };
    }
}
