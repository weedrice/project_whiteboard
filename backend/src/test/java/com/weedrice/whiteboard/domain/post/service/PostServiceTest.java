package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentOwnershipService;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.feed.event.PostPublishedEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.*;
import com.weedrice.whiteboard.domain.post.repository.*;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.tag.service.TagAssignmentService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
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
    private BoardCategoryRepository boardCategoryRepository;
    @Mock
    private ScrapRepository scrapRepository;
    @Mock
    private DraftPostRepository draftPostRepository;
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
    private BoardAccessPolicy boardAccessPolicy;
    private PostAccessPolicy postAccessPolicy;
    private PostSummaryAssembler postSummaryAssembler;

    private PostService postService;

    private User user;
    private Board board;
    private Post post;
    private BoardCategory category;

    @BeforeEach
    void setUp() {
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        postSummaryAssembler = new PostSummaryAssembler(
                fileService,
                userRepository,
                postLikeRepository,
                scrapRepository,
                boardSubscriptionRepository,
                commentRepository,
                boardAccessPolicy);
        postAccessPolicy = new PostAccessPolicy(boardAccessPolicy);
        postService = new PostService(
                postRepository,
                boardRepository,
                boardCategoryRepository,
                userRepository,
                postLikeRepository,
                scrapRepository,
                draftPostRepository,
                postVersionRepository,
                tagAssignmentService,
                viewHistoryRepository,
                eventPublisher,
                pointService,
                commentRepository,
                fileService,
                boardSubscriptionRepository,
                userBlockService,
                globalConfigService,
                agentOwnershipService,
                sanctionService,
                postSummaryAssembler,
                postAccessPolicy,
                boardAccessPolicy);

        // GlobalConfigService 기본 mock 설정 - lenient()로 설정하여 일부 테스트에서 사용되지 않아도 허용
        lenient().when(globalConfigService.getConfig(anyString())).thenReturn("50");
        lenient().when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(anyLong(), eq(true)))
                .thenReturn(Collections.emptyList());
        lenient().when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());
        lenient().when(commentRepository.findLatestNonDeletedAuthorsByPostIds(anyList()))
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
    }

    // --- Create Post ---

    @Test
    @DisplayName("게시글 생성 성공 - BoardUrl 사용")
    void createPost_withBoardUrl_success() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, List.of(1L, 2L));

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post p = invocation.getArgument(0);
            ReflectionTestUtils.setField(p, "postId", 100L);
            return p;
        });

        Post created = postService.createPost(1L, "free", request);

        assertThat(created).isNotNull();
        assertThat(created.getTitle()).isEqualTo("New Post");
        verify(tagAssignmentService).assignTags(created, request.getTags());
        verify(fileService, times(2)).associateFileWithEntity(anyLong(), eq(1L), eq(100L), eq("POST_CONTENT"));
        verify(pointService).addPoint(eq(1L), eq(50), anyString(), eq(100L), eq("POST"));
    }

    @Test
    @DisplayName("활성 BAN 사용자는 게시글을 작성할 수 없다")
    void createPost_bannedUser_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

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
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
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
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postService.createPost(1L, 1L, request);

        verify(boardCategoryRepository).findById(1L);
    }

    @Test
    @DisplayName("createPost fails when category belongs to another board")
    void createPost_categoryBoardMismatch_notFound() {
        PostCreateRequest request = new PostCreateRequest(2L, "New Post", "New Contents", Collections.emptyList(),
                false, false, false, false, null);

        Board otherBoard = Board.builder().boardName("Other Board").creator(user).build();
        ReflectionTestUtils.setField(otherBoard, "boardId", 2L);
        BoardCategory otherCategory = BoardCategory.builder().name("Other").board(otherBoard).build();
        ReflectionTestUtils.setField(otherCategory, "categoryId", 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findById(2L)).thenReturn(Optional.of(otherCategory));

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
    @DisplayName("에이전트 게시글 생성 실패 - 일반 카테고리 권한을 만족하지 못하면 작성 불가")
    void createPostAsAgent_generalCategoryPermission_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "Title", "Contents", Collections.emptyList(), false,
                false, false, false, null);
        User boardOwner = User.builder().loginId("owner").build();
        ReflectionTestUtils.setField(boardOwner, "userId", 99L);
        ReflectionTestUtils.setField(board, "creator", boardOwner);

        BoardCategory generalCategory = BoardCategory.builder().name("일반").board(board).minWriteRole("BOARD_ADMIN")
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
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findByBoard_BoardIdAndIsActiveOrderBySortOrderAsc(1L, true))
                .thenReturn(List.of(generalCategory));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPostAsAgent(1L, 10L, "free", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    // --- Read Post ---

    @Test
    @DisplayName("게시글 조회 성공 - ID로 조회")
    void getPostById_success() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isEqualTo(post);
        assertThat(result.getViewCount()).isEqualTo(1); // incremented
        verify(viewHistoryRepository).saveAndFlush(any(ViewHistory.class));
    }

    @Test
    @DisplayName("게시글 조회 - 조회 이력 중복 insert를 기존 row로 흡수")
    void getPostById_duplicateViewHistory_reusesExistingRow() {
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findByUserAndPost(user, post))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isEqualTo(post);
        verify(viewHistoryRepository).saveAndFlush(any(ViewHistory.class));
        verify(viewHistoryRepository, times(2)).findByUserAndPost(user, post);
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
    @DisplayName("게시글 조회 실패 - 비활성 게시판, 권한 없음")
    void getPostById_inactiveBoard_forbidden() {
        ReflectionTestUtils.setField(board, "isActive", false);

        // Mock user who is NOT author, NOT admin, NOT superadmin
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.existsByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(false);

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
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(), any(Boolean.class), any(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        postService.getPosts("free", null, null, null, null, Pageable.unpaged());

        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(), any(Boolean.class), any(),
                any(Pageable.class));
    }

    @Test
    @DisplayName("게시글 목록 조회 실패 - 비활성 게시판, 권한 없음")
    void getPosts_inactiveBoard_forbidden() {
        ReflectionTestUtils.setField(board, "isActive", false);
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        // Mock user who is NOT author, NOT admin, NOT superadmin
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> postService.getPosts("free", null, null, null, 2L, Pageable.unpaged()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("인기 게시글 조회 - 로그인 사용자")
    void getTrendingPosts_loggedIn() {
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), anyList(), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Map.of(1L, 10L));

        when(postLikeRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(scrapRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findByUserAndBoardIn(eq(user), anyList())).thenReturn(Collections.emptyList());

        List<PostSummary> result = postService.getTrendingPosts(PageRequest.of(0, 10), 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThumbnailUrl()).isEqualTo("/api/v1/files/10");
    }

    // --- Update Post ---

    @Test
    @DisplayName("게시글 수정 성공")
    void updatePost_success() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, List.of(5L));

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Post updated = postService.updatePost(1L, 1L, request);

        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        verify(tagAssignmentService).assignTags(post, request.getTags());
        verify(fileService).associateFileWithEntity(eq(5L), eq(1L), eq(1L), eq("POST_CONTENT"));
        verify(postVersionRepository).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 寃뚯떆湲???섏젙?????녿떎")
    void updatePost_bannedUser_forbidden() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents",
                Collections.emptyList(), false, false, false, null);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
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
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
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

        Board otherBoard = Board.builder().boardName("Other Board").creator(user).build();
        ReflectionTestUtils.setField(otherBoard, "boardId", 2L);
        BoardCategory otherCategory = BoardCategory.builder().name("Other").board(otherBoard).build();
        ReflectionTestUtils.setField(otherCategory, "categoryId", 2L);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardCategoryRepository.findById(2L)).thenReturn(Optional.of(otherCategory));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
    }

    // --- Delete Post ---

    @Test
    @DisplayName("게시글 삭제 성공")
    void deletePost_success() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        postService.deletePost(1L, 1L);

        assertThat(post.getIsDeleted()).isTrue();
        verify(tagAssignmentService).clearTags(post);
        verify(pointService).forceSubtractPoint(eq(1L), eq(50), anyString(), eq(1L), eq("POST"));
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 寃뚯떆湲???쒖젣?????녿떎")
    void deletePost_bannedUser_forbidden() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
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
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class))).thenReturn(PostLike.builder().user(user).post(post).build());
        when(postRepository.incrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(1);

        int likeCount = postService.likePost(1L, 1L);

        verify(postLikeRepository).saveAndFlush(any(PostLike.class));
        verify(postRepository).incrementLikeCount(1L);
        assertThat(likeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 寃뚯떆湲 醫뗭븘?????녿떎")
    void likePost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postLikeRepository, never()).saveAndFlush(any(PostLike.class));
    }

    @Test
    @DisplayName("좋아요 실패 - 이미 좋아요 함")
    void likePost_alreadyLiked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_LIKED);
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
    @DisplayName("좋아요 취소 성공")
    void unlikePost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.deleteByUserIdAndPostId(1L, 1L)).thenReturn(1);
        when(postRepository.decrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(0);

        int likeCount = postService.unlikePost(1L, 1L);

        verify(postLikeRepository).deleteByUserIdAndPostId(1L, 1L);
        verify(postRepository).decrementLikeCount(1L);
        assertThat(likeCount).isZero();
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 寃뚯떆湲 醫뗭븘??痍⑥냼?????녿떎")
    void unlikePost_bannedUser_forbidden() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(postLikeRepository, never()).deleteByUserIdAndPostId(anyLong(), anyLong());
    }

    // --- Scraps ---

    @Test
    @DisplayName("스크랩 성공")
    void scrapPost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.scrapPost(1L, 1L, "My Scrap");

        verify(scrapRepository).saveAndFlush(any(Scrap.class));
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 寃뚯떆湲 ???ㅽ겕?앺븷 ????녿떎")
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
        when(scrapRepository.deleteByUser_UserIdAndPost_PostId(1L, 1L)).thenReturn(1L);

        postService.unscrapPost(1L, 1L);

        verify(scrapRepository).deleteByUser_UserIdAndPost_PostId(1L, 1L);
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 寃뚯떆湲 ???ㅽ겕???⑥냼?????녿떎")
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
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(scrapRepository.existsById(any(ScrapId.class))).thenReturn(true);

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "My Scrap"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_SCRAPED);
    }

    @Test
    @DisplayName("non-duplicate scrap persistence error is rethrown")
    void scrapPost_nonDuplicateDataIntegrityViolation_isRethrown() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException("other");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(scrapRepository.saveAndFlush(any(Scrap.class))).thenThrow(exception);
        when(scrapRepository.existsById(any(ScrapId.class))).thenReturn(false);

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "My Scrap"))
                .isSameAs(exception);
    }

    @Test
    @DisplayName("unscrap returns NOT_SCRAPED when no row is deleted")
    void unscrapPost_notScrapped() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scrapRepository.deleteByUser_UserIdAndPost_PostId(1L, 1L)).thenReturn(0L);

        assertThatThrownBy(() -> postService.unscrapPost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_SCRAPED);
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
        when(scrapRepository.findPageByUserWithPostDetails(eq(user), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(scrap), PageRequest.of(0, 10), 1));

        ScrapListResponse response = postService.getMyScraps(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getRemark()).isEqualTo("bookmark");
        assertThat(response.getContent().getFirst().getPost().getTitle()).isEqualTo("Test Post");
        assertThat(response.getContent().getFirst().getPost().getBoardName()).isEqualTo("Test Board");
        verify(scrapRepository).findPageByUserWithPostDetails(eq(user), any(Pageable.class));
    }

    // --- Drafts ---

    @Test
    @DisplayName("초안 저장 - 신규")
    void saveDraftPost_new() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.save(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        DraftPost draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getTitle()).isEqualTo("Draft Title");
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 珥덉븞????ν븷 ???녿떎")
    void saveDraftPost_bannedUser_forbidden() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(user);

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(draftPostRepository, never()).save(any(DraftPost.class));
    }

    @Test
    @DisplayName("초안 저장 - 수정")
    void saveDraftPost_update() {
        DraftPost existingDraft = DraftPost.builder().user(user).board(board).title("Old").build();
        PostDraftRequest request = new PostDraftRequest(10L, "free", "New Title", "New Content", null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(draftPostRepository.findByDraftIdAndUser(10L, user)).thenReturn(Optional.of(existingDraft));
        when(draftPostRepository.save(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        DraftPost draft = postService.saveDraftPost(1L, request);

        assertThat(draft.getTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("초안 저장은 읽기 가능 보드라도 쓰기 권한이 없으면 차단한다")
    void saveDraftPost_requiresWritableBoard() {
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft Title", "Draft Content", null);
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        ReflectionTestUtils.setField(board, "isPublic", false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(draftPostRepository, never()).save(any(DraftPost.class));
    }

    @Test
    @DisplayName("기존 초안도 보드가 더 이상 쓰기 불가하면 수정 저장을 차단한다")
    void saveDraftPost_updateRequiresWritableBoard() {
        PostDraftRequest request = new PostDraftRequest(10L, "free", "New Title", "New Content", null);
        User otherCreator = User.builder().loginId("other").displayName("Other").build();
        ReflectionTestUtils.setField(otherCreator, "userId", 2L);
        ReflectionTestUtils.setField(board, "creator", otherCreator);
        ReflectionTestUtils.setField(board, "isPublic", false);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.saveDraftPost(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);

        verify(draftPostRepository, never()).save(any(DraftPost.class));
    }

    @Test
    @DisplayName("초안 삭제")
    void deleteDraftPost_success() {
        DraftPost existingDraft = DraftPost.builder().user(user).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByDraftIdAndUser(10L, user)).thenReturn(Optional.of(existingDraft));

        postService.deleteDraftPost(1L, 10L);

        verify(draftPostRepository).delete(existingDraft);
    }

    @Test
    @DisplayName("?쒖꽦 BAN ?ъ슜?먮뒗 珥덉븞???쒖젣?????녿떎")
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
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));
        when(commentRepository.findByCommentIdAndPost_PostId(100L, 1L))
                .thenReturn(Optional.of(Comment.builder().post(post).build()));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).saveAndFlush(any(ViewHistory.class));
    }

    @Test
    @DisplayName("조회 기록 업데이트 - 중복 insert 예외 시 기존 row를 재사용")
    void updateViewHistory_duplicateViewHistory_reusesExistingRow() {
        ViewHistoryRequest request = new ViewHistoryRequest(100L, 5000L);
        ViewHistory existing = ViewHistory.builder().user(user).post(post).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(commentRepository.findByCommentIdAndPost_PostId(100L, 1L))
                .thenReturn(Optional.of(Comment.builder().post(post).build()));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).saveAndFlush(any(ViewHistory.class));
        verify(viewHistoryRepository, times(2)).findByUserAndPost(user, post);
    }

    @Test
    @DisplayName("최근 본 게시글 조회")
    void getRecentlyViewedPosts() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L), eq(false), eq(true), eq(List.of(-1L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(1L), Pageable.unpaged(), 1));
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(1L))).thenReturn(List.of(post));

        Page<PostSummary> result = postService.getRecentlyViewedPosts(1L, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPostId()).isEqualTo(1L);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(viewHistoryRepository).findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L), eq(false), eq(true), eq(List.of(-1L)), any(Pageable.class));
    }

    @Test
    @DisplayName("최근 본 게시글 조회는 차단 사용자를 제외한 가시 항목만 조회한다")
    void getRecentlyViewedPosts_excludesBlockedAuthors() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(99L));
        when(viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L), eq(false), eq(false), eq(List.of(99L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(1L), Pageable.unpaged(), 1));
        when(postRepository.findByPostIdInAndIsDeletedFalse(List.of(1L))).thenReturn(List.of(post));

        Page<PostSummary> result = postService.getRecentlyViewedPosts(1L, Pageable.unpaged());

        assertThat(result.getContent()).extracting(PostSummary::getPostId).containsExactly(1L);
        verify(viewHistoryRepository).findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                eq(1L), eq(false), eq(false), eq(List.of(99L)), any(Pageable.class));
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
    @DisplayName("공지사항 조회 실패 - 비활성 게시판, 권한 없음")
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

        postService.getPostsByTag(1L, null, Pageable.unpaged());

        verify(postRepository).findByTagId(eq(1L), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("내 게시글 조회")
    void getMyPosts_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByUserAndIsDeleted(eq(user), eq(false), any(Pageable.class))).thenReturn(Page.empty());
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        lenient().when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        postService.getMyPosts(1L, Pageable.unpaged());

        verify(postRepository).findByUserAndIsDeleted(eq(user), eq(false), any(Pageable.class));
    }

    @Test
    @DisplayName("게시글 이미지 URL 조회")
    void getPostImageUrls() {
        File file = File.builder().mimeType("image/png").build();
        ReflectionTestUtils.setField(file, "fileId", 123L);
        when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(List.of(file));

        List<String> urls = postService.getPostImageUrls(1L);

        assertThat(urls).contains("/api/v1/files/123");
    }

    @Test
    @DisplayName("게시글 버전 조회")
    void getPostVersions() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postVersionRepository.findByPost_PostIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        postService.getPostVersions(1L, 1L);
        verify(postVersionRepository).findByPost_PostIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("getPostVersions fails for non author and non board admin")
    void getPostVersions_forbidden() {
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.existsByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.getPostVersions(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    // --- PostResponse ---

    @Test
    @DisplayName("게시글 응답 조회 성공")
    void getPostResponse_success() {
        lenient().when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        lenient().when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        lenient().when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        lenient().when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        lenient().when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(false);
        lenient().when(scrapRepository.existsById(any(ScrapId.class))).thenReturn(false);
        lenient().when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());

        PostResponse response = postService.getPostResponse(1L, 1L);

        assertThat(response).isNotNull();
        assertThat(response.getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("게시글 응답 조회 - 조회수 증가하지 않음")
    void getPostResponse_noIncrementView() {
        lenient().when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        lenient().when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        lenient().when(tagAssignmentService.getTagNames(post)).thenReturn(Collections.emptyList());
        lenient().when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(false);
        lenient().when(scrapRepository.existsById(any(ScrapId.class))).thenReturn(false);
        lenient().when(fileService.getFilesByRelatedEntity(1L, "POST_CONTENT")).thenReturn(Collections.emptyList());
        lenient().when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());

        postService.getPostResponse(1L, 1L, false);

        verify(viewHistoryRepository, never()).save(any(ViewHistory.class));
        assertThat(post.getViewCount()).isEqualTo(0);
    }

    // --- View History ---

    @Test
    @DisplayName("조회 기록 조회 - 존재하는 경우")
    void getViewHistory_exists() {
        ViewHistory viewHistory = new ViewHistory(user, post);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
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
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());

        ViewHistory result = postService.getViewHistory(1L, 1L);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("조회수 증가")
    void incrementViewCount_success() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        postService.incrementViewCount(1L);

        assertThat(post.getViewCount()).isEqualTo(1);
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
        verify(draftPostRepository).findPageByUserWithBoard(eq(user), any(Pageable.class));
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
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_FOUND);
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
    @DisplayName("게시판 관리자 확인 - Super Admin")
    void isBoardAdmin_superAdmin() {
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);
        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        boolean result = postService.isBoardAdmin(1L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("게시판 관리자 확인 - Board Admin")
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
    @DisplayName("게시판 관리자 확인 - 일반 유저")
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
    @DisplayName("게시판 관리자 확인 - userId가 null")
    void isBoardAdmin_nullUserId() {
        boolean result = postService.isBoardAdmin(null, 1L);

        assertThat(result).isFalse();
    }

    // --- Latest Posts by Board ---

    @Test
    @DisplayName("게시판 최신 게시글 조회 - 로그인 사용자")
    void getLatestPostsByBoard_loggedIn() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), anyList(), any(Boolean.class), any(),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Map.of(1L, 20L));
        when(postLikeRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(scrapRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findByUserAndBoardIn(eq(user), anyList())).thenReturn(Collections.emptyList());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getThumbnailUrl()).isEqualTo("/api/v1/files/20");
    }

    @Test
    @DisplayName("게시판 최신 게시글 조회 - 비로그인 사용자")
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
    @DisplayName("게시판 최신 게시글 조회 - 결과 없음")
    void getLatestPostsByBoard_empty() {
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(),
                any(Pageable.class)))
                .thenReturn(Page.empty());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("寃뚯떆??理쒖떊 寃뚯떆湲 諛곗튂 議고쉶 - 蹂대뱶蹂??묒쐞濡?洹몃９?묒뾽")
    void getLatestPostsByBoards_groupsSummariesByBoard() {
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findLatestPostIdsByBoardIds(List.of(1L), 5, Collections.emptyList(), Set.of(1L), 1L))
                .thenReturn(List.of(1L));
        when(postRepository.findByPostIdIn(List.of(1L))).thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.getFirstImageFileIdsForPosts(anyList())).thenReturn(Map.of(1L, 20L));
        when(postLikeRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(scrapRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findByUserAndBoardIn(eq(user), anyList())).thenReturn(Collections.emptyList());

        Map<Long, List<PostSummary>> result = postService.getLatestPostsByBoards(List.of(1L), 5, 1L, Set.of(1L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L)).hasSize(1);
        assertThat(result.get(1L).get(0).getPostId()).isEqualTo(1L);
        assertThat(result.get(1L).get(0).getThumbnailUrl()).isEqualTo("/api/v1/files/20");
    }

    // --- Edge Cases ---

    @Test
    @DisplayName("게시글 생성 실패 - 비활성 게시판")
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
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(category));

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
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, board, true)).thenReturn(false);

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("게시글 수정 실패 - 이미 삭제된 게시글")
    void updatePost_alreadyDeleted() {
        ReflectionTestUtils.setField(post, "isDeleted", true);
        PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", null, false, false, false, null);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 삭제 실패 - 이미 삭제된 게시글")
    void deletePost_alreadyDeleted() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.deletePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
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
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postLikeRepository.saveAndFlush(any(PostLike.class))).thenReturn(PostLike.builder().user(actorUser).post(post).build());
        when(postRepository.incrementLikeCount(1L)).thenReturn(1);
        when(postRepository.findLikeCountByPostId(1L)).thenReturn(1);

        postService.likePost(1L, 10L, 1L);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        NotificationEvent notificationEvent = (NotificationEvent) eventCaptor.getValue();
        assertThat(notificationEvent.getActorAgent()).isNotNull();
        assertThat(notificationEvent.getContent()).isEqualTo("agent-liker님이 회원님의 게시글을 좋아합니다.");
    }

    @Test
    @DisplayName("좋아요 취소 실패 - 삭제된 게시글")
    void unlikePost_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
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
    @DisplayName("게시글 조회 실패 - 차단된 사용자")
    void getPostById_blockedUser() {
        User otherUser = User.builder().loginId("other").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(List.of(1L));
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));

        assertThatThrownBy(() -> postService.getPostById(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 게시판 - Super Admin 접근 가능")
    void getPostById_inactiveBoard_superAdmin() {
        ReflectionTestUtils.setField(board, "isActive", false);
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("비활성 게시판 - Board Admin 접근 가능")
    void getPostById_inactiveBoard_boardAdmin() {
        ReflectionTestUtils.setField(board, "isActive", false);
        User otherUser = User.builder().loginId("admin").build();
        ReflectionTestUtils.setField(otherUser, "userId", 2L);
        ReflectionTestUtils.setField(otherUser, "isSuperAdmin", false);
        Admin admin = Admin.builder().user(otherUser).board(board).build();

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherUser));
        when(adminRepository.existsByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(true);
        when(viewHistoryRepository.findByUserAndPost(otherUser, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        Post result = postService.getPostById(1L, 2L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("비활성 게시판 - 작성자 접근 가능")
    void getPostById_inactiveBoard_author() {
        ReflectionTestUtils.setField(board, "isActive", false);

        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("공지사항 요약 조회")
    void getNoticeSummaries_success() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findNoticesByBoardId(eq(1L), eq(true), eq(false), isNull(), eq(false), isNull()))
                .thenReturn(List.of(post));

        List<PostSummary> summaries = postService.getNoticeSummaries("free", null);

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getTitle()).isEqualTo("Test Post");
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
        PostDraftRequest request = new PostDraftRequest(null, "free", "Draft", "Content", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(draftPostRepository.save(any(DraftPost.class))).thenAnswer(i -> i.getArgument(0));

        DraftPost draft = postService.saveDraftPost(1L, request);

        verify(postRepository).findById(1L);
    }

    @Test
    @DisplayName("조회 기록 업데이트 - lastReadCommentId가 null")
    void updateViewHistory_nullCommentId() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, 1000L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        postService.updateViewHistory(1L, 1L, request);

        verify(commentRepository, never()).findByCommentIdAndPost_PostId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("조회 기록 업데이트 - durationMs가 null")
    void updateViewHistory_nullDuration() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.saveAndFlush(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).saveAndFlush(any(ViewHistory.class));
    }

    @Test
    @DisplayName("조회수 적립 - 공개글은 익명 사용자도 허용")
    void incrementViewCount_anonymousVisiblePost_success() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        postService.incrementViewCount(1L, null);

        assertThat(post.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("조회수 적립 - 차단된 작성자 글은 실패")
    void incrementViewCount_blockedAuthor_throwsPostNotFound() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(List.of(user.getUserId()));

        assertThatThrownBy(() -> postService.incrementViewCount(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
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
        when(commentRepository.findByCommentIdAndPost_PostId(200L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
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
    void updateViewHistory_unreadablePost_throwsPostNotFound() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, 1000L);
        ReflectionTestUtils.setField(post, "isDeleted", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updateViewHistory(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }
}
