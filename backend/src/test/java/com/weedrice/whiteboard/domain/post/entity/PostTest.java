package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    private User user;
    private Board board;
    private BoardCategory category;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@test.com")
                .password("password")
                .displayName("Test User")
                .build();

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();

        category = BoardCategory.builder()
                .name("General")
                .board(board)
                .build();

        post = Post.builder()
                .board(board)
                .user(user)
                .category(category)
                .title("Test Post")
                .contents("Test Contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .build();
    }

    @Test
    @DisplayName("게시글 생성 시 초기값 확인")
    void createPost_initialValues() {
        // then
        assertThat(post.getViewCount()).isEqualTo(0);
        assertThat(post.getLikeCount()).isEqualTo(0);
        assertThat(post.getCommentCount()).isEqualTo(0);
        assertThat(post.getIsDeleted()).isFalse();
    }

    @Test
    @DisplayName("조회수 증가")
    void incrementViewCount_success() {
        // when
        post.incrementViewCount();

        // then
        assertThat(post.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 증가")
    void incrementLikeCount_success() {
        // when
        post.incrementLikeCount();

        // then
        assertThat(post.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 감소")
    void decrementLikeCount_success() {
        // given
        post.incrementLikeCount();
        post.incrementLikeCount();

        // when
        post.decrementLikeCount();

        // then
        assertThat(post.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글 수 증가")
    void incrementCommentCount_success() {
        // when
        post.incrementCommentCount();

        // then
        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글 수 감소")
    void decrementCommentCount_success() {
        // given
        post.incrementCommentCount();
        post.incrementCommentCount();

        // when
        post.decrementCommentCount();

        // then
        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시글 수정")
    void updatePost_success() {
        // given
        BoardCategory newCategory = BoardCategory.builder()
                .name("New Category")
                .board(board)
                .build();

        // when
        post.updatePost(newCategory, "Updated Title", "Updated Contents", true, true);

        // then
        assertThat(post.getTitle()).isEqualTo("Updated Title");
        assertThat(post.getContents()).isEqualTo("Updated Contents");
        assertThat(post.getCategory()).isEqualTo(newCategory);
        assertThat(post.getIsNsfw()).isTrue();
        assertThat(post.getIsSpoiler()).isTrue();
    }

    @Test
    @DisplayName("게시글 삭제")
    void deletePost_success() {
        // when
        post.deletePost();

        // then
        assertThat(post.getIsDeleted()).isTrue();
    }
}
