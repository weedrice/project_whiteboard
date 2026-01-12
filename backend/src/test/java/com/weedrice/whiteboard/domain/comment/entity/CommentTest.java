package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    private User user;
    private Post post;
    private Comment comment;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@test.com")
                .password("password")
                .displayName("Test User")
                .build();

        post = Post.builder()
                .title("Test Post")
                .contents("Test Contents")
                .user(user)
                .build();

        comment = Comment.builder()
                .content("Test Comment")
                .user(user)
                .post(post)
                .build();
    }

    @Test
    @DisplayName("댓글 생성 시 초기값 확인")
    void createComment_initialValues() {
        // then
        assertThat(comment.getLikeCount()).isEqualTo(0);
        assertThat(comment.getIsDeleted()).isFalse();
    }

    @Test
    @DisplayName("좋아요 수 증가")
    void incrementLikeCount_success() {
        // when
        comment.incrementLikeCount();

        // then
        assertThat(comment.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요 수 감소")
    void decrementLikeCount_success() {
        // given
        comment.incrementLikeCount();
        comment.incrementLikeCount();

        // when
        comment.decrementLikeCount();

        // then
        assertThat(comment.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("댓글 내용 수정")
    void updateContent_success() {
        // when
        comment.updateContent("Updated Comment");

        // then
        assertThat(comment.getContent()).isEqualTo("Updated Comment");
    }

    @Test
    @DisplayName("댓글 삭제")
    void deleteComment_success() {
        // when
        comment.deleteComment();

        // then
        assertThat(comment.getIsDeleted()).isTrue();
    }
}
