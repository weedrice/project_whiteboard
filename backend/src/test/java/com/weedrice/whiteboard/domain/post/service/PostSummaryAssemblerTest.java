package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostListSummaryProjection;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSummaryAssemblerTest {

    @Mock
    private FileService fileService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private ScrapRepository scrapRepository;
    @Mock
    private BoardSubscriptionRepository boardSubscriptionRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private AdminRepository adminRepository;

    private PostSummaryAssembler postSummaryAssembler;
    private FeedPostSummaryAssembler feedPostSummaryAssembler;

    @BeforeEach
    void setUp() {
        postSummaryAssembler = new PostSummaryAssembler(
                fileService,
                commentRepository,
                new BoardAccessPolicy(adminRepository),
                new PostInteractionContextResolver(
                        userRepository,
                        postLikeRepository,
                        scrapRepository,
                        boardSubscriptionRepository),
                PostContentSummaryExtractorFixtures.withNoviisCdn());
        feedPostSummaryAssembler = new FeedPostSummaryAssembler(
                fileService,
                new PostInteractionContextResolver(
                        userRepository,
                        postLikeRepository,
                        scrapRepository,
                        boardSubscriptionRepository),
                PostContentSummaryExtractorFixtures.withNoviisCdn());
    }

    @Test
    @DisplayName("검색 페이지 요약에 이미지 여부와 row number를 조립한다")
    void assembleSearchPage_assignsImageFlagAndRowNumbers() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);

        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        Post firstPost = Post.builder()
                .title("First")
                .contents("Contents")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(firstPost, "postId", 100L);
        Post secondPost = Post.builder()
                .title("Second")
                .contents("Contents")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(secondPost, "postId", 99L);

        when(fileService.getFirstImageFileIdsForPosts(List.of(100L, 99L)))
                .thenReturn(Map.of(100L, 1000L));

        PageImpl<Post> page = new PageImpl<>(
                List.of(firstPost, secondPost),
                PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"))),
                5);

        List<PostSummary> summaries = postSummaryAssembler.assembleSearchPage(page).getContent();

        assertThat(summaries).extracting(PostSummary::getRowNum).containsExactly(3L, 2L);
        assertThat(summaries).extracting(PostSummary::isHasImage).containsExactly(true, false);
        assertThat(summaries).extracting(PostSummary::getThumbnailUrl)
                .containsExactly("/api/v1/files/1000/variants/thumbnail", null);
    }

    @Test
    @DisplayName("검색 페이지 rowNum은 createdAt 또는 postId 중 첫 정렬 기준을 따른다")
    void assembleSearchPage_usesFirstRowNumberSortDirection() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        Post firstPost = post(100L, author, board);
        Post secondPost = post(99L, author, board);

        when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        PageImpl<Post> createdAtDescPage = new PageImpl<>(
                List.of(firstPost, secondPost),
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("postId"))),
                5);
        PageImpl<Post> createdAtAscPage = new PageImpl<>(
                List.of(firstPost, secondPost),
                PageRequest.of(0, 2, Sort.by(Sort.Order.asc("createdAt"), Sort.Order.desc("postId"))),
                5);
        PageImpl<Post> postIdDescPage = new PageImpl<>(
                List.of(firstPost, secondPost),
                PageRequest.of(0, 2, Sort.by(Sort.Order.desc("postId"), Sort.Order.asc("createdAt"))),
                5);

        assertThat(postSummaryAssembler.assembleSearchPage(createdAtDescPage).getContent())
                .extracting(PostSummary::getRowNum)
                .containsExactly(5L, 4L);
        assertThat(postSummaryAssembler.assembleSearchPage(createdAtAscPage).getContent())
                .extracting(PostSummary::getRowNum)
                .containsExactly(1L, 2L);
        assertThat(postSummaryAssembler.assembleSearchPage(postIdDescPage).getContent())
                .extracting(PostSummary::getRowNum)
                .containsExactly(5L, 4L);
    }

    @Test
    @DisplayName("태그 페이지 요약은 이미지 여부만 조립하고 기존 빈 필드는 보존한다")
    void assembleTagPage_assignsOnlyImageFlag() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);

        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        Post firstPost = Post.builder()
                .title("First")
                .contents("Contents")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(firstPost, "postId", 100L);
        Post secondPost = Post.builder()
                .title("Second")
                .contents("Contents")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(secondPost, "postId", 99L);

        when(fileService.getFirstImageFileIdsForPosts(List.of(100L, 99L)))
                .thenReturn(Map.of(100L, 1000L));

        PageImpl<Post> page = new PageImpl<>(
                List.of(firstPost, secondPost),
                PageRequest.of(0, 2),
                2);

        List<PostSummary> summaries = postSummaryAssembler.assembleTagPage(page).getContent();

        assertThat(summaries).extracting(PostSummary::isHasImage).containsExactly(true, false);
        assertThat(summaries).extracting(PostSummary::getThumbnailUrl)
                .containsExactly("/api/v1/files/1000/variants/thumbnail", null);
        assertThat(summaries).extracting(PostSummary::getRowNum).containsOnlyNulls();
        assertThat(summaries).extracting(PostSummary::getInquiryAnswered).containsOnlyNulls();
    }

    @Test
    @DisplayName("문의글 answered 상태를 게시글 목록 단위로 배치 조회한다")
    void assembleBoardPage_resolvesInquiryAnsweredInBatch() {
        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);

        Board inquiryBoard = Board.builder()
                .boardName("Inquiry")
                .boardUrl("inquiry")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(inquiryBoard, "boardId", 10L);

        Post inquiryPost = Post.builder()
                .title("Question")
                .contents("Contents")
                .user(author)
                .board(inquiryBoard)
                .build();
        ReflectionTestUtils.setField(inquiryPost, "postId", 100L);

        when(commentRepository.findPostIdsWithNonAuthorCommentsByPostIds(List.of(100L)))
                .thenReturn(List.of(100L));

        PageImpl<Post> page = new PageImpl<>(List.of(inquiryPost), PageRequest.of(0, 20), 1);

        PostSummary summary = postSummaryAssembler
                .assembleBoardPage(page, PageRequest.of(0, 20), false, true)
                .getContent()
                .get(0);

        assertThat(summary.getInquiryAnswered()).isTrue();
        verify(commentRepository).findPostIdsWithNonAuthorCommentsByPostIds(List.of(100L));
    }

    @Test
    @DisplayName("트렌딩과 최신글은 공통 조립 규칙을 쓰되 피드 전용 필드만 다르게 채운다")
    void assembleFeedSummaries_shareCommonRulesWithDifferentOptions() {
        when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);

        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .iconUrl("icon.png")
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        Post post = Post.builder()
                .title("Title")
                .contents("<p>Hello</p>"
                        + "<img src=\"https://tracker.example/pixel.gif\" />"
                        + "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>"
                        + "<img src=\"https://cdn.noviis.kr/thumb.png\" />")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        List<FeedPostSummary> trending = feedPostSummaryAssembler.assembleTrendingPosts(List.of(post), null);
        List<FeedPostSummary> latest = feedPostSummaryAssembler.assembleLatestPosts(List.of(post), null);
        PostSummary listSummary = PostSummary.from(
                post,
                trending.get(0).getThumbnailUrl(),
                board.getIconUrl(),
                false,
                false,
                false,
                trending.get(0).isHasImage(),
                trending.get(0).getSummary());

        assertThat(trending.get(0).getSummary()).isEqualTo(latest.get(0).getSummary());
        assertThat(trending.get(0).getThumbnailUrl()).isEqualTo(latest.get(0).getThumbnailUrl());
        assertThat(trending.get(0).getThumbnailUrl()).isEqualTo("https://cdn.noviis.kr/thumb.png");
        assertThat(trending.get(0).getAuthor().getDisplayName()).isEqualTo(listSummary.getAuthor().getDisplayName());
        assertThat(trending.get(0).getBoardUrl()).isEqualTo(listSummary.getBoardUrl());
        assertThat(trending.get(0).getBoardIconUrl()).isEqualTo(listSummary.getBoardIconUrl());
        assertThat(trending.get(0).getContentsExcerpt()).isNotNull();
        assertThat(trending.get(0).getFirstMediaType()).isEqualTo("video");
        assertThat(trending.get(0).getFirstMediaUrl()).isEqualTo("https://www.youtube.com/embed/abc123");
        assertThat(latest.get(0).getContentsExcerpt()).isNull();
        assertThat(latest.get(0).getFirstMediaType()).isNull();
        assertThat(latest.get(0).getFirstMediaUrl()).isNull();
    }

    @Test
    void assembleTrendingPosts_skipsInvalidIframeWhenChoosingFirstMedia() {
        when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        Post post = Post.builder()
                .title("Title")
                .contents("<iframe src=\"https://tracker.example/embed/ignored\"></iframe>"
                        + "<img src=\"/api/v1/files/78\">"
                        + "<iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        FeedPostSummary summary = feedPostSummaryAssembler.assembleTrendingPosts(List.of(post), null).get(0);

        assertThat(summary.getFirstMediaType()).isEqualTo("image");
        assertThat(summary.getFirstMediaUrl()).isEqualTo("/api/v1/files/78/variants/thumbnail");
    }

    @Test
    void assembleTrendingPosts_keepsContentImageIdentitySeparateFromListThumbnail() {
        when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Map.of(100L, 55L));

        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        Post post = Post.builder()
                .title("Title")
                .contents("<img src=\"/api/v1/files/78\">")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        FeedPostSummary summary = feedPostSummaryAssembler.assembleTrendingPosts(List.of(post), null).get(0);

        assertThat(summary.getThumbnailUrl()).isEqualTo("/api/v1/files/55/variants/thumbnail");
        assertThat(summary.getFirstMediaType()).isEqualTo("image");
        assertThat(summary.getFirstMediaUrl()).isEqualTo("/api/v1/files/78/variants/thumbnail");
    }

    @Test
    void assembleTrendingPosts_doesNotExposeInvalidIframeAsVideo() {
        when(fileService.getFirstImageFileIdsForPosts(anyList()))
                .thenReturn(Collections.emptyMap());

        User author = User.builder().displayName("Author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);
        Board board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        Post post = Post.builder()
                .title("Title")
                .contents("<iframe src=\"https://tracker.example/embed/ignored\"></iframe>")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        FeedPostSummary summary = feedPostSummaryAssembler.assembleTrendingPosts(List.of(post), null).get(0);

        assertThat(summary.getFirstMediaType()).isNull();
        assertThat(summary.getFirstMediaUrl()).isNull();
    }

    @Test
    @DisplayName("게시글 목록 projection을 기존 PostSummary shape로 조립한다")
    void assembleBoardListProjectionPage_buildsPostSummaryShape() {
        PostListSummaryProjection projection = new PostListSummaryProjection(
                100L,
                10L,
                20L,
                "Question",
                1L,
                null,
                "Author",
                "profile.png",
                null,
                "General",
                7,
                3,
                2,
                true,
                false,
                false,
                true,
                null,
                "inquiry",
                "Inquiry",
                "icon.png",
                "<p>Hello <strong>world</strong></p>");

        when(fileService.getFirstImageFileIdsForPosts(List.of(100L)))
                .thenReturn(Map.of(100L, 1000L));
        when(commentRepository.findPostIdsWithNonAuthorCommentsByPostIds(List.of(100L)))
                .thenReturn(List.of(100L));

        PageImpl<PostListSummaryProjection> page = new PageImpl<>(
                List.of(projection),
                PageRequest.of(0, 20, Sort.by(Sort.Order.desc("createdAt"))),
                1);

        PostSummary summary = postSummaryAssembler
                .assembleBoardListProjectionPage(page, page.getPageable(), true, true)
                .getContent()
                .get(0);

        assertThat(summary.getPostId()).isEqualTo(100L);
        assertThat(summary.getBoardId()).isEqualTo(10L);
        assertThat(summary.getCategory().getName()).isEqualTo("General");
        assertThat(summary.getAuthor().getDisplayName()).isEqualTo("Author");
        assertThat(summary.isHasImage()).isTrue();
        assertThat(summary.getThumbnailUrl()).isEqualTo("/api/v1/files/1000/variants/thumbnail");
        assertThat(summary.getInquiryAnswered()).isTrue();
        assertThat(summary.getSummary()).isEqualTo("Hello world");
        assertThat(summary.getRowNum()).isEqualTo(1L);
        assertThat(summary.getBoardUrl()).isEqualTo("inquiry");
        assertThat(summary.getBoardIconUrl()).isEqualTo("icon.png");
    }

    private Post post(Long postId, User author, Board board) {
        Post post = Post.builder()
                .title("Post " + postId)
                .contents("Contents")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", postId);
        return post;
    }
}
