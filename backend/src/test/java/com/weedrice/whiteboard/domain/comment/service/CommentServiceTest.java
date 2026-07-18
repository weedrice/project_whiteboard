package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.service.BoardCategoryWritePolicy;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentClosureRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentLikeRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentMentionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentVersionRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.service.CommentStreamEventDispatcher;
import com.weedrice.whiteboard.domain.notification.service.MentionService;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostAccessPolicy;
import com.weedrice.whiteboard.domain.post.service.PostAuthorCommandPolicy;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
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
import org.springframework.context.support.StaticMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

    private CommentService commentService;
    private BoardAccessPolicy boardAccessPolicy;
    private PostAccessPolicy postAccessPolicy;

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private CommentLikeRepository commentLikeRepository;
    @Mock
    private CommentVersionRepository commentVersionRepository;
    @Mock
    private CommentClosureRepository commentClosureRepository;
    @Mock
    private CommentMentionRepository commentMentionRepository;
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
    private BoardCategoryRepository boardCategoryRepository;
    @Mock
    private AgentOwnershipService agentOwnershipService;
    @Mock
    private SanctionService sanctionService;
    @Mock
    private SemanticSearchEventPublisher semanticSearchEventPublisher;
    @Mock
    private MentionService mentionService;
    @Mock
    private CommentStreamEventDispatcher commentStreamEventDispatcher;
    @Mock
    private BadgeEvaluationService badgeEvaluationService;

    @BeforeEach
    void setUp() {
        Map<Long, Comment> lockedCommentTargets = new HashMap<>();
        lenient().when(postRepository.findByIdWithRelationsForUpdate(anyLong()))
                .thenAnswer(invocation -> postRepository.findByIdWithRelations(invocation.getArgument(0)));
        lenient().when(postRepository.findByCommentIdWithRelationsForUpdate(anyLong()))
                .thenAnswer(invocation -> {
                    Long commentId = invocation.getArgument(0);
                    Optional<Comment> comment = commentRepository.findById(commentId);
                    if (comment.isEmpty()) {
                        comment = commentRepository.findByIdWithRelationsForUpdate(commentId);
                    }
                    comment.ifPresent(value -> lockedCommentTargets.put(commentId, value));
                    return comment.map(Comment::getPost);
                });
        lenient().when(commentRepository.findByIdWithRelationsForUpdate(anyLong()))
                .thenAnswer(invocation -> {
                    Long commentId = invocation.getArgument(0);
                    Comment cached = lockedCommentTargets.remove(commentId);
                    return cached == null ? commentRepository.findById(commentId) : Optional.of(cached);
                });
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        postAccessPolicy = new PostAccessPolicy(boardAccessPolicy);
        CommentPostAccessService commentPostAccessService = new CommentPostAccessService(userBlockService, postAccessPolicy);
        CommentReadSupport commentReadSupport = new CommentReadSupport(commentRepository);
        CommentReadModelAssembler commentReadModelAssembler = new CommentReadModelAssembler(commentReadSupport);
        CommentQueryService commentQueryService = new CommentQueryService(
                commentRepository,
                postRepository,
                userBlockRepository,
                new UserReadableResolver(userRepository),
                commentPostAccessService,
                commentReadSupport,
                commentReadModelAssembler,
                commentMentionRepository,
                new StaticMessageSource());
        ContentRewardService contentRewardService = new ContentRewardService(
                pointService,
                pointHistoryRepository,
                globalConfigService);
        CommentNotificationService commentNotificationService = new CommentNotificationService(eventPublisher);
        CommentLikeCommand commentLikeCommand = new CommentLikeCommand(commentRepository, commentLikeRepository);
        UserWritableResolver userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        BoardCategoryWritePolicy boardCategoryWritePolicy = new BoardCategoryWritePolicy(boardAccessPolicy);
        PostAuthorCommandPolicy postAuthorCommandPolicy = new PostAuthorCommandPolicy(
                boardAccessPolicy,
                boardCategoryRepository,
                boardCategoryWritePolicy);
        CommentCommandService commentCommandService = new CommentCommandService(
                commentRepository,
                postRepository,
                boardRepository,
                commentLikeRepository,
                commentVersionRepository,
                commentClosureRepository,
                commentMentionRepository,
                userRepository,
                agentOwnershipService,
                userWritableResolver,
                sanctionService,
                commentPostAccessService,
                postAuthorCommandPolicy,
                contentRewardService,
                commentNotificationService,
                mentionService,
                commentStreamEventDispatcher,
                semanticSearchEventPublisher,
                commentLikeCommand,
                badgeEvaluationService);
        commentService = new CommentService(commentQueryService, commentCommandService);
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
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        Long result = commentService.createComment(1L, 1L, null, "content");

        assertThat(result).isEqualTo(10L);
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getDepth()).isZero();
        verify(commentClosureRepository).createSelfClosure(10L);
        verify(postRepository).incrementCommentCount(1L);
        verify(pointService).addPointIfAbsent(eq(1L), eq(10), anyString(), eq(10L), eq("COMMENT"));
    }

    @Test
    @DisplayName("comment mention ids publish mention notifications")
    void createComment_withMentionedUserIds_publishesMentions() {
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
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        Long result = commentService.createComment(1L, 1L, null, "hello @user", List.of(2L, 3L));

        assertThat(result).isEqualTo(10L);
        verify(mentionService).publishMentions(user, null, NotificationSourceType.COMMENT, 10L, List.of(2L, 3L));
    }

    @Test
    @DisplayName("comment on agent-authored post notifies agent owner")
    void createComment_agentPost_notifiesAgentOwner() {
        User actor = User.builder().displayName("Actor").build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
        User agentOwner = User.builder().displayName("Agent Owner").build();
        ReflectionTestUtils.setField(agentOwner, "userId", 3L);
        User legacyAuthor = User.builder().displayName("Legacy Author").build();
        ReflectionTestUtils.setField(legacyAuthor, "userId", 4L);

        Agent targetAgent = Agent.builder()
                .user(agentOwner)
                .agentTokenHash("hash")
                .name("target-agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(targetAgent, "agentId", 77L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().user(legacyAuthor).agent(targetAgent).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementCommentCount(1L)).thenReturn(1);
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        commentService.createComment(2L, 1L, null, "content");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getUserToNotify()).isSameAs(agentOwner);
        assertThat(notificationEvent.getActor()).isSameAs(actor);
        assertThat(notificationEvent.getActorAgent()).isNull();
        assertThat(notificationEvent.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(notificationEvent.getSourceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("HTML留??⑤뒗 ?볤? 蹂몃Ц? INVALID_INPUT_VALUE濡?嫄곕??쒕떎")
    void createComment_htmlOnlyContent_throwsInvalidInput() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().user(user).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, null, "<p></p>"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(commentRepository, never()).save(any(Comment.class));
        verify(postRepository, never()).incrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("?볤? ?앹꽦? 寃뚯떆湲 ?볤? ??媛깆떊 ?ㅽ뙣 ??濡ㅻ갚?쒕떎")
    void createComment_commentCountUpdateFails_throwsPostNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(postRepository.incrementCommentCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, null, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(commentVersionRepository, never()).save(any());
        verify(commentClosureRepository, never()).createSelfClosure(anyLong());
        verify(pointService, never()).addPointIfAbsent(anyLong(), anyInt(), anyString(), anyLong(), anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("?쒕퉬??吏꾩엯?먯? 1000?먮? 珥덇낵?섎뒗 ?볤? 蹂몃Ц??嫄곕??쒕떎")
    void createComment_tooLongContent_throwsInvalidInput() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().user(user).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, null, "a".repeat(1001)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(commentRepository, never()).save(any(Comment.class));
        verify(postRepository, never()).incrementCommentCount(anyLong());
    }

    @Test
    @DisplayName("?묒꽦?먭? ?볤? ?묒꽦?먮? 李⑤떒?섎㈃ ?볤????묒꽦?????녿떎")
    void createComment_authorBlocksViewer_throwsPostNotFound() {
        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder().boardUrl("free").creator(author).build();
        Post post = Post.builder().user(author).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> commentService.createComment(2L, 100L, null, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("?쎌쓣 ???덉?留??????녿뒗 ?몃뱶?먮뒗 ?볤????묒꽦?????녿떎")
    void createComment_readableButNotWritableBoard_throwsBoardNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        User boardOwner = User.builder().build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        Board board = Board.builder().boardUrl("free").creator(boardOwner).build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "isActive", false);
        Post post = Post.builder().user(user).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(boardRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> commentService.createComment(1L, 100L, null, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(adminRepository).existsByUserAndBoardAndIsActive(user, board, true);
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("移댄뀒怨좊━ 理쒖냼 ?묒꽦 沅뚰븳??留뚯”?섏? 紐삵븯硫??볤????묒꽦?????녿떎")
    void createComment_categoryWriteRoleForbidden_throwsForbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        User boardOwner = User.builder().build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        Board board = Board.builder().boardUrl("free").creator(boardOwner).build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        BoardCategory category = BoardCategory.builder()
                .name("Admin Only")
                .board(board)
                .minWriteRole("BOARD_ADMIN")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        Post post = Post.builder().user(boardOwner).board(board).category(category).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(boardRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> commentService.createComment(1L, 100L, null, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("移댄뀒怨좊━ 理쒖냼 ?묒꽦 沅뚰븳??留뚯”?섏? 紐삵븯硫??듦???遺紐?議고쉶 ?꾩뿉 李⑤떒?쒕떎")
    void createComment_replyCategoryWriteRoleForbidden_rejectsBeforeParentLookup() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        User boardOwner = User.builder().build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        Board board = Board.builder().boardUrl("free").creator(boardOwner).build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        BoardCategory category = BoardCategory.builder()
                .name("Admin Only")
                .board(board)
                .minWriteRole("BOARD_ADMIN")
                .build();
        ReflectionTestUtils.setField(category, "categoryId", 10L);
        Post post = Post.builder().user(boardOwner).board(board).category(category).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(boardRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> commentService.createComment(1L, 100L, 5L, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(commentRepository, never()).findByIdWithRelationsForUpdate(5L);
        verify(commentRepository, never()).save(any(Comment.class));
        verify(commentClosureRepository, never()).createClosures(anyLong(), anyLong());
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤????묒꽦?????녿떎")
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
    @DisplayName("?쒖꽦 MUTE ?ъ슜?먮뒗 ?볤????묒꽦?????녿떎")
    void createComment_mutedUser_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(user);

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
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        Long result = commentService.createComment(1L, 1L, 5L, "content");

        assertThat(result).isEqualTo(10L);
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getDepth()).isEqualTo(1);
        verify(postRepository).incrementCommentCount(1L);
        verify(commentClosureRepository).createClosures(10L, 5L);
    }

    @Test
    @DisplayName("블라인드 댓글에는 답글을 생성할 수 없다")
    void createComment_blindedParent_throwsCommentNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").isPublic(true).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        Comment parent = Comment.builder().depth(0).user(user).post(post).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);
        parent.blind("AUTO_REPORT", LocalDateTime.now());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, 5L, "reply"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("reply to agent-authored comment notifies agent owner")
    void createComment_agentParentComment_notifiesAgentOwner() {
        User actor = User.builder().displayName("Actor").build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
        User agentOwner = User.builder().displayName("Agent Owner").build();
        ReflectionTestUtils.setField(agentOwner, "userId", 3L);
        User legacyAuthor = User.builder().displayName("Legacy Author").build();
        ReflectionTestUtils.setField(legacyAuthor, "userId", 4L);

        Agent targetAgent = Agent.builder()
                .user(agentOwner)
                .agentTokenHash("hash")
                .name("target-agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(targetAgent, "agentId", 77L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(legacyAuthor).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment parent = Comment.builder()
                .depth(0)
                .user(legacyAuthor)
                .agent(targetAgent)
                .post(post)
                .build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementCommentCount(1L)).thenReturn(1);
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        commentService.createComment(2L, 1L, 5L, "content");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getUserToNotify()).isSameAs(agentOwner);
        assertThat(notificationEvent.getActor()).isSameAs(actor);
        assertThat(notificationEvent.getActorAgent()).isNull();
        assertThat(notificationEvent.getSourceType()).isEqualTo(NotificationSourceType.COMMENT);
        assertThat(notificationEvent.getSourceId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("create comment with parent depth four creates depth five")
    void createComment_parentDepthFour_createsDepthFive() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment parent = Comment.builder().depth(4).user(user).post(post).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementCommentCount(1L)).thenReturn(1);
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });

        Long result = commentService.createComment(1L, 1L, 5L, "content");

        assertThat(result).isEqualTo(10L);
        ArgumentCaptor<Comment> commentCaptor = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getDepth()).isEqualTo(5);
        verify(commentClosureRepository).createClosures(10L, 5L);
    }

    @Test
    @DisplayName("create comment rejects replies beyond depth five")
    void createComment_parentDepthFive_throwsInvalidInput() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment parent = Comment.builder().depth(5).user(user).post(post).build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, 5L, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(commentRepository, never()).save(any(Comment.class));
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
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> commentService.createComment(1L, 1L, 5L, "content"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("foreign agent濡??볤? ?묒꽦 ??嫄곕??쒕떎")
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
    @DisplayName("agent ?볤???HTML留??⑤뒗 蹂몃Ц? INVALID_INPUT_VALUE濡?嫄곕??쒕떎")
    void createCommentAsAgent_htmlOnlyContent_throwsInvalidInput() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 10L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().user(user).board(board).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentOwnershipService.resolveOwnedActiveAgent(1L, 10L)).thenReturn(agent);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.createCommentAsAgent(1L, 10L, 1L, null, "<span></span>"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(commentRepository, never()).save(any(Comment.class));
        verify(postRepository, never()).incrementCommentCount(anyLong());
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
        when(postRepository.incrementCommentCount(1L)).thenReturn(1);
        when(commentRepository.findByIdWithRelationsForUpdate(5L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "commentId", 10L);
            return saved;
        });
        when(globalConfigService.getConfig(anyString())).thenReturn("10");

        commentService.createCommentAsAgent(2L, 99L, 1L, 5L, "content");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getActorAgent()).isNotNull();
        assertThat(notificationEvent.getMessageKey()).isEqualTo("notification.reply.created");
        assertThat(notificationEvent.getMessageParams()).containsExactly("agent-writer");
    }

    @Test
    @DisplayName("agent context comment still rejects muted owner")
    void createCommentAsAgent_withContext_mutedUser_forbidden() {
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
        Post post = Post.builder().board(board).user(actorUser).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actorUser));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(actorUser);

        assertThatThrownBy(() -> commentService.createCommentAsAgent(
                2L,
                99L,
                1L,
                null,
                "content",
                CommentCreateContext.agentRoot(agent, post)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("agent context comment rejects mismatched agent")
    void createCommentAsAgent_withContext_mismatchedAgent_forbidden() {
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
        Post post = Post.builder().board(board).user(actorUser).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actorUser));

        assertThatThrownBy(() -> commentService.createCommentAsAgent(
                2L,
                100L,
                1L,
                null,
                "content",
                CommentCreateContext.agentRoot(agent, post)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("agent context comment rejects mismatched post")
    void createCommentAsAgent_withContext_mismatchedPost_notFound() {
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
        Post post = Post.builder().board(board).user(actorUser).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actorUser));

        assertThatThrownBy(() -> commentService.createCommentAsAgent(
                2L,
                99L,
                2L,
                null,
                "content",
                CommentCreateContext.agentRoot(agent, post)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    @DisplayName("agent context reply rejects replies beyond depth five")
    void createCommentAsAgent_withContext_parentDepthFive_throwsInvalidInput() {
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
        Post post = Post.builder().board(board).user(actorUser).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment parent = Comment.builder()
                .user(actorUser)
                .post(post)
                .content("parent")
                .depth(5)
                .build();
        ReflectionTestUtils.setField(parent, "commentId", 5L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actorUser));

        assertThatThrownBy(() -> commentService.createCommentAsAgent(
                2L,
                99L,
                1L,
                5L,
                "content",
                CommentCreateContext.agentReply(agent, parent)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(commentRepository, never()).save(any(Comment.class));
        verify(commentRepository, never()).findByIdWithRelationsForUpdate(5L);
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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));
        when(commentRepository.findParentsWithChildrenOrNotDeleted(anyLong(), anyBoolean(), anyCollection(), any()))
                .thenReturn(new PageImpl<>(List.of(comment)));

        Page<CommentResponse> result = commentService.getComments(100L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getContent()).isNull();
        assertThat(response.getAuthor()).isNull();
        assertThat(response.isBlockedAuthor()).isTrue();
        assertThat(response.getMaskedAuthorId()).isEqualTo(2L);
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(1L);
    }

    @Test
    @DisplayName("블라인드 댓글은 멘션 사용자 정보를 조회하거나 노출하지 않는다")
    void getComments_blindedComment_doesNotAttachMentions() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 2L);
        Board board = Board.builder().boardUrl("free").creator(author).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);
        Post post = Post.builder().board(board).title("Title").user(author).build();
        ReflectionTestUtils.setField(post, "postId", 100L);
        Comment comment = Comment.builder()
                .user(author)
                .post(post)
                .content("hidden @mention")
                .depth(0)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.blind("AUTO_REPORT", LocalDateTime.now());

        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(commentRepository.findParentsWithChildrenOrNotDeleted(anyLong(), anyBoolean(), anyCollection(), any()))
                .thenReturn(new PageImpl<>(List.of(comment)));

        Page<CommentResponse> result = commentService.getComments(100L, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).singleElement()
                .satisfies(response -> assertThat(response.getMentions()).isEmpty());
        verify(commentMentionRepository, never()).findByCommentCommentIdIn(anyCollection());
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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));
        Pageable pageable = commentReadPageable(0, 10);
        when(commentRepository.findRepliesWithRelations(9L, false, false, List.of(2L), pageable))
                .thenReturn(new PageImpl<>(List.of(reply), pageable, 1));

        var result = commentService.getReplies(9L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getContent()).isNull();
        assertThat(response.getAuthor()).isNull();
        assertThat(response.isBlockedAuthor()).isTrue();
        assertThat(response.getMaskedAuthorId()).isEqualTo(2L);
        verify(userBlockService).getBlockedUserIdsEitherDirectionForExistingUser(1L);
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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        Pageable pageable = commentReadPageable(0, 10);
        when(commentRepository.findParentsWithChildrenOrNotDeleted(100L, true, NO_BLOCKED_USER_IDS, pageable))
                .thenReturn(new PageImpl<>(List.of(parent), pageable, 1));
        when(commentRepository.countVisibleRepliesByParentIds(List.of(10L), true, NO_BLOCKED_USER_IDS))
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
    @DisplayName("getComments normalizes pageable before repository call")
    void getComments_normalizesPageableBeforeRepositoryCall() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 2L);

        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(author).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).title("Title").user(author).build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        Pageable requested = PageRequest.of(2, 1000, Sort.by(Sort.Order.desc("likeCount")));
        Pageable normalized = commentLikePageable(2, 100);

        when(postRepository.findByIdWithRelations(100L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        when(commentRepository.findParentsWithChildrenOrNotDeletedOrderByLikeCount(100L, true, NO_BLOCKED_USER_IDS, normalized))
                .thenReturn(Page.empty(normalized));

        commentService.getComments(100L, 1L, requested);

        verify(commentRepository).findParentsWithChildrenOrNotDeletedOrderByLikeCount(100L, true, NO_BLOCKED_USER_IDS, normalized);
    }

    @Test
    @DisplayName("???볤? 議고쉶???섏씠吏 ?ш린? ?뺣젹 ?꾨뱶瑜??쒗븳?쒕떎")
    void getMyComments_normalizesPageableBeforeRepositoryCall() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Pageable requested = PageRequest.of(2, 250, Sort.by(Sort.Order.asc("likeCount")));
        Pageable normalized = PageRequest.of(2, 100, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("commentId")));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findVisibleMyComments(
                user,
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                normalized))
                .thenReturn(Page.empty(normalized));

        commentService.getMyComments(1L, requested);

        verify(commentRepository).findVisibleMyComments(
                user,
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                normalized);
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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        Pageable pageable = commentReadPageable(0, 10);
        when(commentRepository.findRepliesWithRelations(9L, false, true, NO_BLOCKED_USER_IDS, pageable))
                .thenReturn(new PageImpl<>(List.of(reply), pageable, 1));
        when(commentRepository.countVisibleRepliesByParentIds(List.of(10L), true, NO_BLOCKED_USER_IDS))
                .thenReturn(List.of(replyCountProjection(10L, 1L)));

        var result = commentService.getReplies(9L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        CommentResponse response = result.getContent().get(0);
        assertThat(response.getCommentId()).isEqualTo(10L);
        assertThat(response.getReplyCount()).isEqualTo(1L);
        assertThat(response.isHasReplies()).isTrue();
        assertThat(response.getChildren()).isEmpty();
        verify(commentRepository, never()).findAllDescendants(anyList());
    }

    @Test
    @DisplayName("deleted parent comment with no visible replies is hidden from direct replies lookup")
    void getReplies_deletedParentWithoutVisibleReplies_notFound() {
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
        ReflectionTestUtils.setField(parent, "isDeleted", true);

        Pageable pageable = commentReadPageable(0, 10);
        when(commentRepository.findByIdWithRelations(9L)).thenReturn(Optional.of(parent));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        when(commentRepository.findRepliesWithRelations(9L, false, true, NO_BLOCKED_USER_IDS, pageable))
                .thenReturn(Page.empty(pageable));
        when(commentRepository.existsVisibleReplyByParentId(9L, true, NO_BLOCKED_USER_IDS))
                .thenReturn(false);

        assertThatThrownBy(() -> commentService.getReplies(9L, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(commentRepository).findRepliesWithRelations(9L, false, true, NO_BLOCKED_USER_IDS, pageable);
    }

    @Test
    @DisplayName("deleted parent comment with only blocked replies is hidden from direct replies lookup")
    void getReplies_deletedParentWithOnlyBlockedReplies_notFound() {
        User blockedAuthor = User.builder().displayName("Blocked").build();
        ReflectionTestUtils.setField(blockedAuthor, "userId", 2L);

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
        ReflectionTestUtils.setField(parent, "isDeleted", true);

        Comment blockedReply = Comment.builder()
                .user(blockedAuthor)
                .post(post)
                .parent(parent)
                .content("Blocked Reply")
                .depth(1)
                .build();
        ReflectionTestUtils.setField(blockedReply, "commentId", 10L);
        ReflectionTestUtils.setField(blockedReply, "createdAt", LocalDateTime.now());

        Pageable pageable = commentReadPageable(0, 10);
        when(commentRepository.findByIdWithRelations(9L)).thenReturn(Optional.of(parent));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));
        when(commentRepository.findRepliesWithRelations(9L, false, false, List.of(2L), pageable))
                .thenReturn(new PageImpl<>(List.of(blockedReply), pageable, 1));
        when(commentRepository.existsVisibleReplyByParentId(9L, false, List.of(2L)))
                .thenReturn(false);

        assertThatThrownBy(() -> commentService.getReplies(9L, 1L, PageRequest.of(0, 10)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(commentRepository).findRepliesWithRelations(9L, false, false, List.of(2L), pageable);
        verify(commentRepository).existsVisibleReplyByParentId(9L, false, List.of(2L));
    }

    @Test
    @DisplayName("deleted parent comment with visible replies can load replies")
    void getReplies_deletedParentWithVisibleReplies_returnsReplies() {
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
        ReflectionTestUtils.setField(parent, "isDeleted", true);

        Comment reply = Comment.builder()
                .user(author)
                .post(post)
                .parent(parent)
                .content("Reply")
                .depth(1)
                .build();
        ReflectionTestUtils.setField(reply, "commentId", 10L);
        ReflectionTestUtils.setField(reply, "createdAt", LocalDateTime.now());

        Pageable pageable = commentReadPageable(0, 10);
        when(commentRepository.findByIdWithRelations(9L)).thenReturn(Optional.of(parent));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        when(commentRepository.findRepliesWithRelations(9L, false, true, NO_BLOCKED_USER_IDS, pageable))
                .thenReturn(new PageImpl<>(List.of(reply), pageable, 1));
        when(commentRepository.existsVisibleReplyByParentId(9L, true, NO_BLOCKED_USER_IDS))
                .thenReturn(true);

        var result = commentService.getReplies(9L, 1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getCommentId()).isEqualTo(10L);
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

        when(commentRepository.findNonDeletedByIdWithRelations(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));

        CommentResponse result = commentService.getComment(10L, 1L);

        assertThat(result.getContent()).isNull();
        assertThat(result.getAuthor()).isNull();
        assertThat(result.isBlockedAuthor()).isTrue();
        assertThat(result.getMaskedAuthorId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("deleted comment is hidden from direct comment lookup")
    void getComment_deletedComment_notFound() {
        when(commentRepository.findNonDeletedByIdWithRelations(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getComment(10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
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
        when(commentLikeRepository.insertIgnore(1L, 10L)).thenReturn(1);
        when(commentRepository.incrementLikeCount(10L)).thenReturn(1);
        when(commentRepository.findLikeCountByCommentId(10L)).thenReturn(1);

        commentService.likeComment(1L, 10L);

        verify(commentLikeRepository).insertIgnore(1L, 10L);
        verify(commentRepository).incrementLikeCount(10L);
    }

    @Test
    @DisplayName("like on agent-authored comment notifies agent owner")
    void likeComment_agentComment_notifiesAgentOwner() {
        User actor = User.builder().displayName("Actor").build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
        User agentOwner = User.builder().displayName("Agent Owner").build();
        ReflectionTestUtils.setField(agentOwner, "userId", 3L);
        User legacyAuthor = User.builder().displayName("Legacy Author").build();
        ReflectionTestUtils.setField(legacyAuthor, "userId", 4L);

        Agent targetAgent = Agent.builder()
                .user(agentOwner)
                .agentTokenHash("hash")
                .name("target-agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(targetAgent, "agentId", 77L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(legacyAuthor).build();
        Comment comment = Comment.builder()
                .user(legacyAuthor)
                .agent(targetAgent)
                .post(post)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.insertIgnore(2L, 10L)).thenReturn(1);
        when(commentRepository.incrementLikeCount(10L)).thenReturn(1);
        when(commentRepository.findLikeCountByCommentId(10L)).thenReturn(1);

        commentService.likeComment(2L, 10L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getUserToNotify()).isSameAs(agentOwner);
        assertThat(notificationEvent.getActor()).isSameAs(actor);
        assertThat(notificationEvent.getActorAgent()).isNull();
        assertThat(notificationEvent.getSourceType()).isEqualTo(NotificationSourceType.COMMENT);
        assertThat(notificationEvent.getSourceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("?묒꽦?먭? ?볤? 醫뗭븘???ъ슜?먮? 李⑤떒?섎㈃ 醫뗭븘?뷀븷 ???녿떎")
    void likeComment_authorBlocksViewer_throwsPostNotFound() {
        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder().boardUrl("free").creator(author).build();
        Post post = Post.builder().board(board).user(author).build();
        Comment comment = Comment.builder().user(author).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> commentService.likeComment(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(commentLikeRepository, never()).saveAndFlush(any());
        verify(commentRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("?쒖꽦 MUTE ?ъ슜?먮뒗 ?볤? 醫뗭븘?붾? ?????녿떎")
    void likeComment_mutedUser_forbidden() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(user);

        assertThatThrownBy(() -> commentService.likeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(commentRepository, never()).findById(anyLong());
        verify(commentLikeRepository, never()).saveAndFlush(any());
        verify(commentRepository, never()).incrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("duplicate comment like maps to already liked without counter or notification")
    void likeComment_duplicate_doesNotIncrementOrNotify() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.insertIgnore(1L, 10L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.likeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_LIKED);

        verify(commentRepository, never()).incrementLikeCount(anyLong());
        verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
    }

    @Test
    @DisplayName("?볤? 醫뗭븘?붾뒗 移댁슫??媛깆떊 ?ㅽ뙣 ??COMMENT_NOT_FOUND瑜?諛섑솚?쒕떎")
    void likeComment_likeCountUpdateFails_throwsCommentNotFound() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.insertIgnore(1L, 10L)).thenReturn(1);
        when(commentRepository.incrementLikeCount(10L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.likeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("??젣???볤?? 醫뗭븘?뷀븷 ???녿떎")
    void likeComment_deletedComment_throwsCommentNotFound() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.deleteComment();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.likeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(commentLikeRepository, never()).saveAndFlush(any());
        verify(commentRepository, never()).incrementLikeCount(anyLong());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("블라인드 댓글에는 좋아요를 추가할 수 없다")
    void likeComment_blindedComment_throwsCommentNotFound() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").isPublic(true).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.blind("AUTO_REPORT", LocalDateTime.now());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.likeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(commentLikeRepository, never()).insertIgnore(anyLong(), anyLong());
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤? 醫뗭븘?붾? ?????녿떎")
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
    @DisplayName("?볤? 醫뗭븘??痍⑥냼 ?깃났")
    void unlikeComment_success() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.deleteByUserIdAndCommentId(1L, 10L)).thenReturn(1);
        when(commentRepository.decrementLikeCount(10L)).thenReturn(1);

        commentService.unlikeComment(1L, 10L);

        verify(commentLikeRepository).deleteByUserIdAndCommentId(1L, 10L);
        verify(commentRepository).findById(10L);
        verify(commentRepository).decrementLikeCount(10L);
    }

    @Test
    @DisplayName("블라인드 댓글의 기존 좋아요는 취소할 수 있다")
    void unlikeComment_blindedComment_allowsCleanup() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").isPublic(true).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.blind("AUTO_REPORT", LocalDateTime.now());

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        when(commentLikeRepository.deleteByUserIdAndCommentId(1L, 10L)).thenReturn(1);
        when(commentRepository.decrementLikeCount(10L)).thenReturn(1);

        commentService.unlikeComment(1L, 10L);

        verify(commentLikeRepository).deleteByUserIdAndCommentId(1L, 10L);
        verify(commentRepository).decrementLikeCount(10L);
    }

    @Test
    @DisplayName("醫뗭븘?뷀븯吏 ?딆? ?볤? 痍⑥냼??NOT_LIKED瑜?諛섑솚?쒕떎")
    void unlikeComment_notLiked() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.deleteByUserIdAndCommentId(1L, 10L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.unlikeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_LIKED);

        verify(commentRepository).findById(10L);
        verify(commentRepository, never()).decrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("?볤? 醫뗭븘??痍⑥냼??移댁슫??媛깆떊 ?ㅽ뙣 ??COMMENT_NOT_FOUND瑜?諛섑솚?쒕떎")
    void unlikeComment_likeCountUpdateFails_throwsCommentNotFound() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.deleteByUserIdAndCommentId(1L, 10L)).thenReturn(1);
        when(commentRepository.decrementLikeCount(10L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.unlikeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("??젣???볤???醫뗭븘??痍⑥냼???ㅽ뙣?쒕떎")
    void unlikeComment_deletedComment_throwsCommentNotFound() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.deleteComment();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.unlikeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(commentLikeRepository, never()).deleteByUserIdAndCommentId(anyLong(), anyLong());
        verify(commentRepository, never()).decrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("?쎌쓣 ???녿뒗 寃뚯떆湲???볤? 醫뗭븘??痍⑥냼???ㅽ뙣?쒕떎")
    void unlikeComment_authorBlocksViewer_throwsPostNotFound() {
        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder().boardUrl("free").creator(author).build();
        Post post = Post.builder().board(board).user(author).build();
        Comment comment = Comment.builder().user(author).post(post).build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(commentRepository.findById(10L)).thenReturn(Optional.of(comment));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> commentService.unlikeComment(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(commentLikeRepository, never()).deleteByUserIdAndCommentId(anyLong(), anyLong());
        verify(commentRepository, never()).decrementLikeCount(anyLong());
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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of());

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
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.likeComment(2L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("update comment without mention ids keeps existing mention metadata")
    void updateComment_withoutMentionIds_keepsExistingMentions() {
        User user = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(user).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old @Alice").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());

        Long result = commentService.updateComment(1L, 10L, "New @Alice");

        assertThat(result).isEqualTo(10L);
        assertThat(comment.getContent()).isEqualTo("New @Alice");
        verify(commentMentionRepository, never()).deleteByCommentCommentId(anyLong());
    }

    @Test
    @DisplayName("update comment with empty mention ids clears mention metadata")
    void updateComment_withEmptyMentionIds_clearsMentions() {
        User user = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").creator(user).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "isPublic", true);

        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old @Alice").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());

        Long result = commentService.updateComment(1L, 10L, "New", List.of());

        assertThat(result).isEqualTo(10L);
        assertThat(comment.getContent()).isEqualTo("New");
        verify(commentMentionRepository).deleteByCommentCommentId(10L);
    }

    @Test
    @DisplayName("update comment is blocked when the post author blocks the viewer")
    void updateComment_authorBlocksViewer_throwsPostNotFound() {
        User viewer = User.builder().displayName("Viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder().boardUrl("free").creator(author).build();
        Post post = Post.builder().board(board).user(author).build();
        Comment comment = Comment.builder().user(viewer).post(post).content("Old").build();

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> commentService.updateComment(2L, 10L, "New"))
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

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "New"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("HTML留??⑤뒗 ?볤? ?섏젙 蹂몃Ц? INVALID_INPUT_VALUE濡?嫄곕??쒕떎")
    void updateComment_htmlOnlyContent_throwsInvalidInput() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old").build();

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "<b></b>"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        assertThat(comment.getContent()).isEqualTo("Old");
        verify(commentVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("??젣???볤?? ?섏젙?????녿떎")
    void updateComment_deletedComment_throwsCommentNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old").build();
        comment.deleteComment();

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "New"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        assertThat(comment.getContent()).isEqualTo("Old");
        verify(commentVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("블라인드 댓글은 수정할 수 없다")
    void updateComment_blindedComment_throwsCommentNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").isPublic(true).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.blind("AUTO_REPORT", LocalDateTime.now());

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> commentService.updateComment(1L, 10L, "New"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        assertThat(comment.getContent()).isEqualTo("Old");
        verify(commentVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤????섏젙?????녿떎")
    void updateComment_bannedUser_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Old").build();

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
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

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user,
                List.of("EARN", "REWARD_REVERSAL"),
                "COMMENT",
                10L))
                .thenReturn(10L);

        commentService.deleteComment(1L, 10L);

        assertThat(comment.getIsDeleted()).isTrue();
        verify(postRepository).decrementCommentCount(1L);
        verify(pointService).reverseRewardPoint(1L, 10, "댓글 삭제", 10L, "COMMENT");
    }

    @Test
    @DisplayName("블라인드 댓글은 작성자가 삭제할 수 있다")
    void deleteComment_blindedComment_allowsCleanup() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").isPublic(true).build();
        ReflectionTestUtils.setField(board, "isActive", true);
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        comment.blind("AUTO_REPORT", LocalDateTime.now());

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of());
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);

        commentService.deleteComment(1L, 10L);

        assertThat(comment.getIsDeleted()).isTrue();
        verify(postRepository).decrementCommentCount(1L);
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤?????젣?????녿떎")
    void deleteComment_bannedUser_forbidden() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        Comment comment = Comment.builder().user(user).post(post).content("Content").build();

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
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

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user,
                List.of("EARN", "REWARD_REVERSAL"),
                "COMMENT",
                10L))
                .thenReturn(15L);

        commentService.deleteComment(1L, 10L);

        verify(pointService).reverseRewardPoint(1L, 15, "댓글 삭제", 10L, "COMMENT");
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

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(1);
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user,
                List.of("EARN", "REWARD_REVERSAL"),
                "COMMENT",
                10L))
                .thenReturn(0L);

        commentService.deleteComment(1L, 10L);

        verify(postRepository).decrementCommentCount(1L);
        verify(pointService, never())
                .reverseRewardPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("??젣???볤?? ??젣?????녿떎")
    void deleteComment_deletedComment_throwsCommentNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        comment.deleteComment();

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);

        verify(postRepository, never()).decrementCommentCount(anyLong());
        verify(commentVersionRepository, never()).save(any());
        verify(pointService, never())
                .reverseRewardPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("?볤? ??젣??寃뚯떆湲 ?볤? ??媛깆떊 ?ㅽ뙣 ??濡ㅻ갚?쒕떎")
    void deleteComment_commentCountUpdateFails_throwsPostNotFound() {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).user(user).build();
        ReflectionTestUtils.setField(post, "postId", 1L);

        Comment comment = Comment.builder().user(user).post(post).content("Content").build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);

        when(commentRepository.findByIdWithRelationsForUpdate(10L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.decrementCommentCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> commentService.deleteComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(commentVersionRepository, never()).save(any());
        verify(pointService, never())
                .reverseRewardPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 ?볤? 醫뗭븘?붾? 痍⑥냼?????녿떎")
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

    @Test
    @DisplayName("?쒖꽦 MUTE ?ъ슜?먮뒗 ?볤? 醫뗭븘?붾? 痍⑥냼?????녿떎")
    void unlikeComment_mutedUser_forbidden() {
        User user = User.builder().displayName("User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(user);

        assertThatThrownBy(() -> commentService.unlikeComment(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(commentRepository, never()).findById(anyLong());
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

    private Pageable commentReadPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId")));
    }

    private Pageable commentLikePageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("likeCount"),
                Sort.Order.asc("createdAt"),
                Sort.Order.asc("commentId")));
    }
}
