package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostTest {

    @Test
    @DisplayName("Post 생성 빌더 테스트")
    void createPost() {
        User user = User.builder().build();
        Board board = Board.builder().build();
        
        Post post = Post.builder()
                .title("Title")
                .contents("Contents")
                .user(user)
                .board(board)
                .build();

        assertThat(post.getTitle()).isEqualTo("Title");
        assertThat(post.getContents()).isEqualTo("Contents");
        assertThat(post.getViewCount()).isZero();
        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("조회수 증가")
    void incrementViewCount() {
        Post post = Post.builder().build();
        post.incrementViewCount();
        assertThat(post.getViewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 증감")
    void likeCount() {
        Post post = Post.builder().build();
        
        post.incrementLikeCount();
        assertThat(post.getLikeCount()).isEqualTo(1);
        
        post.decrementLikeCount();
        assertThat(post.getLikeCount()).isZero();
    }

    @Test
    @DisplayName("댓글 수 증감")
    void commentCount() {
        Post post = Post.builder().build();
        
        post.incrementCommentCount();
        assertThat(post.getCommentCount()).isEqualTo(1);
        
        post.decrementCommentCount();
        assertThat(post.getCommentCount()).isZero();
    }

    @Test
    @DisplayName("게시글 수정")
    void updatePost() {
        Post post = Post.builder()
                .title("Old Title")
                .contents("Old Contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .build();

        BoardCategory category = BoardCategory.builder().name("Category").build();

        // category, title, contents, isNsfw, isSpoiler
        post.updatePost(category, "New Title", "New Contents", true, true);

        assertThat(post.getTitle()).isEqualTo("New Title");
        assertThat(post.getContents()).isEqualTo("New Contents");
        assertThat(post.getCategory()).isEqualTo(category);
        assertThat(post.getIsNotice()).isFalse(); // 공지 여부는 수정 불가 (별도 메서드 없으면)
        assertThat(post.getIsNsfw()).isTrue();
        assertThat(post.getIsSpoiler()).isTrue();
    }

    @Test
    @DisplayName("게시글 삭제")
    void deletePost() {
        Post post = Post.builder().build();
        post.deletePost();
        assertThat(post.getIsDeleted()).isTrue();
    }
}