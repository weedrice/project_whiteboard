package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardCategoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.*;
import com.weedrice.whiteboard.domain.post.repository.*;
import com.weedrice.whiteboard.domain.tag.entity.PostTag;
import com.weedrice.whiteboard.domain.tag.entity.Tag;
import com.weedrice.whiteboard.domain.tag.repository.PostTagRepository;
import com.weedrice.whiteboard.domain.tag.service.TagService;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private TagService tagService;
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
    private PostTagRepository postTagRepository;
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

    @InjectMocks
    private PostService postService;

    private User user;
    private Board board;
    private Post post;
    private BoardCategory category;

    @BeforeEach
    void setUp() {
        // GlobalConfigService 기본 mock 설정 - lenient()로 설정하여 일부 테스트에서 사용되지 않아도 허용
        lenient().when(globalConfigService.getConfig(anyString())).thenReturn("50");
        
        user = User.builder().loginId("testuser").displayName("Test User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        board = Board.builder().boardName("Test Board").creator(user).build();
        ReflectionTestUtils.setField(board, "boardId", 1L);
        ReflectionTestUtils.setField(board, "boardUrl", "free");
        ReflectionTestUtils.setField(board, "isActive", true);
        ReflectionTestUtils.setField(board, "iconUrl", "icon.png");

        category = BoardCategory.builder().name("General").board(board).build();
        ReflectionTestUtils.setField(category, "categoryId", 1L);

        post = Post.builder().title("Test Post").contents("Test Contents").user(user).board(board).category(category).build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "likeCount", 0);
        ReflectionTestUtils.setField(post, "viewCount", 0);
    }

    // --- Create Post ---

    @Test
    @DisplayName("게시글 생성 성공 - BoardUrl 사용")
    void createPost_withBoardUrl_success() {
        PostCreateRequest request = new PostCreateRequest(null, "New Post", "New Contents", Collections.emptyList(), false, false, false, List.of(1L, 2L));
        
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
        verify(fileService, times(2)).associateFileWithEntity(anyLong(), eq(100L), eq("POST_CONTENT"));
        verify(pointService).addPoint(eq(1L), eq(50), eq("게시글 작성"), eq(100L), eq("POST"));
    }

    @Test
    @DisplayName("게시글 생성 성공 - 카테고리 포함")
    void createPost_withCategory_success() {
        PostCreateRequest request = new PostCreateRequest(1L, "New Post", "New Contents", Collections.emptyList(), false, false, false, null);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        postService.createPost(1L, 1L, request);

        verify(boardCategoryRepository).findById(1L);
    }

    @Test
    @DisplayName("게시글 생성 실패 - 공지사항 권한 없음")
    void createPost_notice_forbidden() {
        PostCreateRequest request = new PostCreateRequest(null, "Notice", "Contents", Collections.emptyList(), true, false, false, null);
        
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
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
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isEqualTo(post);
        assertThat(result.getViewCount()).isEqualTo(1); // incremented
        verify(viewHistoryRepository).save(any(ViewHistory.class));
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
        when(adminRepository.findByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.getPostById(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND); // Security requirement often masks as Not Found
    }

    @Test
    @DisplayName("게시글 목록 조회 - BoardUrl")
    void getPosts_byBoardUrl() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        // currentUserId가 null이므로 userBlockService가 호출되지 않음
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(Pageable.class))).thenReturn(Page.empty());

        postService.getPosts("free", null, null, null, Pageable.unpaged());

        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("인기 게시글 조회 - 로그인 사용자")
    void getTrendingPosts_loggedIn() {
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), anyList(), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        when(fileService.getRelatedIdsWithImages(anyList(), eq("POST_CONTENT"))).thenReturn(List.of(1L));
        when(fileService.getOneImageFileIdForPost(1L)).thenReturn(10L);
        
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
        PostUpdateRequest request = new PostUpdateRequest(null, "Updated Title", "Updated Contents", Collections.emptyList(), false, false, List.of(5L));
        
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Post updated = postService.updatePost(1L, 1L, request);

        assertThat(updated.getTitle()).isEqualTo("Updated Title");
        verify(fileService).associateFileWithEntity(eq(5L), eq(1L), eq("POST_CONTENT"));
        verify(postVersionRepository).save(any(PostVersion.class));
    }

    @Test
    @DisplayName("게시글 수정 실패 - 작성자 아님")
    void updatePost_forbidden() {
        PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", null, false, false, null);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        
        assertThatThrownBy(() -> postService.updatePost(2L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    // --- Delete Post ---

    @Test
    @DisplayName("게시글 삭제 성공")
    void deletePost_success() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        Tag tag = Tag.builder().tagName("Tag1").build();
        PostTag postTag = PostTag.builder().post(post).tag(tag).build();
        when(postTagRepository.findByPost(post)).thenReturn(List.of(postTag));

        postService.deletePost(1L, 1L);

        assertThat(post.getIsDeleted()).isTrue();
        verify(postTagRepository).deleteByPost(post);
        verify(pointService).forceSubtractPoint(eq(1L), eq(50), eq("게시글 삭제"), eq(1L), eq("POST"));
    }

    // --- Likes ---

    @Test
    @DisplayName("좋아요 성공")
    void likePost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(false);

        postService.likePost(1L, 1L);

        verify(postLikeRepository).save(any(PostLike.class));
        assertThat(post.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 실패 - 이미 좋아요 함")
    void likePost_alreadyLiked() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(true);

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("좋아요 취소 성공")
    void unlikePost_success() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsById(any(PostLikeId.class))).thenReturn(true);

        postService.unlikePost(1L, 1L);

        verify(postLikeRepository).deleteById(any(PostLikeId.class));
        assertThat(post.getLikeCount()).isEqualTo(-1); // Starts at 0, decremented
    }

    // --- Scraps ---

    @Test
    @DisplayName("스크랩 성공")
    void scrapPost_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(scrapRepository.existsById(any(ScrapId.class))).thenReturn(false);

        postService.scrapPost(1L, 1L, "My Scrap");

        verify(scrapRepository).save(any(Scrap.class));
    }

    @Test
    @DisplayName("스크랩 취소 성공")
    void unscrapPost_success() {
        when(scrapRepository.existsById(any(ScrapId.class))).thenReturn(true);

        postService.unscrapPost(1L, 1L);

        verify(scrapRepository).deleteById(any(ScrapId.class));
    }

    @Test
    @DisplayName("내 스크랩 조회")
    void getMyScraps_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(scrapRepository.findByUserOrderByCreatedAtDesc(eq(user), any(Pageable.class))).thenReturn(Page.empty());

        postService.getMyScraps(1L, Pageable.unpaged());

        verify(scrapRepository).findByUserOrderByCreatedAtDesc(eq(user), any(Pageable.class));
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
    @DisplayName("초안 삭제")
    void deleteDraftPost_success() {
        DraftPost existingDraft = DraftPost.builder().user(user).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByDraftIdAndUser(10L, user)).thenReturn(Optional.of(existingDraft));

        postService.deleteDraftPost(1L, 10L);

        verify(draftPostRepository).delete(existingDraft);
    }
    
    // --- View History ---
    
    @Test
    @DisplayName("조회 기록 업데이트 - 신규")
    void updateViewHistory_new() {
        ViewHistoryRequest request = new ViewHistoryRequest(100L, 5000L); // commentId, duration
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));
        when(commentRepository.findById(100L)).thenReturn(Optional.of(Comment.builder().build()));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).save(any(ViewHistory.class));
    }
    
    @Test
    @DisplayName("최근 본 게시글 조회")
    void getRecentlyViewedPosts() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(viewHistoryRepository.findByUserOrderByModifiedAtDesc(eq(user), any(Pageable.class)))
            .thenReturn(Page.empty());
            
        postService.getRecentlyViewedPosts(1L, Pageable.unpaged());
        
        verify(viewHistoryRepository).findByUserOrderByModifiedAtDesc(eq(user), any(Pageable.class));
    }

    // --- Misc ---

    @Test
    @DisplayName("공지사항 조회")
    void getNotices_success() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        // currentUserId가 null이므로 userBlockService가 호출되지 않음, blockedUserIds는 null
        when(postRepository.findNoticesByBoardId(eq(1L), eq(true), eq(false), isNull()))
            .thenReturn(List.of(post));
            
        List<Post> notices = postService.getNotices("free", null);
        
        assertThat(notices).hasSize(1);
    }

    @Test
    @DisplayName("태그별 게시글 조회")
    void getPostsByTag_success() {
        when(postTagRepository.findByTag_TagId(eq(1L), any(Pageable.class))).thenReturn(Page.empty());
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        lenient().when(fileService.getRelatedIdsWithImages(anyList(), eq("POST_CONTENT"))).thenReturn(Collections.emptyList());
        
        postService.getPostsByTag(1L, null, Pageable.unpaged());
        
        verify(postTagRepository).findByTag_TagId(eq(1L), any(Pageable.class));
    }
    
    @Test
    @DisplayName("내 게시글 조회")
    void getMyPosts_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByUserAndIsDeleted(eq(user), eq(false), any(Pageable.class))).thenReturn(Page.empty());
        // Page.empty()인 경우 getPostIdsWithImages가 빈 리스트를 받아 fileService가 호출되지 않음
        lenient().when(fileService.getRelatedIdsWithImages(anyList(), eq("POST_CONTENT"))).thenReturn(Collections.emptyList());
        
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
        when(postVersionRepository.findByPost_PostIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());
        postService.getPostVersions(1L);
        verify(postVersionRepository).findByPost_PostIdOrderByCreatedAtDesc(1L);
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
        lenient().when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
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
        lenient().when(postTagRepository.findByPost(post)).thenReturn(Collections.emptyList());
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
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        postService.incrementViewCount(1L);

        assertThat(post.getViewCount()).isEqualTo(1);
    }

    // --- Draft Posts ---

    @Test
    @DisplayName("초안 목록 조회")
    void getDraftPosts_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(draftPostRepository.findByUserOrderByModifiedAtDesc(eq(user), any(Pageable.class)))
            .thenReturn(Page.empty());

        DraftListResponse response = postService.getDraftPosts(1L, Pageable.unpaged());

        assertThat(response).isNotNull();
        verify(draftPostRepository).findByUserOrderByModifiedAtDesc(eq(user), any(Pageable.class));
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
        Tag tag1 = Tag.builder().tagName("Java").build();
        Tag tag2 = Tag.builder().tagName("Spring").build();
        PostTag postTag1 = PostTag.builder().post(post).tag(tag1).build();
        PostTag postTag2 = PostTag.builder().post(post).tag(tag2).build();

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postTagRepository.findByPost(post)).thenReturn(List.of(postTag1, postTag2));

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
        Admin admin = Admin.builder().user(user).board(board).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.of(admin));

        boolean result = postService.isBoardAdmin(1L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("게시판 관리자 확인 - 일반 유저")
    void isBoardAdmin_normalUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());

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
        when(userBlockService.getBlockedUserIds(1L)).thenReturn(Collections.emptyList());
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), anyList(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(post)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(fileService.getRelatedIdsWithImages(anyList(), eq("POST_CONTENT"))).thenReturn(List.of(1L));
        when(fileService.getOneImageFileIdForPost(1L)).thenReturn(20L);
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
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(post)));
        when(fileService.getRelatedIdsWithImages(anyList(), eq("POST_CONTENT"))).thenReturn(Collections.emptyList());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, null);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("게시판 최신 게시글 조회 - 결과 없음")
    void getLatestPostsByBoard_empty() {
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), isNull(), isNull(), isNull(), any(Pageable.class)))
            .thenReturn(Page.empty());

        List<PostSummary> result = postService.getLatestPostsByBoard(1L, 5, null);

        assertThat(result).isEmpty();
    }

    // --- Edge Cases ---

    @Test
    @DisplayName("게시글 생성 실패 - 비활성 게시판")
    void createPost_inactiveBoard() {
        ReflectionTestUtils.setField(board, "isActive", false);
        PostCreateRequest request = new PostCreateRequest(null, "Title", "Content", null, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 생성 - 카테고리 권한 체크 (SUPER_ADMIN)")
    void createPost_categoryPermission_superAdmin() {
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);
        ReflectionTestUtils.setField(category, "minWriteRole", "SUPER_ADMIN");
        PostCreateRequest request = new PostCreateRequest(1L, "Title", "Content", null, false, false, false, null);

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
        PostCreateRequest request = new PostCreateRequest(1L, "Title", "Content", null, false, false, false, null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(boardRepository.findById(1L)).thenReturn(Optional.of(board));
        when(boardCategoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.createPost(1L, 1L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("게시글 수정 실패 - 이미 삭제된 게시글")
    void updatePost_alreadyDeleted() {
        ReflectionTestUtils.setField(post, "isDeleted", true);
        PostUpdateRequest request = new PostUpdateRequest(null, "Title", "Content", null, false, false, null);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.updatePost(1L, 1L, request))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 삭제 실패 - 이미 삭제된 게시글")
    void deletePost_alreadyDeleted() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("좋아요 실패 - 삭제된 게시글")
    void likePost_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.likePost(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("좋아요 취소 실패 - 삭제된 게시글")
    void unlikePost_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.unlikePost(1L, 1L))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("스크랩 실패 - 삭제된 게시글")
    void scrapPost_deletedPost() {
        ReflectionTestUtils.setField(post, "isDeleted", true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.scrapPost(1L, 1L, "remark"))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
    }

    @Test
    @DisplayName("게시글 조회 실패 - 차단된 사용자")
    void getPostById_blockedUser() {
        when(postRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(post));
        when(userBlockService.getBlockedUserIds(2L)).thenReturn(List.of(1L));

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
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

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
        when(adminRepository.findByUserAndBoardAndIsActive(otherUser, board, true)).thenReturn(Optional.of(admin));
        when(viewHistoryRepository.findByUserAndPost(otherUser, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

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
        when(adminRepository.findByUserAndBoardAndIsActive(user, board, true)).thenReturn(Optional.empty());
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        Post result = postService.getPostById(1L, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("공지사항 요약 조회")
    void getNoticeSummaries_success() {
        when(boardRepository.findByBoardUrl("free")).thenReturn(Optional.of(board));
        when(postRepository.findNoticesByBoardId(eq(1L), eq(true), eq(false), isNull()))
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
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        postService.updateViewHistory(1L, 1L, request);

        verify(commentRepository, never()).findById(any());
    }

    @Test
    @DisplayName("조회 기록 업데이트 - durationMs가 null")
    void updateViewHistory_nullDuration() {
        ViewHistoryRequest request = new ViewHistoryRequest(null, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(viewHistoryRepository.findByUserAndPost(user, post)).thenReturn(Optional.empty());
        when(viewHistoryRepository.save(any(ViewHistory.class))).thenAnswer(i -> i.getArgument(0));

        postService.updateViewHistory(1L, 1L, request);

        verify(viewHistoryRepository).save(any(ViewHistory.class));
    }
}