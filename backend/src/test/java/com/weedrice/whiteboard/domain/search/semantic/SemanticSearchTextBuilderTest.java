package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSearchTextBuilderTest {

    private final SemanticSearchTextBuilder textBuilder = new SemanticSearchTextBuilder();

    @Test
    void buildPostText_stripsHtmlAndIncludesBoardAndTitle() {
        User user = User.builder().displayName("writer").build();
        Board board = Board.builder().boardName("free board").boardUrl("free").creator(user).build();
        Post post = Post.builder()
                .board(board)
                .user(user)
                .title("search title")
                .contents("<p>body <strong>bold</strong></p>")
                .build();

        String result = textBuilder.buildPostText(post);

        assertThat(result).contains("post", "free board", "search title", "body bold");
        assertThat(result).doesNotContain("<p>", "<strong>");
    }

    @Test
    void buildCommentText_includesPostTitleAndBoard() {
        User user = User.builder().displayName("writer").build();
        Board board = Board.builder().boardName("qna").boardUrl("qna").creator(user).build();
        Post post = Post.builder().board(board).user(user).title("post title").contents("body").build();
        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content("<span>comment content</span>")
                .build();

        String result = textBuilder.buildCommentText(comment);

        assertThat(result).contains("comment", "qna", "post title", "comment content");
        assertThat(result).doesNotContain("<span>");
    }

    @Test
    void buildPostText_decodesPreservedHtmlBeforeEmbedding() {
        User user = User.builder().displayName("writer").build();
        Board board = Board.builder().boardName("free").boardUrl("free").creator(user).build();
        String encoded = Base64.getEncoder().encodeToString(
                "<section><p>숨겨지면 안 되는 본문</p></section>".getBytes(StandardCharsets.UTF_8));
        Post post = Post.builder()
                .board(board)
                .user(user)
                .title("title")
                .contents("<div class=\"noviis-sandboxed-post-html\" data-value=\"" + encoded + "\"></div>")
                .build();

        assertThat(textBuilder.buildPostText(post)).contains("숨겨지면 안 되는 본문");
    }
}
