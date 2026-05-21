package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PostContentSummaryExtractorTest {

    private PostContentSummaryExtractor extractor;
    private Post post;

    @BeforeEach
    void setUp() {
        extractor = new PostContentSummaryExtractor();

        User author = User.builder().displayName("author").build();
        ReflectionTestUtils.setField(author, "userId", 1L);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(author)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        post = Post.builder()
                .title("title")
                .contents("<p>Hello</p><img src=\"https://cdn.example.com/image.png\" />")
                .user(author)
                .board(board)
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);
    }

    @Test
    @DisplayName("썸네일 파일이 없으면 본문 첫 이미지 URL을 썸네일로 사용한다")
    void resolveThumbnail_fallsBackToHtmlImage() {
        PostThumbnailInfo thumbnailInfo = extractor.resolveThumbnail(post, Set.of(), Map.of());

        assertThat(thumbnailInfo.thumbnailUrl()).isEqualTo("https://cdn.example.com/image.png");
        assertThat(thumbnailInfo.hasImage()).isTrue();
    }

    @Test
    @DisplayName("본문 iframe에서 첫 비디오 embed URL을 추출한다")
    void extractFirstVideoEmbedFromContent_success() {
        String url = extractor.extractFirstVideoEmbedFromContent(
                "<p>x</p><iframe src=\"https://www.youtube.com/embed/abc123\"></iframe>");

        assertThat(url).isEqualTo("https://www.youtube.com/embed/abc123");
    }

    @Test
    @DisplayName("요약 생성 시 HTML 블록 사이 텍스트가 붙지 않도록 공백을 보존한다")
    void extractSummary_preservesSpacingBetweenHtmlBlocks() {
        Post htmlPost = Post.builder()
                .title("title")
                .contents("<p>첫 문단</p><p>둘째 문단</p><ul><li>목록 하나</li><li>목록 둘</li></ul>")
                .user(post.getUser())
                .board(post.getBoard())
                .build();

        String summary = extractor.extractSummary(htmlPost);

        assertThat(summary).isEqualTo("첫 문단 둘째 문단 목록 하나 목록 둘");
    }
}
