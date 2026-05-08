package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.entity.Post;
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
                new PostContentSummaryExtractor());
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
                .contents("<p>Hello</p><img src=\"https://cdn.example.com/thumb.png\" />")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        List<PostSummary> trending = postSummaryAssembler.assembleTrendingPosts(List.of(post), null);
        List<PostSummary> latest = postSummaryAssembler.assembleLatestPosts(List.of(post), null);

        assertThat(trending.get(0).getSummary()).isEqualTo(latest.get(0).getSummary());
        assertThat(trending.get(0).getThumbnailUrl()).isEqualTo(latest.get(0).getThumbnailUrl());
        assertThat(trending.get(0).getContentsExcerpt()).isNotNull();
        assertThat(trending.get(0).getFirstMediaType()).isEqualTo("image");
        assertThat(trending.get(0).getFirstMediaUrl()).isEqualTo("https://cdn.example.com/thumb.png");
        assertThat(latest.get(0).getContentsExcerpt()).isNull();
        assertThat(latest.get(0).getFirstMediaType()).isNull();
        assertThat(latest.get(0).getFirstMediaUrl()).isNull();
    }
}
