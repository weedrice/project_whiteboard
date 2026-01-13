package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentTest {

    @Test
    @DisplayName("Comment 생성 및 내용 수정")
    void createAndUpdateComment() {
        Post post = Post.builder().build();
        User user = User.builder().build();
        
        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content("Old Content")
                .build();

        assertThat(comment.getContent()).isEqualTo("Old Content");
        assertThat(comment.getLikeCount()).isZero();

        comment.updateContent("New Content");
        assertThat(comment.getContent()).isEqualTo("New Content");
    }

    @Test
    @DisplayName("댓글 삭제")
    void deleteComment() {
        Comment comment = Comment.builder().build();
        comment.deleteComment();
        assertThat(comment.getIsDeleted()).isTrue();
    }

    @Test
    @DisplayName("좋아요 수 증감")
    void likeCount() {
        Comment comment = Comment.builder().build();
        
        comment.incrementLikeCount();
        assertThat(comment.getLikeCount()).isEqualTo(1);
        
        comment.decrementLikeCount();
        assertThat(comment.getLikeCount()).isZero();
    }
}