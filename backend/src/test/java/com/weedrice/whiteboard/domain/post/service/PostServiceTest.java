package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.service.BoardCategoryWritePolicy;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.service.MentionService;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.*;
import com.weedrice.whiteboard.domain.post.repository.*;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.post.scheduled.repository.ScheduledPostRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.domain.search.service.SearchRecordEventPublisher;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.config.AnonymousReadCacheInvalidator;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.common.service.ReactionWriter;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);
    private static final int MAX_KEYWORD_LENGTH = 255;

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private TagAssignmentService tagAssignmentService;
    @Mock
    private PostVersionRepository postVersionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private PointService pointService;
    @Mock
    private PointHistoryRepository pointHistoryRepository;
    @Mock
    private BoardCategoryRepository boardCategoryRepository;
    @Mock
    private ScrapRepository scrapRepository;
    @Mock
    private DraftPostRepository draftPostRepository;
    @Mock
    private ScheduledPostRepository scheduledPostRepository;
    @Mock
    private ViewHistoryRepository viewHistoryRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private FileService fileService;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private GlobalConfigService globalConfigService;
    @Mock
    private AgentOwnershipService agentOwnershipService;
    @Mock
    private SanctionService sanctionService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private SearchRecordEventPublisher searchRecordEventPublisher;
    @Mock
    private SemanticSearchEventPublisher semanticSearchEventPublisher;
    @Mock
    private MentionService mentionService;
    @Mock
    private PollService pollService;
    @Mock
    private BadgeEvaluationService badgeEvaluationService;
    @Mock
    private PostRelatedReadService postRelatedReadService;
    @Mock
    private PostManagerModerationService postManagerModerationService;
    @Mock
    private AnonymousReadCacheInvalidator anonymousReadCacheInvalidator;
    private BoardAccessPolicy boardAccessPolicy;
    private PostAccessPolicy postAccessPolicy;
    private PostSummaryAssembler postSummaryAssembler;
    private PostImageAttachmentReader postImageAttachmentReader;
    private ViewHistoryCommandService viewHistoryCommandService;
    private PostDetailReadService postDetailReadService;
    private PostDetailViewCommandService postDetailViewCommandService;
    private PostDraftService postDraftService;
    private PostInteractionService postInteractionService;
    private PostListReadService postListReadService;
    private PostFacadeReadService postFacadeReadService;
    private PostLatestReadService postLatestReadService;
    private PostAuthorCommandPolicy postAuthorCommandPolicy;
    private PostCommandService postCommandService;
    private UserWritableResolver userWritableResolver;

    private PostService postService;

    private User user;
    private Board board;
    private Post post;
    private BoardCategory category;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(invocation -> userRepository.findById(invocation.getArgument(0)));
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        PostInteractionContextResolver postInteractionContextResolver = new PostInteractionContextResolver(
                userRepository,
                postLikeRepository,
                scrapRepository,
                boardSubscriptionRepository);
        postSummaryAssembler = new PostSummaryAssembler(
                fileService,
                commentRepository,
                boardAccessPolicy,
                postInteractionContextResolver,
                new PostContentSummaryExtractor());
        FeedPostSummaryAssembler feedPostSummaryAssembler = new FeedPostSummaryAssembler(
                fileService,
                postInteractionContextResolver,
                new PostContentSummaryExtractor());
        postAccessPolicy = new PostAccessPolicy(boardAccessPolicy);
        PostReadContextResolver postReadContextResolver = new PostReadContextResolver(
                userRepository,
                userBlockService,
                adminRepository);
        PostDetailContextResolver postDetailContextResolver = new PostDetailContextResolver(
                postRepository,
                viewHistoryRepository,
                postReadContextResolver,
                postAccessPolicy);
        postImageAttachmentReader = new PostImageAttachmentReader(fileService);
        ReactionWriter reactionWriter = new ReactionWriter();
        postAuthorCommandPolicy = new PostAuthorCommandPolicy(
                boardAccessPolicy,
                boardCategoryRepository,
                new BoardCategoryWritePolicy(boardAccessPolicy));
        userWritableResolver = new UserWritableResolver(userRepository, sanctionService);
        PostDraftCleanupService postDraftCleanupService = new PostDraftCleanupService(
                draftPostRepository,
                fileService,
                Clock.systemUTC(),
                mock(PostDraftCleanupBatchService.class),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        viewHistoryCommandService = new ViewHistoryCommandService(viewHistoryRepository);
        PostViewCountWriter postViewCountWriter = new PostViewCountWriter(postRepository);
        PostReactionService postReactionService = new PostReactionService(
                postRepository,
                postLikeRepository,
                eventPublisher,
                reactionWriter,
                badgeEvaluationService,
                anonymousReadCacheInvalidator);
        PostScrapService postScrapService = new PostScrapService(
                scrapRepository,
                mock(com.weedrice.whiteboard.domain.post.repository.ScrapFolderRepository.class),
                reactionWriter);
        PostViewHistoryService postViewHistoryService = new PostViewHistoryService(
                viewHistoryRepository,
                viewHistoryCommandService,
                commentRepository,
                postRepository,
                postSummaryAssembler);
        postDetailReadService = new PostDetailReadService(
                postRepository,
                postVersionRepository,
                tagAssignmentService,
                postImageAttachmentReader,
                postInteractionContextResolver,
                boardAccessPolicy,
                postDetailContextResolver,
                pollService,
                mock(PostSeriesService.class));
        postDetailViewCommandService = new PostDetailViewCommandService(
                postRepository,
                viewHistoryCommandService,
                postDetailContextResolver,
                postViewCountWriter);
        postDraftService = new PostDraftService(
                userRepository,
                boardRepository,
                boardCategoryRepository,
                postRepository,
                mock(PostSeriesRepository.class),
                draftPostRepository,
                scheduledPostRepository,
                fileService,
                userWritableResolver,
                sanctionService,
                boardAccessPolicy,
                postAuthorCommandPolicy,
                postDraftCleanupService);
        postInteractionService = new PostInteractionService(
                postRepository,
                postReactionService,
                postScrapService,
                postViewHistoryService,
                postReadContextResolver,
                agentOwnershipService,
                userWritableResolver,
                postAccessPolicy,
                postViewCountWriter,
                entityManager,
                sanctionService);
        postLatestReadService = new PostLatestReadService(
                postRepository,
                userBlockService,
                postSummaryAssembler);
        postListReadService = new PostListReadService(
                postRepository,
                boardRepository,
                userRepository,
                userBlockRepository,
                postReadContextResolver,
                postSummaryAssembler,
                feedPostSummaryAssembler,
                boardAccessPolicy,
                postLatestReadService,
                searchRecordEventPublisher,
                java.time.Clock.fixed(
                        java.time.Instant.parse("2026-07-07T00:00:00Z"),
                        java.time.ZoneOffset.UTC));
        ContentRewardService contentRewardService = new ContentRewardService(
                pointService,
                pointHistoryRepository,
                globalConfigService);
        PostCreateTargetResolver postCreateTargetResolver = new PostCreateTargetResolver(
                boardRepository,
                boardCategoryRepository,
                agentOwnershipService,
                userWritableResolver,
                sanctionService);
        PostCreatePolicyValidator postCreatePolicyValidator = new PostCreatePolicyValidator(
                boardAccessPolicy,
                postAuthorCommandPolicy);
        PostVersionRecorder postVersionRecorder = new PostVersionRecorder(postVersionRepository);
        PostDraftPublicationService postDraftPublicationService = new PostDraftPublicationService(
                draftPostRepository,
                scheduledPostRepository,
                fileService);
        PostCreateSideEffectService postCreateSideEffectService = new PostCreateSideEffectService(
                tagAssignmentService,
                eventPublisher,
                contentRewardService,
                fileService,
                semanticSearchEventPublisher,
                postVersionRecorder,
                postDraftPublicationService,
                mentionService,
                pollService,
                badgeEvaluationService);
        postCommandService = new PostCommandService(
                postRepository,
                boardRepository,
                boardCategoryRepository,
                tagAssignmentService,
                contentRewardService,
                fileService,
                userWritableResolver,
                sanctionService,
                postCreateTargetResolver,
                postCreatePolicyValidator,
                postVersionRecorder,
                postDraftPublicationService,
                postCreateSideEffectService,
                boardAccessPolicy,
                postAuthorCommandPolicy,
                semanticSearchEventPublisher,
                mock(PostSeriesService.class),
                mock(com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService.class),
                mentionService,
                anonymousReadCacheInvalidator);
        postFacadeReadService = new PostFacadeReadService(
                postRepository,
                postVersionRepository,
                tagAssignmentService,
                postImageAttachmentReader,
                postReadContextResolver,
                postSummaryAssembler,
                postAccessPolicy,
                boardAccessPolicy);
        postService = new PostService(
                boardRepository,
                userRepository,
                postDetailReadService,
                postDetailViewCommandService,
                postDraftService,
                postInteractionService,
                postListReadService,
                boardAccessPolicy,
                postAuthorCommandPolicy,
                postCommandService,
                postFacadeReadService,
                postDetailContextResolver,
                mock(PostSeriesService.class),
                postRelatedReadService,
                postManagerModerationService);

        // GlobalConfigService 기본 mock 설정 - lenient()로 설정하여 일부 테스트에서 사용되지 않아도 허용
        lenient().when(globalConfigService.getConfig(anyString())).thenReturn("50");
        lenient().when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), eq(true)))
                .thenReturn(Collections.emptyList());
        lenient().when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());
        lenient().when(commentRepository.findPostIdsWithNonAuthorCommentsByPostIds(anyList()))
                .thenReturn(Collections.emptyList());

        user = User.builder().loginId("testuser").displayName("Test User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        board = Board.builder().boardName("Test Board").creator(user).build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "boardUrl", "free");
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "iconUrl", "icon.png");

        category = BoardCategory.builder().name("General").board(board).build();
        ReflectionTestUtils.setField(category, "categoryId", 1L);

        post = Post.builder().title("Test Post").contents("Test Contents").user(user).board(board).category(category)
                .build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "likeCount", 0);
        ReflectionTestUtils.setField(post, "viewCount", 0);

        lenient().when(boardRepository.findByBoardUrlForUpdate(anyString()))
                .thenAnswer(invocation -> boardRepository.findByBoardUrl(invocation.getArgument(0)));
        lenient().when(boardRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(invocation -> {
                    Optional<Board> resolved = boardRepository.findById(invocation.getArgument(0));
                    return resolved.isPresent() ? resolved : Optional.of(board);
                });
        lenient().when(postRepository.findBoardIdByPostId(1L)).thenReturn(Optional.of(1L));
    }

    private void assertDeleteVersionRecorded(User modifier) {
        ArgumentCaptor<PostVersion> versionCaptor = ArgumentCaptor.forClass(PostVersion.class);
        verify(postVersionRepository).save(versionCaptor.capture());
        PostVersion version = versionCaptor.getValue();
        assertThat(version.getPost()).isSameAs(post);
        assertThat(version.getModifier()).isSameAs(modifier);
        assertThat(version.getVersionType()).isEqualTo("DELETE");
        assertThat(version.getOriginalTitle()).isEqualTo("Test Post");
        assertThat(version.getOriginalContents()).isEqualTo("Test Contents");
    }

    // --- Create Post ---

    @Test
    @DisplayName("게시글 생성 성공 - BoardUrl 사용")
    void createPost_withBoardUrl_success() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, List.of(1L, 2L));

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 100L);
            return p;
        });

        Long createdId = postService.createPost(1L, "free", request);

        assertThat(createdId).isEqualTo(100L);
        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(tagAssignmentService).assignTags(postCaptor.capture(), eq(request.getTags()));
        assertThat(postCaptor.getValue().getTitle()).isEqualTo("New Post");
        verify(fileService).attachFilesToPost(List.of(1L, 2L), 1L, 100L, null);
        verify(pointService).addPointIfAbsent(eq(1L), eq(50), anyString(), eq(100L), eq("POST"));
        verify(anonymousReadCacheInvalidator).evictPostRelatedCachesAfterCommit();
        InOrder lockOrder = inOrder(userRepository, boardRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(boardRepository).findByBoardUrlForUpdate("free");
    }

    @Test
    @DisplayName("게시글 생성은 초안 파일 승격을 위해 draftId를 파일 서비스에 전달한다")
    void createPost_withDraftId_passesDraftIdToFileService() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, 55L, List.of(1L));
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Draft").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 55L);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 100L);
            return p;
        });
        when(draftPostRepository.findByDraftIdAndUserForUpdate(55L, user)).thenReturn(Optional.of(existingDraft));

        postService.createPost(1L, "free", request);

        verify(fileService).attachFilesToPost(List.of(1L), 1L, 100L, 55L);
        InOrder inOrder = inOrder(draftPostRepository, fileService);
        inOrder.verify(draftPostRepository).findByDraftIdAndUserForUpdate(55L, user);
        inOrder.verify(fileService).attachFilesToPost(List.of(1L), 1L, 100L, 55L);
        inOrder.verify(fileService).markDraftFilesDeletionPending(55L);
        inOrder.verify(draftPostRepository).delete(existingDraft);
    }

    @Test
    @DisplayName("게시글 생성은 다른 게시판의 초안을 발행하지 않는다")
    void createPost_rejectsDraftFromAnotherBoard() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, 55L, List.of(1L));
        Board otherBoard = Board.builder().boardName("Other").boardUrl("other").build();
        ReflectionTestUtils.setField(otherBoard, "boardId", 2L);
        DraftPost existingDraft = DraftPost.builder().user(user).board(otherBoard).title("Draft").build();

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 100L);
            return saved;
        });
        when(draftPostRepository.findByDraftIdAndUserForUpdate(55L, user)).thenReturn(Optional.of(existingDraft));

        assertThatThrownBy(() -> postService.createPost(1L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(fileService, never()).attachFilesToPost(anyList(), anyLong(), anyLong(), any());
        verify(draftPostRepository, never()).delete(any(DraftPost.class));
    }

    @Test
    @DisplayName("게시글 생성은 예약발행이 보호하는 초안을 발행하지 않는다")
    void createPost_rejectsScheduledDraft() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, 55L, List.of(1L));
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Draft").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 55L);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 100L);
            return saved;
        });
        when(draftPostRepository.findByDraftIdAndUserForUpdate(55L, user)).thenReturn(Optional.of(existingDraft));
        when(scheduledPostRepository.existsByDraftIdAndStatusIn(55L, ScheduledPost.PROTECTED_DRAFT_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> postService.createPost(1L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_PROTECTED);

        verify(fileService, never()).attachFilesToPost(anyList(), anyLong(), anyLong(), any());
        verify(draftPostRepository, never()).delete(any(DraftPost.class));
    }

    @Test
    @DisplayName("예약발행 워커는 자신이 발행 중인 보호 초안을 사용할 수 있다")
    void createScheduledPost_allowsClaimedScheduledDraft() {
        PostCreateRequest request = new PostCreateRequest(null, "Scheduled Post", "Contents", Collections.emptyList(),
                false, false, false, false, 55L, List.of(1L));
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Draft").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 55L);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 100L);
            return saved;
        });
        when(draftPostRepository.findByDraftIdAndUserForUpdate(55L, user)).thenReturn(Optional.of(existingDraft));
        when(scheduledPostRepository.existsByDraftIdAndStatusIn(55L, ScheduledPost.PROTECTED_DRAFT_STATUSES))
                .thenReturn(true);
        when(scheduledPostRepository.existsByScheduledPostIdAndDraftIdAndStatus(
                77L, 55L, ScheduledPost.STATUS_PUBLISHING)).thenReturn(true);

        PostCreateResponse response = postCommandService.createScheduledPostWithResponse(1L, "free", request, 77L);

        assertThat(response.getPostId()).isEqualTo(100L);
        verify(fileService).attachFilesToPost(List.of(1L), 1L, 100L, 55L);
        verify(draftPostRepository).delete(existingDraft);
    }

    @Test
    @DisplayName("게시글 발행은 첨부 파일이 없어도 사용한 초안을 삭제한다")
    void createPost_withDraftIdWithoutFiles_deletesDraft() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, 55L, Collections.emptyList());
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Draft").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 55L);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 100L);
            return p;
        });
        when(draftPostRepository.findByDraftIdAndUserForUpdate(55L, user)).thenReturn(Optional.of(existingDraft));

        postService.createPost(1L, "free", request);

        verify(fileService, never()).attachFilesToPost(anyList(), anyLong(), anyLong(), any());
        verify(fileService).markDraftFilesDeletionPending(55L);
        verify(draftPostRepository).delete(existingDraft);
    }

    @Test
    @DisplayName("게시글 생성은 본문 HTML을 허용 목록 기반으로 정제해 저장한다")
    void createPost_sanitizesPostHtmlContent() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post",
                "<p onclick=\"alert(1)\">Safe</p><a href=\"javascript:alert(1)\">bad</a>"
                        + "<img src=\"/api/v1/files/1\" alt=\"ok\"><script>alert(1)</script>",
                Collections.emptyList(), false, false, false, false, null);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 100L);
            return saved;
        });

        postService.createPost(1L, "free", request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getContents()).contains("<p>Safe</p>");
        assertThat(postCaptor.getValue().getContents()).contains("src=\"/api/v1/files/1\"");
        assertThat(postCaptor.getValue().getContents()).doesNotContain("onclick");
        assertThat(postCaptor.getValue().getContents()).doesNotContain("javascript:");
        assertThat(postCaptor.getValue().getContents()).doesNotContain("<script");
    }

    @Test
    @DisplayName("게시글 생성은 서비스 경계에서 HTML 제목을 거부한다")
    void createPost_htmlTitle_rejectedBeforeSideEffects() {
        PostCreateRequest request = new PostCreateRequest(null, "<b>New Post</b>", "New Contents",
                Collections.emptyList(), false, false, false, false, List.of(1L));

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> postService.createPost(1L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(postRepository, never()).save(any(Post.class));
        verify(tagAssignmentService, never()).assignTags(any(Post.class), anyList());
        verify(fileService, never()).attachFilesToPost(anyList(), anyLong(), anyLong(), any());
        verify(pointService, never()).addPointIfAbsent(anyLong(), anyInt(), anyString(), anyLong(), anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("createPost rejects invalid tags before persistence")
    void createPost_invalidTags_rejectedBeforePersistenceAndSideEffects() {
        List<String> invalidTags = List.of("valid", " ");
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents",
                invalidTags, false, false, false, false, List.of(1L));

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE))
                .when(tagAssignmentService).validateTags(invalidTags);

        assertThatThrownBy(() -> postService.createPost(1L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postRepository, never()).save(any(Post.class));
        verify(tagAssignmentService, never()).assignTags(any(Post.class), anyList());
        verify(fileService, never()).attachFilesToPost(anyList(), anyLong(), anyLong(), any());
        verify(pointService, never()).addPointIfAbsent(anyLong(), anyInt(), anyString(), anyLong(), anyString());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("게시글 생성은 null 본문을 빈 문자열로 저장한다")
    void createPost_nullContents_storesEmptyString() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", null, Collections.emptyList(),
                false, false, false, false, null);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 100L);
            return saved;
        });

        postService.createPost(1L, "free", request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getContents()).isEmpty();
    }

    @Test
    @DisplayName("게시글 작성 보상 설정이 잘못되면 기본값으로 지급한다")
    void createPost_invalidRewardConfig_usesDefaultReward() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig("POINT_POST_CREATE_REWARD")).thenReturn("invalid");
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 100L);
            return p;
        });

        postService.createPost(1L, "free", request);

        verify(pointService).addPointIfAbsent(eq(1L), eq(50), anyString(), eq(100L), eq("POST"));
    }

    @Test
    @DisplayName("게시글 작성 보상 설정이 0이면 포인트를 지급하지 않는다")
    void createPost_zeroRewardConfig_skipsPointGrant() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(globalConfigService.getConfig("POINT_POST_CREATE_REWARD")).thenReturn("0");
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 100L);
            return p;
        });

        postService.createPost(1L, "free", request);

        verify(pointService, never()).addPointIfAbsent(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글을 작성할 수 없다")
    void createPost_bannedUser_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.createPost(1L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("활성 MUTE 사용자는 게시글을 작성할 수 없다")
    void createPost_mutedUser_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(user);

        assertThatThrownBy(() -> postService.createPost(1L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    @DisplayName("게시글 생성 후 발행 이벤트를 발행한다")
    void createPost_publishesPostPublishedEvent() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "postId", 200L);
            return saved;
        });

        postService.createPost(1L, "free", request);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PostPublishedEvent.class);
        PostPublishedEvent event = (PostPublishedEvent) eventCaptor.getValue();
        assertThat(event.postId()).isEqualTo(200L);
        assertThat(event.boardId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("게시글 생성 성공 - 카테고리 포함")
    void createPost_withCategory_success() {
        PostCreateRequest request = new PostCreateRequest(1L, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postService.createPost(1L, 1L, request);

        verify(boardCategoryRepository).findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true);
    }

    @Test
    @DisplayName("createPost validates write role with the requested category only")
    void createPost_withCategory_skipsDefaultCategoryPermission() {
        PostCreateRequest request = new PostCreateRequest(1L, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.createPost(1L, 1L, request);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getCategory()).isEqualTo(category);
        verify(boardCategoryRepository, never()).findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(1L, true);
    }

    @Test
    @DisplayName("createPost keeps notice permission before category lookup")
    void createPost_noticeForbidden_beforeCategoryLookup() {
        PostCreateRequest request = new PostCreateRequest(99L, "Notice", "Contents", Collections.emptyList(), true,
                false, false, false, null);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(boardCategoryRepository, never()).findByCategoryIdAndBoard_BoardIdAndIsActive(anyLong(), anyLong(),
                anyBoolean());
    }

    @Test
    @DisplayName("createPost fails when category belongs to another board")
    void createPost_categoryBoardMismatch_notFound() {
        PostCreateRequest request = new PostCreateRequest(2L, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 생성 실패 - 공지사항 권한 없음")
    void createPost_notice_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "Notice", "Contents", Collections.emptyList(), true,
                false, false, false, null);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("에이전트 게시글 생성 실패 - 이름과 무관하게 기본 카테고리 권한을 만족하지 못하면 작성 불가")
    void createPostAsAgent_generalCategoryPermission_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "Title", "Contents", Collections.emptyList(), false,
                false, false, false, null);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);

        BoardCategory generalCategory = BoardCategory.builder().name("Restricted").board(board)
                .minWriteRole("BOARD_ADMIN")
                .isDefault(true)
                .build();
        ReflectionTestUtils.setField(generalCategory, "categoryId", 10L);

        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 10L);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(agentOwnershipService.resolveOwnedActiveAgent(1L, 10L)).thenReturn(agent);
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(1L, true))
                .thenReturn(List.of(generalCategory));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPostAsAgent(1L, 10L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    void createPostAsAgent_prevalidatedContextWithoutCategoryStillValidatesRequestedCategoryRole() {
        PostCreateRequest request = new PostCreateRequest(10L, "Title", "Contents", Collections.emptyList(), false,
                false, false, false, null);
        BoardCategory restrictedCategory = BoardCategory.builder().name("Restricted").board(board)
                .minWriteRole("BOARD_ADMIN")
                .isDefault(false)
                .build();
        ReflectionTestUtils.setField(restrictedCategory, "categoryId", 10L);

        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(10L, 1L, true))
                .thenReturn(Optional.of(restrictedCategory));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPostAsAgent(
                1L,
                10L,
                request,
                PostCreateContext.agent(agent, board, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(postRepository, never()).save(any(Post.class));
    }

    // --- Read Post ---

    @Test
    @DisplayName("게시글 조회 성공 - ID로 조회")
    void getPostById_success() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isEqualTo(post);
        verify(postRepository).incrementViewCount(1L);
        verify(entityManager).refresh(post);
        verify(viewHistoryRepository).insertIgnore(1L, 1L);
        verify(viewHistoryRepository, never()).touchModifiedAt(1L, 1L);
    }

    @Test
    @DisplayName("게시글 조회 - 기존 읽은 댓글 위치를 덮지 않고 조회 시각만 갱신")
    void getPostById_existingViewHistory_touchesWithoutLoadingLastReadComment() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(0);
        when(viewHistoryRepository.touchModifiedAt(1L, 1L)).thenReturn(1);

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isEqualTo(post);
        verify(viewHistoryRepository).insertIgnore(1L, 1L);
        verify(viewHistoryRepository).touchModifiedAt(1L, 1L);
        verify(viewHistoryRepository, never()).findByUserAndPost(user, post);
    }

    @Test
    @DisplayName("게시글 조회 실패 - 삭제된 게시글")
    void getPostById_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.getPostById(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 조회 실패 - 블라인드 게시글")
    void getPostById_blindedPost() {
        post.blind("reported", LocalDateTime.now());
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.getPostById(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 조회 실패 - 비활성 노드, 권한 없음")
    void getPostById_inactiveBoard_forbidden() {
        ReflectionTestUtils.setField(board, "isActive", false);

        // Mock user who is NOT author, NOT admin, NOT superadmin
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(otherUser, List.of(1L), true))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> postService.getPostById(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND); // Security requirement often masks
                                                                                     // as Not Found
    }

    @Test
    @DisplayName("게시글 목록 조회 - BoardUrl")
    void getPosts_byBoardUrl() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        // currentUserId가 null이므로 userBlockService가 호출되지 않음
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        when(postRepository.findPostListSummariesByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(),
                any(Boolean.class), any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getPosts(" free ", null, null, null, null, Pageable.unpaged());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(boardRepository).findByBoardUrl("free");
        verify(postRepository).findPostListSummariesByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(),
                any(Boolean.class), any(),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("pinnedAt"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 목록 키워드 조회 성공 후 검색 기록 이벤트를 발행한다")
    void getPosts_withKeyword_publishesSearchRecord() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findPostListSummariesByBoardIdAndCategoryId(eq(1L), any(), eq("test"), any(), any(),
                any(Boolean.class), any(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getPosts("free", null, " test ", null, null, Pageable.unpaged());

        verify(searchRecordEventPublisher).publish(null, "test");
    }

    @Test
    @DisplayName("게시글 목록 조회는 음수 최소 좋아요 수를 거부한다")
    void getPosts_rejectsNegativeMinLikes() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.getPosts("free", null, null, -1, null, Pageable.unpaged()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(postRepository, never()).findPostListSummariesByBoardIdAndCategoryId(
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("board post search caps keyword before repository and record event")
    void getPosts_truncatesKeywordBeforeSearchAndRecord() {
        String rawKeyword = "A".repeat(MAX_KEYWORD_LENGTH + 10);
        String canonicalKeyword = "A".repeat(MAX_KEYWORD_LENGTH);
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findPostListSummariesByBoardIdAndCategoryId(eq(1L), any(), eq(canonicalKeyword), any(),
                any(),
                any(Boolean.class), any(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getPosts("free", null, rawKeyword, null, null, Pageable.unpaged());

        verify(postRepository).findPostListSummariesByBoardIdAndCategoryId(eq(1L), any(), eq(canonicalKeyword), any(),
                any(),
                any(Boolean.class), any(), any(Pageable.class));
        verify(searchRecordEventPublisher).publish(null, canonicalKeyword);
    }

    @Test
    @DisplayName("게시글 목록 키워드가 비어 있으면 검색 기록 이벤트를 발행하지 않는다")
    void getPosts_blankKeywordDoesNotPublishSearchRecord() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findPostListSummariesByBoardIdAndCategoryId(eq(1L), any(), isNull(), any(), any(),
                any(Boolean.class), any(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getPosts("free", null, "   ", null, null, Pageable.unpaged());

        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("게시글 목록 조회 - 허용 sort에 안정 정렬을 보강")
    void getPosts_appendsStableSortToAllowedSort() {
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(), any(Boolean.class), any(),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getPosts(1L, null, null, null, null, false,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("likeCount"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(), any(Boolean.class), any(),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("likeCount"),
                Sort.Order.desc("pinnedAt"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("게시글 목록 조회는 양방향 차단 사용자 목록을 전달한다")
    void getPosts_passesEitherDirectionBlockedUserIds() {
        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(List.of(99L));
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), eq(List.of(99L)),
                eq(false), eq(1L), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getPosts(1L, null, null, null, 1L, false, Pageable.unpaged());

        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), eq(List.of(99L)),
                eq(false), eq(1L), any(Pageable.class));
    }

    @Test
    @DisplayName("게시글 목록 조회 실패 - 비활성 노드, 권한 없음")
    void getPosts_inactiveBoard_forbidden() {
        ReflectionTestUtils.setField(board, "isActive", false);
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        // Mock user who is NOT author, NOT admin, NOT superadmin
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> postService.getPosts("free", null, "test", null, 2L, Pageable.unpaged()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
        verify(searchRecordEventPublisher, never()).publish(any(), anyString());
    }

    @Test
    @DisplayName("인기 게시글 조회 - 로그인 사용자")
    void getTrendingPosts_loggedIn() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), anyList(), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Map.of(1L, 10L));

        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());

        List<PostSummary> result = postService.getTrendingPosts(PageRequest.of(0, 10), 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThumbnailUrl()).isEqualTo("/api/v1/files/10/variants/thumbnail");
    }

    @Test
    @DisplayName("인기 게시글 조회는 선택한 기간 기준으로 집계 시점을 계산한다")
    void getTrendingPosts_resolvesSelectedPeriod() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), anyList(), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Collections.emptyMap());
        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());

        postService.getTrendingPosts(PageRequest.of(0, 10), 1L, "7d");

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(postRepository).findTrendingPosts(sinceCaptor.capture(), anyList(), any(Pageable.class));
        assertThat(sinceCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 6, 30, 0, 0));
    }

    @Test
    @DisplayName("Trending post list normalizes unpaged requests")
    void getTrendingPosts_normalizesUnpagedRequest() {
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), isNull(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getTrendingPosts(Pageable.unpaged(), null, "24h");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findTrendingPosts(any(LocalDateTime.class), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().isPaged()).isTrue();
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("랜딩 최신글 조회는 unpaged 요청을 정규화한다")
    void getPublicLandingLatestPosts_normalizesUnpagedRequest() {
        when(postRepository.findPublicLandingLatestPosts(anyString(), isNull(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postService.getPublicLandingLatestPosts(Pageable.unpaged(), null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findPublicLandingLatestPosts(anyString(), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().isPaged()).isTrue();
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("피드 인기글 조회는 과도한 page size를 제한한다")
    void getTrendingFeedPosts_clampsOversizedPageSize() {
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), isNull(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postListReadService.getTrendingFeedPosts(PageRequest.of(2, 1000), null, "24h");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findTrendingPosts(any(LocalDateTime.class), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("피드 랜딩 최신글 조회는 과도한 page size를 제한한다")
    void getPublicLandingLatestFeedPosts_clampsOversizedPageSize() {
        when(postRepository.findPublicLandingLatestPosts(anyString(), isNull(), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        postListReadService.getPublicLandingLatestFeedPosts(PageRequest.of(1, 1000), null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findPublicLandingLatestPosts(anyString(), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("인기 게시글 페이지 조회는 repository count로 정확한 total을 반환한다")
    void getTrendingPostsPage_usesExactRepositoryTotal() {
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), anyList(), anyLong(), anyInt()))
                .thenReturn(List.of(post));
        when(postRepository.countTrendingPosts(any(LocalDateTime.class), anyList())).thenReturn(20L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());

        Page<PostSummary> result = postService.getTrendingPostsPage(PageRequest.of(0, 1), 1L, "24h");

        verify(postRepository).findTrendingPosts(any(LocalDateTime.class), anyList(), eq(0L), eq(1));
        verify(postRepository).countTrendingPosts(any(LocalDateTime.class), anyList());
        assertThat(result.getContent()).extracting(PostSummary::getPostId).containsExactly(1L);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.getTotalElements()).isEqualTo(20);
    }

    @Test
    @DisplayName("인기 게시글 페이지 조회는 원래 offset을 유지한다")
    void getTrendingPostsPage_keepsOriginalOffset() {
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), isNull(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(postRepository.countTrendingPosts(any(LocalDateTime.class), isNull())).thenReturn(0L);

        postService.getTrendingPostsPage(PageRequest.of(1, 10), null, "24h");

        verify(postRepository).findTrendingPosts(any(LocalDateTime.class), isNull(), eq(10L), eq(10));
        verify(postRepository).countTrendingPosts(any(LocalDateTime.class), isNull());
    }

    @Test
    @DisplayName("Trending post page clamps oversized page size")
    void getTrendingPostsPage_clampsOversizedPageSize() {
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), isNull(), anyLong(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(postRepository.countTrendingPosts(any(LocalDateTime.class), isNull())).thenReturn(0L);

        Page<PostSummary> result = postService.getTrendingPostsPage(PageRequest.of(0, 1000), null, "24h");

        verify(postRepository).findTrendingPosts(any(LocalDateTime.class), isNull(), eq(0L), eq(100));
        assertThat(result.getPageable().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Trending post page with missing user returns USER_NOT_FOUND")
    void getTrendingPostsPage_missingUser_throwsUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getTrendingPostsPage(PageRequest.of(0, 10), 99L, "24h"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    // --- Update Post ---

    @Test
    @DisplayName("게시글 수정 성공")
    void updatePost_success() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, List.of(5L));

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Long updatedId = postService.updatePost(1L, 1L, request);

        assertThat(updatedId).isEqualTo(1L);
        assertThat(post.getTitle()).isEqualTo("Updated Title");
        verify(tagAssignmentService).assignTags(post, request.getTags());
        verify(fileService).syncPostFiles(List.of(5L), 1L, 1L, null);
        verify(postVersionRepository).save(any(PostVersion.class));
        verify(anonymousReadCacheInvalidator).evictPostRelatedCachesAfterCommit();
        InOrder lockOrder = inOrder(userRepository, postRepository, boardRepository);
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(postRepository).findBoardIdByPostId(1L);
        lockOrder.verify(boardRepository).findByIdForUpdate(1L);
        lockOrder.verify(postRepository).findByIdWithRelationsForUpdate(1L);
    }

    @Test
    @DisplayName("게시글 수정은 공지 값이 생략되면 기존 공지 상태를 유지한다")
    void updatePost_noticeOmitted_preservesExistingNotice() {
        ReflectionTestUtils.setField(post, "isNotice", true);
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, List.of());

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.updatePost(1L, 1L, request);

        assertThat(post.getIsNotice()).isTrue();
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("보드 관리자는 게시글 수정으로 공지를 해제할 수 있다")
    void updatePost_boardAdminCanUnsetNotice() {
        ReflectionTestUtils.setField(post, "isNotice", true);
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, false, null, List.of(), null, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(true);

        postService.updatePost(1L, 1L, request);

        assertThat(post.getIsNotice()).isFalse();
    }

    @Test
    @DisplayName("보드 관리자가 아니면 게시글 수정으로 공지 상태를 변경할 수 없다")
    void updatePost_nonBoardAdminCannotChangeNotice() {
        ReflectionTestUtils.setField(post, "isNotice", true);
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, false, null, List.of(), null, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        assertThat(post.getIsNotice()).isTrue();
        verify(postVersionRepository, never()).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정은 초안 파일 승격을 위해 draftId를 파일 서비스에 전달한다")
    void updatePost_withDraftId_passesDraftIdToFileService() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, 55L, List.of(5L));
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).originalPost(post).title("Draft").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 55L);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(55L, user)).thenReturn(Optional.of(existingDraft));

        postService.updatePost(1L, 1L, request);

        verify(fileService).syncPostFiles(List.of(5L), 1L, 1L, 55L);
        InOrder inOrder = inOrder(draftPostRepository, fileService);
        inOrder.verify(draftPostRepository).findByDraftIdAndUserForUpdate(55L, user);
        inOrder.verify(fileService).syncPostFiles(List.of(5L), 1L, 1L, 55L);
        inOrder.verify(fileService).markDraftFilesDeletionPending(55L);
        inOrder.verify(draftPostRepository).delete(existingDraft);
    }

    @Test
    @DisplayName("게시글 수정은 본문 HTML을 생성과 같은 정책으로 정제한다")
    void updatePost_sanitizesPostHtmlContent() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title",
                "<h2>Title</h2><p onmouseover=\"alert(1)\">Safe</p><iframe src=\"javascript:alert(1)\"></iframe>",
                Collections.emptyList(), false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.updatePost(1L, 1L, request);

        assertThat(post.getContents()).contains("<h2>Title</h2>");
        assertThat(post.getContents()).contains("<p>Safe</p>");
        assertThat(post.getContents()).doesNotContain("onmouseover");
        assertThat(post.getContents()).doesNotContain("javascript:");
    }

    @Test
    void updatePost_publishesNotificationsOnlyForNewMentions() {
        String originalContents = "<span data-mention-user-id=\"2\">Existing</span>";
        ReflectionTestUtils.setField(post, "contents", originalContents);
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title",
                originalContents + "<span data-mention-user-id=\"3\">Added</span>",
                Collections.emptyList(), false, false, false, null);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.updatePost(1L, 1L, request);

        verify(mentionService).publishNewMentions(
                user,
                null,
                NotificationSourceType.POST,
                1L,
                originalContents,
                post.getContents());
    }

    @Test
    @DisplayName("게시글 수정은 서비스 경계에서 HTML 제목을 거부한다")
    void updatePost_htmlTitle_rejectedBeforeSideEffects() {
        PostUpdateRequest request = new PostUpdateRequest(null, "<b>Updated Title</b>", "Updated Contents",
                Collections.emptyList(), false, false, false, List.of(5L));

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        assertThat(post.getTitle()).isEqualTo("Test Post");
        assertThat(post.getContents()).isEqualTo("Test Contents");
        verify(tagAssignmentService, never()).assignTags(any(Post.class), anyList());
        verify(fileService, never()).syncPostFiles(anyList(), anyLong(), anyLong(), any());
        verify(postVersionRepository, never()).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정은 null 본문을 빈 문자열로 저장한다")
    void updatePost_nullContents_storesEmptyString() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", null,
                Collections.emptyList(), false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.updatePost(1L, 1L, request);

        assertThat(post.getContents()).isEmpty();
        verify(postVersionRepository).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정에서 fileIds가 null이면 파일 연결을 변경하지 않는다")
    void updatePost_fileIdsNull_doesNotSyncFiles() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.updatePost(1L, 1L, request);

        verify(fileService, never()).syncPostFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("게시글 수정에서 빈 fileIds는 모든 게시글 파일 제거 의도로 전달한다")
    void updatePost_emptyFileIds_syncsEmptyTargetState() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, List.of());

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.updatePost(1L, 1L, request);

        verify(fileService).syncPostFiles(List.of(), 1L, 1L, null);
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글을 수정할 수 없다")
    void updatePost_bannedUser_forbidden() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postVersionRepository, never()).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정 실패 - 작성자 아님")
    void updatePost_forbidden() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", null, false, false, false, null);
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        User otherUser = User.builder().loginId("other").displayName("Other User").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> postService.updatePost(2L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("updatePost fails when category belongs to another board")
    void updatePost_categoryBoardMismatch_notFound() {
        PostUpdateRequest request = new PostUpdateRequest(2L, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    // --- Delete Post ---

    @Test
    @DisplayName("게시글 삭제 성공")
    void deletePost_success() {
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user, List.of("EARN", "REWARD_REVERSAL"), "POST", 1L))
                .thenReturn(50L);

        postService.deletePost(1L, 1L);

        assertThat(post.getIsDeleted()).isTrue();
        verify(tagAssignmentService).clearTags(post);
        assertDeleteVersionRecorded(user);
        verify(fileService).markPostContentFilesDeletionPending(1L);
        verify(pointService).reverseRewardPoint(eq(1L), eq(50), anyString(), eq(1L), eq("POST"));
        verify(semanticSearchEventPublisher).publish("POST", 1L, SemanticSearchIndexAction.DELETE);
        verify(anonymousReadCacheInvalidator).evictPostRelatedCachesAfterCommit();
        verify(globalConfigService, never()).getConfig("POINT_POST_CREATE_REWARD");
    }

    @Test
    @DisplayName("에이전트 게시글 삭제도 공통 삭제 후처리를 실행한다")
    void deleteAgentOwnedPost_appliesCommonDeleteSideEffects() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("hash")
                .name("agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);
        ReflectionTestUtils.setField(post, "agent", agent);
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user, List.of("EARN", "REWARD_REVERSAL"), "POST", 1L))
                .thenReturn(50L);

        postCommandService.deleteAgentOwnedPost(post, 7L, user);

        assertThat(post.getIsDeleted()).isTrue();
        verify(tagAssignmentService).clearTags(post);
        assertDeleteVersionRecorded(user);
        verify(fileService).markPostContentFilesDeletionPending(1L);
        verify(pointService).reverseRewardPoint(eq(1L), eq(50), anyString(), eq(1L), eq("POST"));
        verify(semanticSearchEventPublisher).publish("POST", 1L, SemanticSearchIndexAction.DELETE);
    }

    @Test
    @DisplayName("게시글 삭제 시 적립 이력이 없으면 포인트를 차감하지 않는다")
    void deletePost_withoutRewardHistory_skipsPointRollback() {
        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user, List.of("EARN", "REWARD_REVERSAL"), "POST", 1L))
                .thenReturn(0L);

        postService.deletePost(1L, 1L);

        assertThat(post.getIsDeleted()).isTrue();
        verify(tagAssignmentService).clearTags(post);
        verify(pointService, never()).reverseRewardPoint(anyLong(), anyInt(), anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글을 삭제할 수 없다")
    void deletePost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.deletePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(tagAssignmentService, never()).clearTags(any(Post.class));
    }

    // --- Likes ---

    @Test
    @DisplayName("좋아요 성공")
    void likePost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class)))
                .thenReturn(PostLike.builder().user(user).post(post).build());
        when(postRepository.incrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(1);

        int likeCount = postService.likePost(1L, 1L);

        verify(postLikeRepository).saveAndFlush(any(PostLike.class));
        verify(postRepository).incrementLikeCount(1L);
        verify(anonymousReadCacheInvalidator).evictPostEngagementCachesAfterCommit("free");
        verify(userRepository).findById(1L);
        assertThat(likeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글 좋아요를 할 수 없다")
    void likePost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postLikeRepository, never()).saveAndFlush(any(PostLike.class));
    }

    @Test
    void likePost_mutedUser_forbiddenBeforeLoadingPost() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(user);

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postRepository, never()).findByIdWithRelations(anyLong());
        verify(postLikeRepository, never()).saveAndFlush(any(PostLike.class));
    }

    @Test
    @DisplayName("좋아요 실패 - 이미 좋아요 함")
    void likePost_alreadyLiked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_LIKED);
    }

    @Test
    @DisplayName("게시글 좋아요는 카운터 갱신 실패 시 POST_NOT_FOUND를 반환한다")
    void likePost_likeCountUpdateFails_throwsPostNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.incrementLikeCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(eventPublisher, never()).publishEvent(any());
        verify(postRepository, never()).findLikeCountByPostId(anyLong());
    }

    @Test
    @DisplayName("agent actor가 현재 사용자 소유가 아니면 게시글 좋아요를 거부한다")
    void likePost_withForeignAgent_forbidden() {
        User otherUser = User.builder().loginId("other").build();
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

        assertThatThrownBy(() -> postService.likePost(1L, 10L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("resolved foreign agent is forbidden")
    void likePost_withResolvedForeignAgent_forbidden() {
        User otherUser = User.builder().loginId("other").build();
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

        assertThatThrownBy(() -> postService.likePost(1L, foreignAgent, post))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(postLikeRepository, never()).saveAndFlush(any(PostLike.class));
    }

    @Test
    @DisplayName("좋아요 취소 성공")
    void unlikePost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.deleteByUserIdAndPostId(1L, 1L)).thenReturn(1);
        when(postRepository.decrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(0);

        int likeCount = postService.unlikePost(1L, 1L);

        verify(postLikeRepository).deleteByUserIdAndPostId(1L, 1L);
        verify(postRepository).findByIdWithRelations(1L);
        verify(postRepository).decrementLikeCount(1L);
        verify(anonymousReadCacheInvalidator).evictPostEngagementCachesAfterCommit("free");
        verify(userRepository).findById(1L);
        assertThat(likeCount).isZero();
    }

    @Test
    @DisplayName("좋아요하지 않은 게시글 취소는 NOT_LIKED를 반환한다")
    void unlikePost_notLiked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.deleteByUserIdAndPostId(1L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_LIKED);

        verify(postRepository).findByIdWithRelations(1L);
        verify(postRepository, never()).decrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("게시글 좋아요 취소는 카운터 갱신 실패 시 POST_NOT_FOUND를 반환한다")
    void unlikePost_likeCountUpdateFails_throwsPostNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.deleteByUserIdAndPostId(1L, 1L)).thenReturn(1);
        when(postRepository.decrementLikeCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(postRepository, never()).findLikeCountByPostId(anyLong());
    }

    @Test
    @DisplayName("like on agent-authored post notifies agent owner")
    void likePost_agentPost_notifiesAgentOwner() {
        User actorUser = User.builder().displayName("actor").build();
        ReflectionTestUtils.setField(actorUser, "userId", 1L);

        User agentOwner = User.builder().displayName("agent-owner").build();
        ReflectionTestUtils.setField(agentOwner, "userId", 3L);

        User legacyAuthor = User.builder().displayName("legacy-author").build();
        ReflectionTestUtils.setField(legacyAuthor, "userId", 4L);

        Agent targetAgent = Agent.builder()
                .user(agentOwner)
                .agentTokenHash("hash")
                .name("target-agent")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(targetAgent, "agentId", 20L);
        ReflectionTestUtils.setField(post, "user", legacyAuthor);
        ReflectionTestUtils.setField(post, "agent", targetAgent);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actorUser));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class)))
                .thenReturn(PostLike.builder().user(actorUser).post(post).build());
        when(postRepository.incrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(1);

        postService.likePost(1L, 1L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getUserToNotify()).isSameAs(agentOwner);
        assertThat(notificationEvent.getActor()).isSameAs(actorUser);
        assertThat(notificationEvent.getActorAgent()).isNull();
        assertThat(notificationEvent.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(notificationEvent.getSourceId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글 좋아요를 취소할 수 없다")
    void unlikePost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postLikeRepository, never()).deleteByUserIdAndPostId(anyLong(), anyLong());
    }

    @Test
    void unlikePost_mutedUser_forbiddenBeforeLoadingPost() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotMuted(user);

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postRepository, never()).findByIdWithRelations(anyLong());
        verify(postLikeRepository, never()).deleteByUserIdAndPostId(anyLong(), anyLong());
    }

    // --- Scraps ---

    @Test
    @DisplayName("스크랩 성공")
    void scrapPost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.scrapPost(1L, 1L, "My Scrap");

        verify(scrapRepository).saveAndFlush(any(Scrap.class));
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("스크랩 메모는 앞뒤 공백을 제거해 저장한다")
    void scrapPost_trimsRemark() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.scrapPost(1L, 1L, "  My Scrap  ");

        ArgumentCaptor<Scrap> scrapCaptor = ArgumentCaptor.forClass(Scrap.class);
        verify(scrapRepository).saveAndFlush(scrapCaptor.capture());
        assertThat(scrapCaptor.getValue().getRemark()).isEqualTo("My Scrap");
    }

    @Test
    @DisplayName("스크랩 메모가 255자를 초과하면 저장하지 않는다")
    void scrapPost_longRemark_throwsInvalidInput() {
        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "a".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(scrapRepository, never()).saveAndFlush(any(Scrap.class));
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글을 스크랩할 수 없다")
    void scrapPost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "My Scrap"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(scrapRepository, never()).saveAndFlush(any(Scrap.class));
    }

    @Test
    @DisplayName("스크랩 취소 성공")
    void unscrapPost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.deleteByUser_UserIdAndPost_PostId(1L, 1L)).thenReturn(1L);

        postService.unscrapPost(1L, 1L);

        verify(postRepository).findByIdWithRelations(1L);
        verify(scrapRepository).deleteByUser_UserIdAndPost_PostId(1L, 1L);
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글 스크랩을 취소할 수 없다")
    void unscrapPost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.unscrapPost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(scrapRepository, never()).deleteByUser_UserIdAndPost_PostId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("duplicate scrap is normalized to ALREADY_SCRAPED")
    void scrapPost_duplicate_throwsAlreadyScraped() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "My Scrap"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_SCRAPED);
        verify(scrapRepository, never()).existsById(any(ScrapId.class));
    }

    @Test
    @DisplayName("scrap persistence conflict is normalized without requery")
    void scrapPost_dataIntegrityViolation_throwsAlreadyScrapedWithoutRequery() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class)))
                .thenThrow(new DataIntegrityViolationException("other"));

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "My Scrap"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_SCRAPED);
        verify(scrapRepository, never()).existsById(any(ScrapId.class));
    }

    @Test
    @DisplayName("unscrap returns NOT_SCRAPED when no row is deleted")
    void unscrapPost_notScrapped() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.deleteByUser_UserIdAndPost_PostId(1L, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> postService.unscrapPost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_SCRAPED);

        verify(postRepository).findByIdWithRelations(1L);
    }

    @Test
    @DisplayName("내 스크랩 조회")
    void getMyScraps_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Scrap scrap = Scrap.builder()
                .user(user)
                .post(post)
                .remark("bookmark")
                .build();
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scrap), PageRequest.of(0, 10), 1));

        ScrapListResponse response = postService.getMyScraps(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getRemark()).isEqualTo("bookmark");
        assertThat(response.getContent().getFirst().getPost().getTitle()).isEqualTo("Test Post");
        assertThat(response.getContent().getFirst().getPost().getBoardName()).isEqualTo("Test Board");
        assertThat(response.getContent().getFirst().getPost().getAuthor().getAuthorType()).isEqualTo("USER");
        assertThat(response.getContent().getFirst().getPost().getAuthor().getAgentId()).isNull();
        verify(scrapRepository).findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class));
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("내 스크랩 조회는 에이전트 작성자 정보를 반환한다")
    void getMyScraps_agentAuthoredPost_returnsAgentAuthor() {
        Agent agent = Agent.builder()
                .user(user)
                .agentTokenHash("agent-token")
                .name("Helper Agent")
                .description("description")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(agent, "agentId", 7L);
        Post agentPost = Post.builder()
                .board(board)
                .user(user)
                .agent(agent)
                .title("Agent Post")
                .contents("contents")
                .build();
        ReflectionTestUtils.setField(agentPost, "postId", 77L);
        Scrap scrap = Scrap.builder()
                .user(user)
                .post(agentPost)
                .remark("agent bookmark")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scrap), PageRequest.of(0, 10), 1));

        ScrapListResponse response = postService.getMyScraps(1L, PageRequest.of(0, 10));

        ScrapListResponse.AuthorInfo author = response.getContent().getFirst().getPost().getAuthor();
        assertThat(author.getUserId()).isEqualTo(1L);
        assertThat(author.getAgentId()).isEqualTo(7L);
        assertThat(author.getAuthorType()).isEqualTo("AGENT");
        assertThat(author.getDisplayName()).isEqualTo("Helper Agent");
        assertThat(author.getProfileImageUrl()).isNull();
    }

    @Test
    @DisplayName("내 스크랩 조회는 양방향 차단 작성자를 repository 필터로 전달한다")
    void getMyScraps_passesEitherDirectionBlockedAuthors() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(99L));
        when(scrapRepository.findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(false),
                eq(List.of(99L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(5)));

        ScrapListResponse response = postService.getMyScraps(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).isEmpty();
        verify(scrapRepository).findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(false),
                eq(List.of(99L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class));
    }

    @Test
    @DisplayName("스크랩 목록 조회 - pageable 정규화")
    void getMyScraps_normalizesPageable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(5)));

        postService.getMyScraps(1L, PageRequest.of(2, 1000, Sort.by(Sort.Order.asc("createdAt"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(scrapRepository).findPageByUserWithPostDetails(
                eq(user),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("post.postId")));
    }

    @Test
    @DisplayName("스크랩 목록 검색은 keyword 전용 쿼리에 LIKE 패턴을 전달한다")
    void getMyScraps_withKeyword_usesKeywordQuery() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.findPageByUserWithPostDetailsByKeyword(
                eq(user),
                isNull(),
                eq("%test%"),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        ScrapListResponse response = postService.getMyScraps(1L, null, "Test", PageRequest.of(0, 10));

        assertThat(response.getContent()).isEmpty();
        verify(scrapRepository).findPageByUserWithPostDetailsByKeyword(
                eq(user),
                isNull(),
                eq("%test%"),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class));
    }

    @Test
    @DisplayName("스크랩 검색 LIKE 패턴은 이스케이프 문자와 와일드카드를 이스케이프한다")
    void getMyScraps_withSpecialKeyword_escapesLikeMetacharacters() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.findPageByUserWithPostDetailsByKeyword(
                eq(user),
                isNull(),
                eq("%!%a!_!!%"),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(7)));

        postService.getMyScraps(1L, null, "%A_!", PageRequest.of(0, 10));

        verify(scrapRepository).findPageByUserWithPostDetailsByKeyword(
                eq(user),
                isNull(),
                eq("%!%a!_!!%"),
                eq(false),
                eq(true),
                eq(NO_BLOCKED_USER_IDS),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class));
    }

    // --- Drafts ---

    @Test
    @DisplayName("초안 저장 - 신규")
    void saveDraftPost_new() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> {
            DraftPost draftPost = i.getArgument(0);
            ReflectionTestUtils.setField(draftPost, "draftId", 10L);
            return draftPost;
        });

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getTitle()).isEqualTo("Draft Title");
        assertThat(draft.getContents()).isEqualTo("Draft Content");
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 10L);
    }

    @Test
    @DisplayName("임시글 저장은 게시글 본문 정책으로 HTML을 정규화한다")
    void saveDraftPost_sanitizesContents() {
        PostDraftRequest request = new PostDraftRequest(
                null,
                "free",
                "Draft Title",
                "<p style=\"color:red; background-image:url(javascript:alert(1))\">Draft</p><script>alert(1)</script>",
                null);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> {
            DraftPost draftPost = i.getArgument(0);
            ReflectionTestUtils.setField(draftPost, "draftId", 10L);
            return draftPost;
        });

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getContents()).isEqualTo("<p style=\"color: red\">Draft</p>");
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 10L);
    }

    @Test
    @DisplayName("초안 저장 시 확장 필드와 파일 연결 정보를 함께 보존한다")
    void saveDraftPost_persistsExtendedFields() {
        Post originalPost = Post.builder().title("Original").board(board).category(category).user(user).build();
        ReflectionTestUtils.setField(originalPost, "postId", 77L);
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft Title")
                .contents("Draft Content")
                .categoryId(1L)
                .tags(List.of("tag-a", "tag-b"))
                .isNotice(false)
                .isNsfw(true)
                .isSpoiler(true)
                .isSecret(true)
                .fileIds(List.of(11L, 12L))
                .originalPostId(77L)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.findById(77L)).thenReturn(Optional.of(originalPost));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> {
            DraftPost draftPost = i.getArgument(0);
            ReflectionTestUtils.setField(draftPost, "draftId", 22L);
            return draftPost;
        });

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getCategoryId()).isEqualTo(1L);
        assertThat(draft.getTags()).containsExactly("tag-a", "tag-b");
        assertThat(draft.isNsfw()).isTrue();
        assertThat(draft.isSpoiler()).isTrue();
        assertThat(draft.isSecret()).isTrue();
        assertThat(draft.getFileIds()).containsExactly(11L, 12L);
        assertThat(draft.getOriginalPostId()).isEqualTo(77L);
        verify(fileService).syncDraftFiles(List.of(11L, 12L), 1L, 22L);
    }

    @Test
    @DisplayName("활성 BAN 사용자는 초안을 저장할 수 없다")
    void saveDraftPost_bannedUser_forbidden() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("MUTE 사용자는 초안을 저장할 수 없다")
    void saveDraftPost_mutedUser_forbidden() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.FORBIDDEN)).when(sanctionService).validateNotMuted(user);

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(boardRepository, never()).findByBoardUrl(anyString());
        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("게시판 관리자가 아니면 공지 초안을 저장할 수 없다")
    void saveDraftPost_noticeWithoutBoardAdmin_forbidden() {
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Notice Draft")
                .contents("Content")
                .isNotice(true)
                .build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("게시판 관리자는 공지 초안을 저장할 수 있다")
    void saveDraftPost_noticeWithBoardAdmin_success() {
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Notice Draft")
                .contents("Content")
                .isNotice(true)
                .fileIds(Collections.emptyList())
                .build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(true);
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(invocation -> {
            DraftPost saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "draftId", 24L);
            return saved;
        });

        DraftResponse response = postService.saveDraftPost(1L, request);

        assertThat(response.isNotice()).isTrue();
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 24L);
    }

    @Test
    @DisplayName("초안 저장 - 수정")
    void saveDraftPost_update() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        LocalDateTime version = LocalDateTime.of(2025, 1, 2, 12, 0, 0, 123_456_000);
        ReflectionTestUtils.setField(existingDraft, "modifiedAt", version);
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("New Title")
                .contents("New Content")
                .fileIds(Collections.emptyList())
                .updatedAt(version)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getTitle()).isEqualTo("New Title");
        assertThat(draft.getContents()).isEqualTo("New Content");
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 10L);
    }

    @Test
    @DisplayName("예약 발행이 참조 중인 초안은 수정할 수 없다")
    void saveDraftPost_scheduledDraftUpdateRejected() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        LocalDateTime version = LocalDateTime.of(2025, 1, 2, 12, 0);
        ReflectionTestUtils.setField(existingDraft, "modifiedAt", version);
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("Changed after scheduling")
                .contents("Changed contents")
                .fileIds(Collections.emptyList())
                .updatedAt(version)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));
        when(scheduledPostRepository.existsByDraftIdAndStatusIn(10L, ScheduledPost.PROTECTED_DRAFT_STATUSES))
                .thenReturn(true);

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_PROTECTED);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("같은 clientDraftKey와 동일한 내용으로 신규 저장을 재시도하면 기존 초안을 반환한다")
    void saveDraftPost_reusesClientDraftKey() {
        DraftPost existingDraft = DraftPost.builder()
                .user(user)
                .board(board)
                .clientDraftKey("client-draft-key-1234")
                .title("First attempt")
                .contents("")
                .build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        ReflectionTestUtils.setField(existingDraft, "version", 2L);
        PostDraftRequest request = PostDraftRequest.builder()
                .clientDraftKey("client-draft-key-1234")
                .boardUrl("free")
                .title("First attempt")
                .fileIds(Collections.emptyList())
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByUserAndClientDraftKeyForUpdate(user, "client-draft-key-1234"))
                .thenReturn(Optional.of(existingDraft));
        when(draftPostRepository.saveAndFlush(existingDraft)).thenReturn(existingDraft);

        DraftResponse response = postService.saveDraftPost(1L, request);

        assertThat(response.getDraftId()).isEqualTo(10L);
        assertThat(response.getClientDraftKey()).isEqualTo("client-draft-key-1234");
        assertThat(response.getVersion()).isEqualTo(2L);
        assertThat(response.getTitle()).isEqualTo("First attempt");
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 10L);
    }

    @Test
    @DisplayName("같은 clientDraftKey라도 내용이 다르면 오래된 신규 저장 재시도를 거부한다")
    void saveDraftPost_rejectsChangedClientDraftKeyRetry() {
        DraftPost existingDraft = DraftPost.builder()
                .user(user)
                .board(board)
                .clientDraftKey("client-draft-key-1234")
                .title("Latest title")
                .contents("Latest contents")
                .build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        ReflectionTestUtils.setField(existingDraft, "version", 2L);
        PostDraftRequest request = PostDraftRequest.builder()
                .clientDraftKey("client-draft-key-1234")
                .boardUrl("free")
                .title("Stale title")
                .contents("Stale contents")
                .fileIds(Collections.emptyList())
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByUserAndClientDraftKeyForUpdate(user, "client-draft-key-1234"))
                .thenReturn(Optional.of(existingDraft));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_OUTDATED);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("숫자형 초안 버전이 다르면 수정 저장을 거부한다")
    void saveDraftPost_rejectsMismatchedEntityVersion() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        ReflectionTestUtils.setField(existingDraft, "version", 4L);
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .version(3L)
                .boardUrl("free")
                .title("Stale payload")
                .contents("Stale contents")
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_OUTDATED);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("임시글 수정도 게시글 본문 정책으로 HTML을 정규화한다")
    void saveDraftPost_updateSanitizesContents() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        LocalDateTime version = LocalDateTime.of(2025, 1, 2, 12, 0, 0, 123_456_000);
        ReflectionTestUtils.setField(existingDraft, "modifiedAt", version);
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("New Title")
                .contents("<iframe src=\"https://evil.example/embed/1\"></iframe><strong>Safe</strong>")
                .fileIds(Collections.emptyList())
                .updatedAt(version)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getContents()).isEqualTo("<strong>Safe</strong>");
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 10L);
    }

    @Test
    @DisplayName("draft original post must belong to current user")
    void saveDraftPost_originalPostOtherAuthor_forbidden() {
        User otherAuthor = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherAuthor, "userId", 2L);
        Post originalPost = Post.builder()
                .title("Original")
                .contents("Content")
                .board(board)
                .category(category)
                .user(otherAuthor)
                .build();
        ReflectionTestUtils.setField(originalPost, "postId", 77L);
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft")
                .contents("Content")
                .categoryId(1L)
                .originalPostId(77L)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.findById(77L)).thenReturn(Optional.of(originalPost));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("draft original post must not be deleted")
    void saveDraftPost_originalPostDeleted_notFound() {
        Post originalPost = Post.builder()
                .title("Original")
                .contents("Content")
                .board(board)
                .category(category)
                .user(user)
                .build();
        ReflectionTestUtils.setField(originalPost, "postId", 77L);
        originalPost.deletePost();
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft")
                .contents("Content")
                .categoryId(1L)
                .originalPostId(77L)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.findById(77L)).thenReturn(Optional.of(originalPost));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("draft original post must match requested board")
    void saveDraftPost_originalPostBoardMismatch_invalidInput() {
        Board otherBoard = Board.builder().boardName("Other").creator(user).build();
        ReflectionTestUtils.setField(otherBoard, "boardId", 2L);
        ReflectionTestUtils.setField(otherBoard, "isActive", true);
        Post originalPost = Post.builder()
                .title("Original")
                .contents("Content")
                .board(otherBoard)
                .user(user)
                .build();
        ReflectionTestUtils.setField(originalPost, "postId", 77L);
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft")
                .contents("Content")
                .originalPostId(77L)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findById(77L)).thenReturn(Optional.of(originalPost));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("draft update also validates original post ownership")
    void saveDraftPost_updateOriginalPostOtherAuthor_forbidden() {
        User otherAuthor = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherAuthor, "userId", 2L);
        Post originalPost = Post.builder()
                .title("Original")
                .contents("Content")
                .board(board)
                .category(category)
                .user(otherAuthor)
                .build();
        ReflectionTestUtils.setField(originalPost, "postId", 77L);
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("Draft")
                .contents("Content")
                .categoryId(1L)
                .originalPostId(77L)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.findById(77L)).thenReturn(Optional.of(originalPost));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("더 오래된 초안 타임스탬프로 저장하면 충돌을 반환한다")
    void saveDraftPost_rejectsOutdatedRequest() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "modifiedAt", LocalDateTime.of(2025, 1, 2, 12, 0));
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("New Title")
                .contents("New Content")
                .updatedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_OUTDATED);

        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("초안 수정은 DB 마이크로초 정밀도에서 같은 버전을 허용한다")
    void saveDraftPost_acceptsMatchingMicrosecondVersion() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "draftId", 10L);
        ReflectionTestUtils.setField(existingDraft, "modifiedAt",
                LocalDateTime.of(2025, 1, 2, 12, 0, 0, 987_654_321));
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("New Title")
                .contents("New Content")
                .fileIds(Collections.emptyList())
                .updatedAt(LocalDateTime.of(2025, 1, 2, 12, 0, 0, 987_654_000))
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getTitle()).isEqualTo("New Title");
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 10L);
    }

    @Test
    @DisplayName("기존 초안 수정은 updatedAt이 없으면 충돌을 반환한다")
    void saveDraftPost_updateWithoutVersion_rejected() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "modifiedAt", LocalDateTime.of(2025, 1, 2, 12, 0));
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("New Title")
                .contents("New Content")
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_OUTDATED);

        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("현재 버전보다 미래인 updatedAt도 충돌을 반환한다")
    void saveDraftPost_futureVersion_rejected() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        ReflectionTestUtils.setField(existingDraft, "modifiedAt", LocalDateTime.of(2025, 1, 2, 12, 0));
        PostDraftRequest request = PostDraftRequest.builder()
                .draftId(10L)
                .boardUrl("free")
                .title("New Title")
                .contents("New Content")
                .updatedAt(LocalDateTime.of(2025, 1, 2, 12, 0, 1))
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_OUTDATED);

        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("draft save requires writable board")
    void saveDraftPost_requiresWritableBoard() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        ReflectionTestUtils.setField(board, "isPublic", false);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("기존 초안도 보드가 더 이상 쓰기 불가하면 수정 저장을 차단한다")
    void saveDraftPost_updateRequiresWritableBoard() {
        PostDraftRequest request = new PostDraftRequest(10L, "free", "New Title", "New Content", null);
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        ReflectionTestUtils.setField(board, "isPublic", false);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("임시저장 실패 - 기본 카테고리 작성 권한을 만족하지 못하면 저장 불가")
    void saveDraftPost_defaultCategoryWriteRole_forbidden() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        BoardCategory generalCategory = BoardCategory.builder()
                .name("Restricted")
                .board(board)
                .minWriteRole("BOARD_ADMIN")
                .isDefault(true)
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(1L, true))
                .thenReturn(List.of(generalCategory));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("임시저장 실패 - 선택 카테고리 작성 권한을 만족하지 못하면 저장 불가")
    void saveDraftPost_categoryWriteRole_forbidden() {
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft Title")
                .contents("Draft Content")
                .categoryId(2L)
                .build();
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        BoardCategory adminOnlyCategory = BoardCategory.builder()
                .name("Admin Only")
                .board(board)
                .minWriteRole("BOARD_ADMIN")
                .build();

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.of(adminOnlyCategory));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
        verify(fileService, never()).syncDraftFiles(any(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("임시저장은 선택 카테고리가 있으면 기본 카테고리 권한을 요구하지 않는다")
    void saveDraftPost_explicitCategorySkipsDefaultCategoryWriteRole() {
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft Title")
                .contents("Draft Content")
                .categoryId(2L)
                .fileIds(Collections.emptyList())
                .build();
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        BoardCategory selectedCategory = BoardCategory.builder()
                .name("General")
                .board(board)
                .minWriteRole("USER")
                .build();
        ReflectionTestUtils.setField(selectedCategory, "categoryId", 2L);

        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.of(selectedCategory));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> {
            DraftPost draftPost = i.getArgument(0);
            ReflectionTestUtils.setField(draftPost, "draftId", 23L);
            return draftPost;
        });

        DraftResponse draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getCategoryId()).isEqualTo(2L);
        verify(boardCategoryRepository, never()).findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), anyBoolean());
        verify(fileService).syncDraftFiles(Collections.emptyList(), 1L, 23L);
    }

    @Test
    @DisplayName("초안 삭제")
    void deleteDraftPost_success() {
        DraftPost existingDraft = DraftPost.builder().user(user).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByDraftIdAndUserForUpdate(10L, user)).thenReturn(Optional.of(existingDraft));

        postService.deleteDraftPost(1L, 10L);

        verify(fileService).markDraftFilesDeletionPending(10L);
        verify(draftPostRepository).delete(existingDraft);
    }

    @Test
    @DisplayName("활성 BAN 사용자는 초안을 삭제할 수 없다")
    void deleteDraftPost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.deleteDraftPost(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(draftPostRepository, never()).delete(any(DraftPost.class));
    }

    // --- View History ---

    @Test
    @DisplayName("조회 기록 업데이트 - 신규")
    void updateViewHistory_new() {
        ViewHistoryRequest request = new ViewHistoryRequest(100L, 5000L); // commentId, duration
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ViewHistory(user, post)));
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);
        when(commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(100L, 1L))
                .thenReturn(Optional.of(Comment.builder().post(post).build()));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).insertIgnore(1L, 1L);
    }

    @Test
    @DisplayName("조회 기록 업데이트 - 중복 insert 예외 시 기존 row를 재사용")
    void updateViewHistory_duplicateViewHistory_reusesExistingRow() {
        ViewHistoryRequest request = new ViewHistoryRequest(100L, 5000L);
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(0);
        when(commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(100L, 1L))
                .thenReturn(Optional.of(Comment.builder().post(post).build()));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).insertIgnore(1L, 1L);
        verify(viewHistoryRepository, times(2)).findByUserAndPostForUpdate(1L, 1L);
    }

    @Test
    @DisplayName("최근 본 게시글 조회")
    void getRecentlyViewedPosts() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L),
                eq(false),
                eq(true),
                eq(List.of(-1L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(1L), Pageable.unpaged(), 1));
        when(postRepository.findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(List.of(1L))).thenReturn(List.of(post));

        Page<PostSummary> result = postInteractionService.getRecentlyViewedPosts(1L, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPostId()).isEqualTo(1L);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(postRepository).findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(List.of(1L));
        verify(viewHistoryRepository).findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L),
                eq(false),
                eq(true),
                eq(List.of(-1L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class));
    }

    @Test
    @DisplayName("최근 본 게시글 조회는 차단 사용자를 제외한 가시 항목만 조회한다")
    void getRecentlyViewedPosts_excludesBlockedAuthors() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(99L));
        when(viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L),
                eq(false),
                eq(false),
                eq(List.of(99L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(1L), Pageable.unpaged(), 1));
        when(postRepository.findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(List.of(1L))).thenReturn(List.of(post));

        Page<PostSummary> result = postInteractionService.getRecentlyViewedPosts(1L, Pageable.unpaged());

        assertThat(result.getContent()).extracting(PostSummary::getPostId).containsExactly(1L);
        verify(viewHistoryRepository).findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L),
                eq(false),
                eq(false),
                eq(List.of(99L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class));
    }

    @Test
    @DisplayName("최근 본 게시글 조회 - pageable 정규화")
    void getRecentlyViewedPosts_normalizesPageable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L),
                eq(false),
                eq(true),
                eq(List.of(-1L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(5)));

        Page<PostSummary> result = postInteractionService.getRecentlyViewedPosts(
                1L,
                PageRequest.of(2, 1000, Sort.by(Sort.Order.asc("createdAt"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(viewHistoryRepository).findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L),
                eq(false),
                eq(true),
                eq(List.of(-1L)),
                eq(BoardPolicyConstants.INQUIRY_BOARD_URL),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("modified_at"),
                Sort.Order.desc("post_id")));
        assertThat(result.getPageable()).isEqualTo(pageable);
    }

    // --- Misc ---

    @Test
    @DisplayName("공지사항 조회")
    void getNotices_success() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        // currentUserId가 null이므로 userBlockService가 호출되지 않음, blockedUserIds는 null
        when(postRepository.findNoticesByBoardId(eq(1L), eq(true), eq(false), isNull(), eq(false), isNull()))
                .thenReturn(List.of(post));

        List<Post> notices = postService.getNotices("free", null);

        assertThat(notices).hasSize(1);
    }

    @Test
    @DisplayName("공지사항 조회 실패 - 비활성 노드, 권한 없음")
    void getNotices_inactiveBoard_forbidden() {
        ReflectionTestUtils.setField(board, "isActive", false);
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        // Mock user who is NOT author, NOT admin, NOT superadmin
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> postService.getNotices("free", 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("태그별 게시글 조회")
    void getPostsByTag_success() {
        when(postRepository.findByTagId(eq(1L), isNull(), any(Pageable.class))).thenReturn(Page.empty());
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        lenient().when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        postListReadService.getPostsByTag(1L, null, Pageable.unpaged());

        verify(postRepository).findByTagId(eq(1L), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("태그별 게시글 조회는 기본 안정 정렬을 적용한다")
    void getPostsByTag_appliesDefaultStableSort() {
        when(postRepository.findByTagId(eq(1L), isNull(), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        postListReadService.getPostsByTag(1L, null, Pageable.unpaged());

        verify(postRepository).findByTagId(eq(1L), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("태그별 게시글 조회는 null pageable을 기본값으로 보정한다")
    void getPostsByTag_normalizesNullPageable() {
        when(postRepository.findByTagId(eq(1L), isNull(), any(Pageable.class))).thenReturn(Page.empty());

        postListReadService.getPostsByTag(1L, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByTagId(eq(1L), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("태그별 게시글 조회는 페이지 크기와 정렬 필드를 제한한다")
    void getPostsByTag_clampsPageSizeAndSort() {
        when(postRepository.findByTagId(eq(1L), isNull(), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        postListReadService.getPostsByTag(1L, null, PageRequest.of(2, 250, Sort.by("unknown")));

        verify(postRepository).findByTagId(eq(1L), isNull(), pageableCaptor.capture());
        Pageable safePageable = pageableCaptor.getValue();
        assertThat(safePageable.getPageNumber()).isEqualTo(2);
        assertThat(safePageable.getPageSize()).isEqualTo(100);
        assertThat(safePageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("태그별 게시글 조회는 허용 정렬에 안정 정렬을 보강한다")
    void getPostsByTag_appendsStableSortToAllowedSort() {
        when(postRepository.findByTagId(eq(1L), isNull(), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        postListReadService.getPostsByTag(
                1L,
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("likeCount"))));

        verify(postRepository).findByTagId(eq(1L), isNull(), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("likeCount"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("내 게시글 조회")
    void getMyPosts_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByUserAndIsDeleted(eq(user), eq(false), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        lenient().when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        postListReadService.getMyPosts(1L, Pageable.unpaged());

        verify(postRepository).findByUserAndIsDeleted(eq(user), eq(false), pageableCaptor.capture());
        Pageable safePageable = pageableCaptor.getValue();
        assertThat(safePageable.getPageNumber()).isZero();
        assertThat(safePageable.getPageSize()).isEqualTo(20);
        assertThat(safePageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("내 게시글 조회는 페이지 크기와 정렬 필드를 제한한다")
    void getMyPosts_normalizesPageable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByUserAndIsDeleted(eq(user), eq(false), any(Pageable.class))).thenReturn(Page.empty());
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        postListReadService.getMyPosts(
                1L,
                PageRequest.of(2, 250, Sort.by(Sort.Order.asc("commentCount"))));

        verify(postRepository).findByUserAndIsDeleted(eq(user), eq(false), pageableCaptor.capture());
        Pageable safePageable = pageableCaptor.getValue();
        assertThat(safePageable.getPageNumber()).isEqualTo(2);
        assertThat(safePageable.getPageSize()).isEqualTo(100);
        assertThat(safePageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("게시글 이미지 URL 조회")
    void getPostImageUrls_excludesFileWithoutMimeType() {
        File file = File.builder().mimeType("image/png").build();
        ReflectionTestUtils.setField(file, "fileId", 123L);
        File fileWithoutMimeType = File.builder().build();
        ReflectionTestUtils.setField(fileWithoutMimeType, "fileId", 456L);
        when(fileService.getFilesByRelatedEntity(1L, FileService.RELATED_TYPE_POST_CONTENT))
                .thenReturn(List.of(file, fileWithoutMimeType));

        List<String> urls = postService.getPostImageUrls(1L);

        assertThat(urls).containsExactly("/api/v1/files/123/variants/medium");
    }

    @Test
    @DisplayName("게시글 버전 조회")
    void getPostVersions() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postVersionRepository.findVersionResponsesByPostId(1L)).thenReturn(Collections.emptyList());
        postService.getPostVersions(1L, 1L);
        verify(postVersionRepository).findVersionResponsesByPostId(1L);
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("getPostVersions fails for non author and non board admin")
    void getPostVersions_forbidden() {
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.existsByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.getPostVersions(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("getPostVersions succeeds for board admin")
    void getPostVersions_boardAdmin() {
        User adminUser = User.builder().loginId("admin").build();
        ReflectionTestUtils.setField(adminUser, "userId", 2L);
        ReflectionTestUtils.setField(adminUser, "isSuperAdmin", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.existsByUserAndBoardAndIsActive(adminUser, board, true)).thenReturn(true);
        when(postVersionRepository.findVersionResponsesByPostId(1L)).thenReturn(Collections.emptyList());

        postService.getPostVersions(1L, 2L);

        verify(postVersionRepository).findVersionResponsesByPostId(1L);
    }

    @Test
    @DisplayName("getPostVersions succeeds for board admin on secret post")
    void getPostVersions_secretPostBoardAdmin() {
        User adminUser = User.builder().loginId("admin").build();
        ReflectionTestUtils.setField(adminUser, "userId", 2L);
        ReflectionTestUtils.setField(adminUser, "isSuperAdmin", false);
        Post secretPost = createPost(1L, board, user, true);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(secretPost));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(adminUser, List.of(board.getBoardId()), true))
                .thenReturn(List.of(Admin.builder()
                        .user(adminUser)
                        .board(board)
                        .role("BOARD_ADMIN")
                        .build()));
        when(postVersionRepository.findVersionResponsesByPostId(1L)).thenReturn(Collections.emptyList());

        postService.getPostVersions(1L, 2L);

        verify(postVersionRepository).findVersionResponsesByPostId(1L);
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(adminUser, board, true);
    }

    // --- PostResponse ---

    @Test
    @DisplayName("게시글 응답 조회 성공")
    void getPostResponse_success() {
        lenient().when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        lenient().when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        lenient().when(viewHistoryRepository.findByUserAndPost(user, post))
                .thenReturn(Optional.of(new ViewHistory(user, post)));
        lenient().when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);
        lenient().when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        lenient().when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(1L));
        lenient().when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(1L));
        lenient().when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());
        lenient().when(postRepository.countPostsBeforeInBoardDefaultOrder(
                eq(1L), nullable(LocalDateTime.class), eq(1L), eq(Collections.emptyList()), anyBoolean(), eq(1L)))
                .thenReturn(45L);
        lenient().when(postRepository.incrementViewCount(1L)).thenReturn(1);
        lenient().when(postRepository.findViewCountByPostId(1L)).thenReturn(1);
        lenient().when(postVersionRepository.countByPostIdAndVersionType(1L, "MODIFY")).thenReturn(2L);

        PostResponse response = postService.getPostResponse(1L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Test Post");
        assertThat(response.getViewCount()).isEqualTo(1);
        assertThat(response.getBoardListPage()).isEqualTo(2);
        assertThat(response.isLiked()).isTrue();
        assertThat(response.isScrapped()).isTrue();
        assertThat(response.getEditCount()).isEqualTo(2);
        verify(postRepository, times(1)).findByIdWithRelations(1L);
        verify(boardSubscriptionRepository, never()).findBoardUrlsByUserIdAndBoardIdIn(eq(1L), anyCollection());
    }

    @Test
    @DisplayName("게시글 상세 응답 라우팅은 외부 write 트랜잭션을 열지 않는다")
    void getPostResponseRouting_doesNotOpenOuterWriteTransaction() throws Exception {
        Method defaultMethod = PostService.class.getMethod("getPostResponse", Long.class, Long.class);
        Method booleanMethod = PostService.class.getMethod("getPostResponse", Long.class, Long.class, boolean.class);
        Method sizedMethod = PostService.class.getMethod(
                "getPostResponse",
                Long.class,
                Long.class,
                boolean.class,
                int.class);

        assertThat(defaultMethod.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(booleanMethod.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(sizedMethod.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    @DisplayName("게시글 상세 조회 전용 경로는 read-only 클래스 트랜잭션을 사용한다")
    void postDetailReadServiceReadPath_usesClassReadOnlyTransaction() throws Exception {
        Method readMethod = PostDetailReadService.class.getMethod(
                "getPostResponse",
                Long.class,
                Long.class,
                int.class);

        assertThat(PostDetailReadService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(readMethod.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    @DisplayName("게시글 상세 조회수 갱신은 별도 command 트랜잭션을 사용한다")
    void postDetailViewCommandService_usesWriteTransaction() throws Exception {
        Method commandMethod = PostDetailViewCommandService.class.getMethod(
                "recordReadableView",
                Long.class,
                Long.class);

        assertThat(PostDetailViewCommandService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(commandMethod.getAnnotation(Transactional.class).readOnly()).isFalse();
    }

    @Test
    @DisplayName("게시글 응답 조회 - 조회수 증가 시 기존 마지막 읽은 댓글을 유지한다")
    void getPostResponse_incrementViewPreservesLastReadComment() {
        Comment lastReadComment = Comment.builder().post(post).build();
        ReflectionTestUtils.setField(lastReadComment, "commentId", 100L);
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();
        existing.updateView(lastReadComment, 0);
        LocalDateTime previousViewedAt = LocalDateTime.now().minusHours(2);
        ReflectionTestUtils.setField(existing, "modifiedAt", previousViewedAt);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(0);
        when(viewHistoryRepository.touchModifiedAt(1L, 1L)).thenReturn(1);
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.of(existing));
        when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        when(postRepository.countPostsBeforeInBoardDefaultOrder(
                eq(1L), nullable(LocalDateTime.class), eq(1L), eq(Collections.emptyList()), anyBoolean(), eq(1L)))
                .thenReturn(0L);
        when(postRepository.incrementViewCount(1L)).thenReturn(1);
        when(postRepository.findViewCountByPostId(1L)).thenReturn(1);

        PostResponse response = postService.getPostResponse(1L, 1L);

        assertThat(response.getLastReadCommentId()).isEqualTo(100L);
        assertThat(response.getLastViewedAt()).isEqualTo(previousViewedAt);
        verify(viewHistoryRepository).touchModifiedAt(1L, 1L);
    }

    @Test
    @DisplayName("게시글 응답 조회는 조회수 갱신 실패 시 POST_NOT_FOUND를 반환한다")
    void getPostResponse_incrementViewUpdateCountZero_throwsPostNotFound() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(postRepository.incrementViewCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> postService.getPostResponse(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(viewHistoryRepository, never()).insertIgnore(anyLong(), anyLong());
        verify(viewHistoryRepository, never()).touchModifiedAt(anyLong(), anyLong());
    }

    @Test
    @DisplayName("게시글 응답 조회 - 작성자가 조회자를 차단하면 숨김 처리")
    void getPostResponse_authorBlocksViewer_throwsPostNotFound() {
        User viewer = User.builder().loginId("viewer").build();
        ReflectionTestUtils.setField(viewer, "userId", 2L);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(viewer));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));

        assertThatThrownBy(() -> postService.getPostResponse(1L, 2L, false))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 응답 조회 - boardListPageSize를 지정하면 해당 크기로 목록 페이지를 계산")
    void getPostResponse_usesBoardListPageSize() {
        lenient().when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        lenient().when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        lenient().when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        lenient().when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        lenient().when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        lenient().when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        lenient().when(postRepository.countPostsBeforeInBoardDefaultOrder(
                eq(1L), nullable(LocalDateTime.class), eq(1L), eq(Collections.emptyList()), anyBoolean(), eq(1L)))
                .thenReturn(45L);

        PostResponse response = postService.getPostResponse(1L, 1L, false, 10);

        assertThat(response.getBoardListPage()).isEqualTo(4);
    }

    @Test
    @DisplayName("게시글 응답 조회 - 과도한 boardListPageSize는 PageRequestUtils 상한으로 제한")
    void getPostResponse_clampsLargeBoardListPageSize() {
        lenient().when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        lenient().when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        lenient().when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        lenient().when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        lenient().when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        lenient().when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        lenient().when(postRepository.countPostsBeforeInBoardDefaultOrder(
                eq(1L), nullable(LocalDateTime.class), eq(1L), eq(Collections.emptyList()), anyBoolean(), eq(1L)))
                .thenReturn(450L);

        PostResponse response = postService.getPostResponse(1L, 1L, false, 1000);

        assertThat(response.getBoardListPage()).isEqualTo(4);
    }

    @Test
    @DisplayName("게시글 응답 조회 - 잘못된 boardListPageSize는 검증 오류")
    void getPostResponse_invalidBoardListPageSize_throwsValidationError() {
        assertThatThrownBy(() -> postService.getPostResponse(1L, 1L, false, 0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(postRepository, never()).findByIdWithRelations(anyLong());
    }

    @Test
    @DisplayName("게시글 응답 조회 - 조회수 증가하지 않음")
    void getPostResponse_noIncrementView() {
        lenient().when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        lenient().when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        lenient().when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        lenient().when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        lenient().when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        lenient().when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        lenient().when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());

        postService.getPostResponse(1L, 1L, false);

        verify(postRepository, never()).incrementViewCount(anyLong());
        verify(postRepository, never()).findViewCountByPostId(anyLong());
        verify(viewHistoryRepository, never()).insertIgnore(anyLong(), anyLong());
        assertThat(post.getViewCount()).isEqualTo(0);
    }

    // --- View History ---

    @Test
    @DisplayName("조회 기록 조회 - 존재하는 경우")
    void getViewHistory_exists() {
        ViewHistory viewHistory = new ViewHistory(user, post);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.of(viewHistory));

        ViewHistory result = postService.getViewHistory(1L, 1L);

        assertThat(result).isEqualTo(viewHistory);
    }

    @Test
    @DisplayName("조회 기록 조회 - userId가 null인 경우")
    void getViewHistory_nullUserId() {
        ViewHistory result = postService.getViewHistory(null, 1L);

        assertThat(result).isNull();
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("조회 기록 조회 - 존재하지 않는 경우")
    void getViewHistory_notExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());

        ViewHistory result = postService.getViewHistory(1L, 1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("View history lookup rejects unreadable posts")
    void getViewHistory_unreadablePost_throwsPostNotFound() {
        ReflectionTestUtils.setField(board, "isPublic", false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(Collections.emptyList());
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.getViewHistory(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(viewHistoryRepository, never()).findByUserAndPost(any(), any());
    }

    @Test
    @DisplayName("조회수 증가")
    void incrementViewCount_success() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);

        postService.incrementViewCount(1L);

        verify(postRepository).incrementViewCount(1L);
    }

    @Test
    @DisplayName("조회수 증가는 카운터 갱신 실패 시 POST_NOT_FOUND를 반환한다")
    void incrementViewCount_updateCountZero_throwsPostNotFound() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementViewCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> postService.incrementViewCount(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    // --- Draft Posts ---

    @Test
    @DisplayName("초안 목록 조회")
    void getDraftPosts_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        DraftPost draft = DraftPost.builder()
                .user(user)
                .board(board)
                .title("Draft Title")
                .contents("Draft Content")
                .build();
        ReflectionTestUtils.setField(draft, "draftId", 11L);
        when(draftPostRepository.findPageByUserWithBoard(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(draft), PageRequest.of(0, 10), 1));

        DraftListResponse response = postService.getDraftPosts(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getDraftId()).isEqualTo(11L);
        assertThat(response.getContent().getFirst().getBoardName()).isEqualTo("Test Board");
        assertThat(response.getRetentionDays()).isEqualTo(90);
        assertThat(response.getMaxDraftsPerUser()).isEqualTo(100);
        verify(draftPostRepository).findPageByUserWithBoard(eq(user), any(Pageable.class));
    }

    @Test
    @DisplayName("초안 목록 조회 - pageable 정규화")
    void getDraftPosts_normalizesPageable() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findPageByUserWithBoard(eq(user), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));

        postService.getDraftPosts(1L, PageRequest.of(1, 1000, Sort.by(Sort.Order.asc("createdAt"))));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(draftPostRepository).findPageByUserWithBoard(eq(user), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("modifiedAt"),
                Sort.Order.desc("draftId")));
    }

    @Test
    @DisplayName("복구용 초안 조회는 생성 초안이 복수이면 자동 선택하지 않는다")
    void getMatchingDraft_reportsMultipleCreateDrafts() {
        DraftPost first = DraftPost.builder().user(user).board(board).title("first").build();
        DraftPost second = DraftPost.builder().user(user).board(board).title("second").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findMatchingByUserAndTarget(
                eq(user), eq("free"), isNull(), any(Pageable.class)))
                .thenReturn(List.of(first, second));

        DraftMatchResponse response = postService.getMatchingDraft(1L, "free", null);

        assertThat(response.getDraftId()).isNull();
        assertThat(response.isMultipleMatchesFound()).isTrue();
        verify(draftPostRepository).findMatchingByUserAndTarget(
                eq(user), eq("free"), isNull(), argThat(pageable -> pageable.getPageSize() == 2));
    }

    @Test
    @DisplayName("복구용 수정 초안 조회는 최신 후보를 반환한다")
    void getMatchingDraft_returnsEditDraft() {
        DraftPost draft = DraftPost.builder().user(user).board(board).title("edit").build();
        ReflectionTestUtils.setField(draft, "draftId", 91L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findMatchingByUserAndTarget(
                eq(user), eq("free"), eq(7L), any(Pageable.class)))
                .thenReturn(List.of(draft));

        DraftMatchResponse response = postService.getMatchingDraft(1L, "free", 7L);

        assertThat(response.getDraftId()).isEqualTo(91L);
        assertThat(response.isMultipleMatchesFound()).isFalse();
    }

    @Test
    @DisplayName("초안 단건 조회")
    void getDraftPost_success() {
        DraftPost draft = DraftPost.builder().user(user).board(board).title("Draft").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByDraftIdAndUser(10L, user)).thenReturn(Optional.of(draft));

        DraftResponse response = postService.getDraftPost(1L, 10L);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Draft");
    }

    @Test
    @DisplayName("초안 조회 실패 - 존재하지 않음")
    void getDraftPost_notFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByDraftIdAndUser(10L, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getDraftPost(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DRAFT_NOT_FOUND);
    }

    // --- Tags ---

    @Test
    @DisplayName("게시글 태그 조회")
    void getTagsForPost_success() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(tagAssignmentService.getTagNames(post)).thenReturn(List.of("Java", "Spring"));

        List<String> tags = postService.getTagsForPost(1L);

        assertThat(tags).containsExactly("Java", "Spring");
    }

    // --- Board Admin Check ---

    @Test
    @DisplayName("노드 관리자 확인 - Super Admin")
    void isBoardAdmin_superAdmin() {
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        boolean result = postService.isBoardAdmin(1L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("노드 관리자 확인 - Board Admin")
    void isBoardAdmin_boardAdmin() {
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(true);

        boolean result = postService.isBoardAdmin(1L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("노드 관리자 확인 - 일반 유저")
    void isBoardAdmin_normalUser() {
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        boolean result = postService.isBoardAdmin(1L, 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("노드 관리자 확인 - userId가 null")
    void isBoardAdmin_nullUserId() {
        boolean result = postService.isBoardAdmin(null, 1L);

        assertThat(result).isFalse();
    }

    // --- Latest Posts by Board ---

    @Test
    @DisplayName("노드 최신 게시글 조회 - 로그인 사용자")
    void getLatestPostsByBoard_loggedIn() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), anyList(), any(Boolean.class), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Map.of(1L, 20L));
        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThumbnailUrl()).isEqualTo("/api/v1/files/20/variants/thumbnail");
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), anyList(), any(Boolean.class), any(),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort()).isEqualTo(Sort.by(
                Sort.Order.desc("pinnedAt"),
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("노드 최신 게시글 조회 - 비로그인 사용자")
    void getLatestPostsByBoard_notLoggedIn() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Collections.emptyMap());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("노드 최신 게시글 조회 - 결과 없음")
    void getLatestPostsByBoard_empty() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Latest posts by board rejects unreadable board")
    void getLatestPostsByBoard_unreadableBoard_throwsBoardNotFound() {
        ReflectionTestUtils.setField(board, "isPublic", false);
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.getLatestPostsByBoard(1L, 5, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(postRepository, never()).findByBoardIdAndCategoryId(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Latest posts by board with missing user returns USER_NOT_FOUND")
    void getLatestPostsByBoard_missingUser_throwsUserNotFound() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getLatestPostsByBoard(1L, 5, 99L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("Latest posts by board clamps large limit")
    void getLatestPostsByBoard_clampsLargeLimit() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 250, null);

        assertThat(result).isEmpty();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("Latest posts by board rejects non-positive limit")
    void getLatestPostsByBoard_nonPositiveLimit_throwsValidationError() {
        assertThatThrownBy(() -> postService.getLatestPostsByBoard(1L, 0, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(boardRepository, never()).findById(anyLong());
        verify(postRepository, never()).findByBoardIdAndCategoryId(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("노드 최신 게시글 배치 조회 - 보드별 묶음으로 그룹화")
    void getLatestPostsByBoards_groupsSummariesByBoard() {
        when(userBlockService.getBlockedUserIdsEitherDirection(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findLatestPostIdsByBoardIds(List.of(1L), 5, Collections.emptyList(), Set.of(1L), 1L))
                .thenReturn(List.of(1L));
        when(postRepository.findByPostIdIn(List.of(1L))).thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Map.of(1L, 20L));
        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(eq(1L), anyCollection()))
                .thenReturn(Collections.emptyList());

        Map<Long, List<PostSummary>> result = postService.getLatestPostsByBoards(List.of(1L), 5, 1L, Set.of(1L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L)).hasSize(1);
        assertThat(result.get(1L).get(0).getPostId()).isEqualTo(1L);
        assertThat(result.get(1L).get(0).getThumbnailUrl()).isEqualTo("/api/v1/files/20/variants/thumbnail");
    }

    // --- Edge Cases ---

    @Test
    @DisplayName("게시글 생성 실패 - 비활성 노드")
    void createPost_inactiveBoard() {
        ReflectionTestUtils.setField(board, "isActive", false);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        PostCreateRequest request = new PostCreateRequest(null, "Title", "Content", null, false, false, false, false,
                null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 생성 - 카테고리 권한 체크 (SUPER_ADMIN)")
    void createPost_categoryPermission_superAdmin() {
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        ReflectionTestUtils.setField(category, "minWriteRole", "SUPER_ADMIN");
        PostCreateRequest request = new PostCreateRequest(1L, "Title", "Content", null, false, false, false, false,
                null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("게시글 생성 - 카테고리 권한 체크 (BOARD_ADMIN)")
    void createPost_categoryPermission_boardAdmin() {
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        ReflectionTestUtils.setField(category, "minWriteRole", "BOARD_ADMIN");
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        PostCreateRequest request = new PostCreateRequest(1L, "Title", "Content", null, false, false, false, false,
                null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("게시글 생성 - 비활성 카테고리는 사용할 수 없음")
    void createPost_inactiveCategory_notFound() {
        PostCreateRequest request = new PostCreateRequest(1L, "Title", "Content", null, false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 생성 - 잘못된 카테고리 권한값은 실패")
    void createPost_invalidCategoryMinWriteRole_throwsInvalidInput() {
        ReflectionTestUtils.setField(category, "minWriteRole", "BOARD_ADMINN");
        PostCreateRequest request = new PostCreateRequest(1L, "Title", "Content", null, false, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("게시글 수정 - 관리자 전용 카테고리 이동은 차단")
    void updatePost_categoryPermission_boardAdmin_forbidden() {
        BoardCategory otherCategory = BoardCategory.builder().name("Admin Only").board(board).minWriteRole("BOARD_ADMIN").build();
        ReflectionTestUtils.setField(otherCategory, "categoryId", 2L);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        PostUpdateRequest request = new PostUpdateRequest(2L, "Title", "Content", null, false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.of(otherCategory));
        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("게시글 수정 - 기존 카테고리 최소 작성 권한이 올라가면 차단")
    void updatePost_existingCategoryPermissionChanged_forbidden() {
        BoardCategory restrictedCategory = BoardCategory.builder()
                .name("Admin Only")
                .board(board)
                .minWriteRole("BOARD_ADMIN")
                .build();
        ReflectionTestUtils.setField(restrictedCategory, "categoryId", 1L);
        ReflectionTestUtils.setField(post, "category", restrictedCategory);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        PostUpdateRequest request = new PostUpdateRequest(1L, "Title", "Content", null, false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verify(postVersionRepository, never()).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정 - 비활성 노드으로 전환되면 차단")
    void updatePost_inactiveBoard_forbidden() {
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        ReflectionTestUtils.setField(board, "isActive", false);
        PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", null, false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
        verify(postVersionRepository, never()).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정 - 비활성 카테고리 이동은 차단")
    void updatePost_inactiveCategory_notFound() {
        PostUpdateRequest request = new PostUpdateRequest(2L, "Title", "Content", null, false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(2L, 1L, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 수정 - 기존 카테고리가 비활성화되면 수정할 수 없다")
    void updatePost_sameInactiveCategory_notFound() {
        category.deactivate();
        ReflectionTestUtils.setField(post, "category", category);
        PostUpdateRequest request = new PostUpdateRequest(1L, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);

        assertThat(post.getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("게시글 수정 실패 - 이미 삭제된 게시글")
    void updatePost_alreadyDeleted() {
        ReflectionTestUtils.setField(post, "isDeleted", true);
        PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", null, false, false, false, null);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 삭제 실패 - 이미 삭제된 게시글")
    void deletePost_alreadyDeleted() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.deletePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 삭제는 기존 카테고리 권한 변경과 무관하게 작성자를 허용한다")
    void deletePost_existingCategoryPermissionChanged_allowsAuthor() {
        BoardCategory restrictedCategory = BoardCategory.builder()
                .name("Admin Only")
                .board(board)
                .minWriteRole("BOARD_ADMIN")
                .build();
        ReflectionTestUtils.setField(restrictedCategory, "categoryId", 1L);
        ReflectionTestUtils.setField(post, "category", restrictedCategory);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user, List.of("EARN", "REWARD_REVERSAL"), "POST", 1L))
                .thenReturn(0L);

        postService.deletePost(1L, 1L);

        assertThat(post.getIsDeleted()).isTrue();
        verify(tagAssignmentService).clearTags(post);
        verify(fileService).markPostContentFilesDeletionPending(1L);
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("게시글 삭제는 노드 공개 상태 변경과 무관하게 작성자를 허용한다")
    void deletePost_privateBoard_allowsAuthor() {
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);
        ReflectionTestUtils.setField(board, "isPublic", false);

        when(postRepository.findByIdWithRelationsForUpdate(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointHistoryRepository.sumAmountByUserAndTypesAndRelatedTypeAndRelatedId(
                user, List.of("EARN", "REWARD_REVERSAL"), "POST", 1L))
                .thenReturn(0L);

        postService.deletePost(1L, 1L);

        assertThat(post.getIsDeleted()).isTrue();
        verify(tagAssignmentService).clearTags(post);
        verify(fileService).markPostContentFilesDeletionPending(1L);
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("좋아요 실패 - 삭제된 게시글")
    void likePost_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("agent like notification uses agent name")
    void likePost_asAgent_notificationUsesAgentName() {
        User postOwner = User.builder().displayName("post-owner").build();
        ReflectionTestUtils.setField(postOwner, "userId", 3L);
        ReflectionTestUtils.setField(post, "user", postOwner);

        User actorUser = User.builder().displayName("user-owner").build();
        ReflectionTestUtils.setField(actorUser, "userId", 1L);

        Agent actorAgent = Agent.builder()
                .user(actorUser)
                .agentTokenHash("hash")
                .name("agent-liker")
                .description("desc")
                .status(Agent.STATUS_ACTIVE)
                .build();
        ReflectionTestUtils.setField(actorAgent, "agentId", 10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(actorUser));
        when(agentOwnershipService.resolveOwnedActiveAgent(1L, 10L)).thenReturn(actorAgent);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class)))
                .thenReturn(PostLike.builder().user(actorUser).post(post).build());
        when(postRepository.incrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(1);

        postService.likePost(1L, 10L, 1L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getActorAgent()).isNotNull();
        assertThat(notificationEvent.getMessageKey()).isEqualTo("notification.post.liked");
        assertThat(notificationEvent.getMessageParams()).containsExactly("agent-liker");
    }

    @Test
    @DisplayName("삭제된 게시글의 좋아요 취소는 실패한다")
    void unlikePost_deletedPost_throwsPostNotFound() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(postLikeRepository, never()).deleteByUserIdAndPostId(anyLong(), anyLong());
        verify(postRepository, never()).decrementLikeCount(anyLong());
    }

    @Test
    @DisplayName("스크랩 실패 - 삭제된 게시글")
    void scrapPost_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "remark"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 게시글의 스크랩 취소는 실패한다")
    void unscrapPost_deletedPost_throwsPostNotFound() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> postService.unscrapPost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);

        verify(scrapRepository, never()).deleteByUser_UserIdAndPost_PostId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("게시글 조회 실패 - 차단된 사용자")
    void getPostById_blockedUser() {
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(List.of(1L));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> postService.getPostById(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 노드 - Super Admin 접근 가능")
    void getPostById_inactiveBoard_superAdmin() {
        ReflectionTestUtils.setField(board, "isActive", false);
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("비활성 노드 - Board Admin 접근 가능")
    void getPostById_inactiveBoard_boardAdmin() {
        ReflectionTestUtils.setField(board, "isActive", false);
        User otherUser = User.builder().loginId("admin").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);
        Admin admin = Admin.builder().user(otherUser).board(board).build();

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(otherUser, List.of(1L), true))
                .thenReturn(List.of(admin));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);
        when(viewHistoryRepository.insertIgnore(2L, 1L)).thenReturn(1);

        Post result = postService.getPostById(1L, 2L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("비활성 노드 - 작성자 접근 가능")
    void getPostById_inactiveBoard_author() {
        ReflectionTestUtils.setField(board, "isActive", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("공지사항 요약 조회")
    void getNoticeSummaries_success() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findNoticesByBoardId(eq(1L), eq(true), eq(false), isNull(), eq(false), isNull()))
                .thenReturn(List.of(post));

        List<PostSummary> summaries = postListReadService.getNoticeSummaries("free", null);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("boardId 기반 공지사항 요약 조회")
    void getNoticeSummariesByBoardId_success() {
        when(postRepository.findNoticesByBoardId(eq(1L), eq(true), eq(false), isNull(), eq(false), isNull()))
                .thenReturn(List.of(post));

        List<PostSummary> summaries = postListReadService.getNoticeSummaries(1L, null, false);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("관리자 문의글 목록은 페이지 크기 상한을 서비스에서 한 번 더 적용한다")
    void getInquiryPostsForAdmin_clampsPageSize() {
        ReflectionTestUtils.setField(board, "boardUrl", "inquiry");
        when(boardRepository.findByBoardUrl("inquiry")).thenReturn(Optional.of(board));
        when(postRepository.findByBoard_BoardIdAndIsDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        postService.getInquiryPostsForAdmin(PageRequest.of(0, 1000, Sort.by("createdAt").descending()));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByBoard_BoardIdAndIsDeletedFalse(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("postId")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("postId").isDescending()).isTrue();
    }

    @Test
    @DisplayName("관리자 문의글 목록은 허용되지 않은 정렬을 기본 정렬로 대체한다")
    void getInquiryPostsForAdmin_replacesUnsupportedSort() {
        ReflectionTestUtils.setField(board, "boardUrl", "inquiry");
        when(boardRepository.findByBoardUrl("inquiry")).thenReturn(Optional.of(board));
        when(postRepository.findByBoard_BoardIdAndIsDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        postService.getInquiryPostsForAdmin(PageRequest.of(0, 20, Sort.by("title")));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByBoard_BoardIdAndIsDeletedFalse(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("title")).isNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("postId")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("postId").isDescending()).isTrue();
    }

    @Test
    @DisplayName("관리자 문의글 목록은 허용 정렬에도 게시글 ID 안정 정렬을 추가한다")
    void getInquiryPostsForAdmin_appendsPostIdTieBreakerToAllowedSort() {
        ReflectionTestUtils.setField(board, "boardUrl", "inquiry");
        when(boardRepository.findByBoardUrl("inquiry")).thenReturn(Optional.of(board));
        when(postRepository.findByBoard_BoardIdAndIsDeletedFalse(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        postService.getInquiryPostsForAdmin(PageRequest.of(0, 20, Sort.by("viewCount").ascending()));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(postRepository).findByBoard_BoardIdAndIsDeletedFalse(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("viewCount").isAscending()).isTrue();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("postId").isDescending()).isTrue();
    }

    @Test
    @DisplayName("좋아요 여부 확인 - userId가 null")
    void isPostLikedByUser_nullUserId() {
        boolean result = postService.isPostLikedByUser(1L, null);

        assertThat(result).isFalse();
        verify(postLikeRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("스크랩 여부 확인 - userId가 null")
    void isPostScrappedByUser_nullUserId() {
        boolean result = postService.isPostScrappedByUser(1L, null);

        assertThat(result).isFalse();
        verify(scrapRepository, never()).existsById(any());
    }

    @Test
    @DisplayName("게시글 이미지 확인 - 빈 목록")
    void getPostIdsWithImages_emptyList() {
        Set<Long> result = postService.getPostIdsWithImages(Collections.emptyList());

        assertThat(result).isEmpty();
        verify(fileService, never()).getRelatedIdsWithImages(anyList(), anyString());
    }

    @Test
    @DisplayName("게시글 이미지 확인 - null 목록")
    void getPostIdsWithImages_nullList() {
        Set<Long> result = postService.getPostIdsWithImages(null);

        assertThat(result).isEmpty();
        verify(fileService, never()).getRelatedIdsWithImages(anyList(), anyString());
    }

    @Test
    @DisplayName("초안 저장 - originalPostId 포함")
    void saveDraftPost_withOriginalPost() {
        PostDraftRequest request = PostDraftRequest.builder()
                .boardUrl("free")
                .title("Draft")
                .contents("Content")
                .categoryId(1L)
                .originalPostId(1L)
                .build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByCategoryIdAndBoard_BoardIdAndIsActive(1L, 1L, true))
                .thenReturn(Optional.of(category));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        postService.saveDraftPost(1L, request);

        verify(postRepository).findById(1L);
    }

    @Test
    @DisplayName("조회 기록 업데이트 - lastReadCommentId가 null")
    void updateViewHistory_nullCommentId() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, 1000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ViewHistory(user, post)));
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);

        postService.updateViewHistory(1L, 1L, request);

        verify(commentRepository, never()).findByCommentIdAndPost_PostIdAndIsDeletedFalse(anyLong(), anyLong());
    }

    @Test
    @DisplayName("조회 기록 업데이트 - durationMs가 null")
    void updateViewHistory_nullDuration() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new ViewHistory(user, post)));
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(1);

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).insertIgnore(1L, 1L);
    }

    @Test
    @DisplayName("조회수 적립 - 공개글은 익명 사용자도 허용")
    void incrementViewCount_anonymousVisiblePost_success() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(postRepository.incrementViewCount(1L)).thenReturn(1);

        postService.incrementViewCount(1L, null);

        verify(postRepository).incrementViewCount(1L);
    }

    @Test
    @DisplayName("조회수 적립 - 차단된 작성자 글은 실패")
    void incrementViewCount_blockedAuthor_throwsPostNotFound() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L))
                .thenReturn(List.of(user.getUserId()));

        assertThatThrownBy(() -> postService.incrementViewCount(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(postRepository, never()).incrementViewCount(anyLong());
    }

    @Test
    @DisplayName("조회 이력 업데이트 - 음수 체류 시간은 실패")
    void updateViewHistory_negativeDuration_throwsInvalidInput() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, -1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("조회 이력 업데이트 - 다른 게시글 댓글은 실패")
    void updateViewHistory_commentFromAnotherPost_throwsInvalidInput() {
        ViewHistoryRequest request = new ViewHistoryRequest(200L, 1000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(200L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("조회 이력 업데이트 - 삭제 댓글 커서는 실패한다")
    void updateViewHistory_deletedComment_throwsInvalidInput() {
        ViewHistoryRequest request = new ViewHistoryRequest(200L, 1000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(200L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(commentRepository).findByCommentIdAndPost_PostIdAndIsDeletedFalse(200L, 1L);
        verify(viewHistoryRepository, never()).insertIgnore(anyLong(), anyLong());
        verify(viewHistoryRepository, never()).findByUserAndPostForUpdate(anyLong(), anyLong());
    }

    @Test
    @DisplayName("조회 이력 업데이트 - 비정상적으로 큰 체류 시간은 실패")
    void updateViewHistory_excessiveDuration_throwsInvalidInput() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, 86_400_001L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("조회 이력 업데이트 - 비공개 또는 삭제 글은 실패")
    void updateViewHistory_duplicateInsertExistingDurationOverflow_throwsInvalidInput() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, 1L);
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();
        existing.updateView(null, Long.MAX_VALUE);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(viewHistoryRepository.insertIgnore(1L, 1L)).thenReturn(0);

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
        verify(viewHistoryRepository).insertIgnore(1L, 1L);
        verify(viewHistoryRepository, times(2)).findByUserAndPostForUpdate(1L, 1L);
    }

    @Test
    @DisplayName("View history update - unreadable post fails")
    void updateViewHistory_unreadablePost_throwsPostNotFound() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, 1000L);
        ReflectionTestUtils.setField(post, "isDeleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("Feed summary lookup uses one bulk admin query for restricted posts")
    void getPostSummariesByIds_restrictedPosts_usesBulkAdminContext() {
        User author = createUser(2L, "author");
        Board adminBoard = createBoard(10L, "admin-board", author, false, false);
        Board privateBoard = createBoard(20L, "private-board", author, false, false);
        Post adminPost = createPost(100L, adminBoard, author, true);
        Post privatePost = createPost(200L, privateBoard, author, true);
        Admin admin = Admin.builder().user(user).board(adminBoard).role("MANAGER").build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(100L, 200L)))
                .thenReturn(List.of(adminPost, privatePost));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, List.of(10L, 20L), true))
                .thenReturn(List.of(admin));
        stubSummaryInteractions(user);

        Map<Long, PostSummary> summaries = postFacadeReadService.getPostSummariesByIds(
                List.of(100L, 200L),
                1L);

        assertThat(summaries.keySet()).containsExactly(100L);
        verify(adminRepository).findByUserAndBoard_BoardIdInAndIsActive(user, List.of(10L, 20L), true);
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("Feed summary lookup reuses pre-resolved read context")
    void getPostSummariesByIds_preResolvedContext_skipsDuplicateUserBlockAndAdminLookup() {
        User author = createUser(2L, "author");
        Board adminBoard = createBoard(10L, "admin-board", author, false, false);
        Board privateBoard = createBoard(20L, "private-board", author, false, false);
        Post adminPost = createPost(100L, adminBoard, author, true);
        Post privatePost = createPost(200L, privateBoard, author, true);
        PostSummaryReadContext readContext = PostSummaryReadContext.of(
                1L,
                user,
                Collections.emptyList(),
                List.of(10L));

        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(100L, 200L)))
                .thenReturn(List.of(adminPost, privatePost));
        stubSummaryInteractions(user);

        Map<Long, PostSummary> summaries = postFacadeReadService.getPostSummariesByIds(
                List.of(100L, 200L),
                readContext);

        assertThat(summaries.keySet()).containsExactly(100L);
        verify(userRepository, never()).findById(1L);
        verify(userBlockService, never()).getBlockedUserIdsEitherDirectionForExistingUser(1L);
        verify(adminRepository, never()).findByUserAndBoard_BoardIdInAndIsActive(
                any(User.class), anyCollection(), anyBoolean());
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("Feed summary lookup denies board creator without active admin role")
    void getPostSummariesByIds_boardCreatorDeniedWithoutAdminRole() {
        Board creatorBoard = createBoard(10L, "creator-board", user, false, false);
        Post creatorPost = createPost(100L, creatorBoard, createUser(2L, "author"), true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(100L))).thenReturn(List.of(creatorPost));
        when(adminRepository.findByUserAndBoard_BoardIdInAndIsActive(user, List.of(10L), true))
                .thenReturn(Collections.emptyList());

        Map<Long, PostSummary> summaries = postFacadeReadService.getPostSummariesByIds(List.of(100L), 1L);

        assertThat(summaries).isEmpty();
        verify(adminRepository).findByUserAndBoard_BoardIdInAndIsActive(user, List.of(10L), true);
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("Feed summary lookup allows super admin without admin lookup")
    void getPostSummariesByIds_superAdminUsesInMemoryAccess() {
        User superAdmin = createUser(3L, "super-admin");
        ReflectionTestUtils.setField(superAdmin, "isSuperAdmin", true);
        Board privateBoard = createBoard(10L, "private-board", createUser(2L, "author"), false, false);
        Post privatePost = createPost(100L, privateBoard, privateBoard.getCreator(), true);

        when(userRepository.findById(3L)).thenReturn(Optional.of(superAdmin));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(3L)).thenReturn(Collections.emptyList());
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(100L))).thenReturn(List.of(privatePost));
        stubSummaryInteractions(superAdmin);

        Map<Long, PostSummary> summaries = postFacadeReadService.getPostSummariesByIds(List.of(100L), 3L);

        assertThat(summaries.keySet()).containsExactly(100L);
        verify(adminRepository, never()).findByUserAndBoard_BoardIdInAndIsActive(
                any(User.class), anyCollection(), anyBoolean());
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("Feed summary lookup filters either-direction blocked authors")
    void getPostSummariesByIds_eitherDirectionBlockedAuthorExcluded() {
        User author = createUser(2L, "blocked-author");
        Board publicBoard = createBoard(10L, "free", author, true, true);
        Post blockedPost = createPost(100L, publicBoard, author, false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(List.of(2L));
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(100L))).thenReturn(List.of(blockedPost));

        Map<Long, PostSummary> summaries = postFacadeReadService.getPostSummariesByIds(List.of(100L), 1L);

        assertThat(summaries).isEmpty();
        verify(postLikeRepository, never()).findPostIdsByUserIdAndPostIdIn(any(), any());
        verify(adminRepository, never()).findByUserAndBoard_BoardIdInAndIsActive(
                any(User.class), anyCollection(), anyBoolean());
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("Feed summary lookup skips admin lookup for public non-secret posts")
    void getPostSummariesByIds_publicPostsSkipAdminLookup() {
        User author = createUser(2L, "author");
        Board publicBoard = createBoard(10L, "free", author, true, true);
        Post publicPost = createPost(100L, publicBoard, author, false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(100L))).thenReturn(List.of(publicPost));
        stubSummaryInteractions(user);

        Map<Long, PostSummary> summaries = postFacadeReadService.getPostSummariesByIds(List.of(100L), 1L);

        assertThat(summaries.keySet()).containsExactly(100L);
        verify(adminRepository, never()).findByUserAndBoard_BoardIdInAndIsActive(
                any(User.class), anyCollection(), anyBoolean());
        verify(adminRepository, never()).existsByUserAndBoardAndIsActive(
                any(User.class), any(Board.class), anyBoolean());
    }

    @Test
    @DisplayName("초안 투표는 질문, 선택지 개수와 선택지 길이 상한을 검증한다")
    void saveDraftPost_rejectsPollAboveDraftUpperBounds() {
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        PollRequest longQuestion = new PollRequest();
        longQuestion.setQuestion("q".repeat(201));
        assertThatThrownBy(() -> postService.saveDraftPost(1L, draftRequest(longQuestion)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        PollRequest tooManyOptions = new PollRequest();
        tooManyOptions.setOptions(java.util.stream.IntStream.range(0, 11).mapToObj(i -> "option").toList());
        assertThatThrownBy(() -> postService.saveDraftPost(1L, draftRequest(tooManyOptions)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        PollRequest longOption = new PollRequest();
        longOption.setOptions(List.of("o".repeat(101)));
        assertThatThrownBy(() -> postService.saveDraftPost(1L, draftRequest(longOption)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);

        verify(draftPostRepository, never()).saveAndFlush(any(DraftPost.class));
    }

    @Test
    @DisplayName("초안 투표는 비어 있는 질문과 선택지 한 개를 허용한다")
    void saveDraftPost_allowsIncompletePollWithinUpperBounds() {
        PollRequest incompletePoll = new PollRequest();
        incompletePoll.setQuestion("");
        incompletePoll.setOptions(List.of(""));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.saveAndFlush(any(DraftPost.class))).thenAnswer(invocation -> {
            DraftPost saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "draftId", 88L);
            return saved;
        });

        DraftResponse response = postService.saveDraftPost(1L, draftRequest(incompletePoll));

        assertThat(response.getPoll()).isSameAs(incompletePoll);
    }

    @Test
    void updateViewHistory_omittedCommentIdPreservesExistingProgress() {
        Comment existingLastReadComment = Comment.builder().post(post).build();
        ReflectionTestUtils.setField(existingLastReadComment, "commentId", 100L);
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();
        existing.updateView(existingLastReadComment, 500L);
        ViewHistoryRequest request = new ViewHistoryRequest(null, 1000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L)).thenReturn(Optional.of(existing));

        postService.updateViewHistory(1L, 1L, request);

        assertThat(existing.getLastReadComment()).isSameAs(existingLastReadComment);
        assertThat(existing.getDurationMs()).isEqualTo(1500L);
        verify(commentRepository, never()).findByCommentIdAndPost_PostIdAndIsDeletedFalse(anyLong(), anyLong());
    }

    @Test
    void updateViewHistory_olderCommentIdDoesNotMoveProgressBackward() {
        Comment existingLastReadComment = Comment.builder().post(post).build();
        ReflectionTestUtils.setField(existingLastReadComment, "commentId", 100L);
        Comment staleLastReadComment = Comment.builder().post(post).build();
        ReflectionTestUtils.setField(staleLastReadComment, "commentId", 50L);
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();
        existing.updateView(existingLastReadComment, 500L);
        ViewHistoryRequest request = new ViewHistoryRequest(50L, 1000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByCommentIdAndPost_PostIdAndIsDeletedFalse(50L, 1L))
                .thenReturn(Optional.of(staleLastReadComment));
        when(viewHistoryRepository.findByUserAndPostForUpdate(1L, 1L)).thenReturn(Optional.of(existing));

        postService.updateViewHistory(1L, 1L, request);

        assertThat(existing.getLastReadComment()).isSameAs(existingLastReadComment);
        assertThat(existing.getDurationMs()).isEqualTo(1500L);
    }

    private PostDraftRequest draftRequest(PollRequest poll) {
        return PostDraftRequest.builder()
                .boardUrl("free")
                .title("draft")
                .contents("")
                .fileIds(Collections.emptyList())
                .poll(poll)
                .build();
    }

    private User createUser(Long userId, String loginId) {
        User createdUser = User.builder()
                .loginId(loginId)
                .displayName(loginId)
                .build();
        ReflectionTestUtils.setField(createdUser, "userId", userId);
        return createdUser;
    }

    private Board createBoard(Long boardId, String boardUrl, User creator, boolean isActive, boolean isPublic) {
        Board createdBoard = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(isPublic)
                .build();
        ReflectionTestUtils.setField(createdBoard, "boardId", boardId);
        ReflectionTestUtils.setField(createdBoard, "isActive", isActive);
        return createdBoard;
    }

    private Post createPost(Long postId, Board board, User author, boolean isSecret) {
        Post createdPost = Post.builder()
                .title("Post " + postId)
                .contents("Contents " + postId)
                .user(author)
                .board(board)
                .isSecret(isSecret)
                .build();
        ReflectionTestUtils.setField(createdPost, "postId", postId);
        return createdPost;
    }

    private void stubSummaryInteractions(User viewer) {
        when(postLikeRepository.findPostIdsByUserIdAndPostIdIn(eq(viewer.getUserId()), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(scrapRepository.findPostIdsByUserIdAndPostIdIn(eq(viewer.getUserId()), anyCollection()))
                .thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(eq(viewer.getUserId()), anyCollection()))
                .thenReturn(Collections.emptyList());
    }
}
