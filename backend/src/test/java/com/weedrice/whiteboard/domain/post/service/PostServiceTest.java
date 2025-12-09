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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    @InjectMocks
    private PostService postService;

    private User user;
    private Board board;
    private Post post;
    private BoardCategory category;

    @BeforeEach
    void setUp() {
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
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
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

        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
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
        when(postRepository.findByBoardIdAndCategoryId(eq(1L), any(), any(), any())).thenReturn(Page.empty());

        postService.getPosts("free", null, null, Pageable.unpaged());

        verify(postRepository).findByBoardIdAndCategoryId(eq(1L), any(), any(), any());
    }

    @Test
    @DisplayName("인기 게시글 조회 - 로그인 사용자")
    void getTrendingPosts_loggedIn() {
        when(postRepository.findTrendingPosts(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        
        File file = File.builder().relatedId(1L).relatedType("POST_CONTENT").mimeType("image/jpeg").build();
        ReflectionTestUtils.setField(file, "fileId", 10L);
        when(fileService.getFilesByRelatedEntityIn(anyList(), eq("POST_CONTENT"))).thenReturn(List.of(file));
        
        when(postLikeRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(scrapRepository.findByUserAndPostIn(user, List.of(post))).thenReturn(Collections.emptyList());
        when(boardSubscriptionRepository.findByUserAndBoardIn(eq(user), anyList())).thenReturn(Collections.emptyList());

        List<PostSummary> result = postService.getTrendingPosts(10, 1L);

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
        when(postRepository.findByBoard_BoardIdAndIsNoticeAndIsDeletedOrderByCreatedAtDesc(1L, true, false))
            .thenReturn(List.of(post));
            
        List<Post> notices = postService.getNotices("free");
        
        assertThat(notices).hasSize(1);
    }

    @Test
    @DisplayName("태그별 게시글 조회")
    void getPostsByTag_success() {
        when(postRepository.findByTagId(eq(1L), any(Pageable.class))).thenReturn(Page.empty());
        
        postService.getPostsByTag(1L, Pageable.unpaged());
        
        verify(postRepository).findByTagId(eq(1L), any(Pageable.class));
    }
    
    @Test
    @DisplayName("내 게시글 조회")
    void getMyPosts_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(postRepository.findByUserAndIsDeleted(eq(user), eq(false), any(Pageable.class))).thenReturn(Page.empty());
        
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
}