package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommentDtoTest {

    @Test
    @DisplayName("CommentResponse.from() - 정상 댓글 및 AuthorInfo")
    void commentResponseFrom_normal() {
        // given
        User user = User.builder().displayName("Test User").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "profileImageUrl", "profile.jpg");

        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).title("Post Title").build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "board", board);

        Comment comment = Comment.builder()
                .content("Test Content")
                .user(user)
                .post(post)
                .depth(0)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 1L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(comment, "likeCount", 5);

        // when
        CommentResponse response = CommentResponse.from(comment);
        response.setChildren(new ArrayList<>()); // Test setter

        // then
        assertThat(response.getCommentId()).isEqualTo(1L);
        assertThat(response.getContent()).isEqualTo("Test Content");
        assertThat(response.getAuthor().getDisplayName()).isEqualTo("Test User");
        assertThat(response.getAuthor().getUserId()).isEqualTo(1L);
        assertThat(response.getAuthor().getProfileImageUrl()).isEqualTo("profile.jpg");
        assertThat(response.getPostId()).isEqualTo(1L);
        assertThat(response.getBoardUrl()).isEqualTo("free");
        assertThat(response.getPostTitle()).isEqualTo("Post Title");
        assertThat(response.getChildren()).isEmpty();
        assertThat(response.getParentId()).isNull();
    }

    @Test
    @DisplayName("CommentResponse.from() - 삭제된 댓글")
    void commentResponseFrom_deleted() {
        // given
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).title("Post Title").build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "board", board);

        Comment comment = Comment.builder()
                .content("Original Content")
                .post(post)
                .depth(0)
                .build();
        comment.deleteComment();

        // when
        CommentResponse response = CommentResponse.from(comment);

        // then
        assertThat(response.isDeleted()).isTrue();
        assertThat(response.getContent()).isEqualTo("삭제된 댓글입니다.");
        assertThat(response.getAuthor()).isNull();
    }

    @Test
    @DisplayName("CommentListResponse.from()")
    void commentListResponseFrom() {
        // given
        User user = User.builder().displayName("User").build();
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).title("Title").build();
        ReflectionTestUtils.setField(post, "board", board);
        
        Comment comment1 = Comment.builder().content("C1").user(user).post(post).depth(0).build();
        Comment comment2 = Comment.builder().content("C2").user(user).post(post).depth(0).build();
        
        Page<Comment> page = new PageImpl<>(List.of(comment1, comment2), PageRequest.of(0, 10), 2);

        // when
        CommentListResponse response = CommentListResponse.from(page);

        // then
        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
    }

    @Test
    @DisplayName("MyCommentResponse.from()")
    void myCommentResponseFrom() {
        // given
        Board board = Board.builder().boardName("Board").boardUrl("url").build();
        Post post = Post.builder().board(board).title("Title").build();
        ReflectionTestUtils.setField(post, "postId", 1L);
        ReflectionTestUtils.setField(post, "board", board);

        Comment comment = Comment.builder()
                .content("My Comment")
                .post(post)
                .depth(0)
                .build();
        ReflectionTestUtils.setField(comment, "commentId", 10L);
        ReflectionTestUtils.setField(comment, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(comment, "likeCount", 3);

        // when
        MyCommentResponse response = MyCommentResponse.from(comment);

        // then
        assertThat(response.getCommentId()).isEqualTo(10L);
        assertThat(response.getContent()).isEqualTo("My Comment");
        assertThat(response.getPost().getPostId()).isEqualTo(1L);
        assertThat(response.getPost().getTitle()).isEqualTo("Title");
        assertThat(response.getPost().getBoardUrl()).isEqualTo("url");
        assertThat(response.getPost().getBoardName()).isEqualTo("Board");
        assertThat(response.getLikeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("CommentResponse.from() - 유저 정보 없는 댓글")
    void commentResponseFrom_noUser() {
        // given
        Board board = Board.builder().boardUrl("free").build();
        Post post = Post.builder().board(board).title("Title").build();
        ReflectionTestUtils.setField(post, "board", board);

        Comment comment = Comment.builder()
                .content("No User Comment")
                .post(post)
                .depth(0)
                .build();
        // user is null

        // when
        CommentResponse response = CommentResponse.from(comment);

        // then
        assertThat(response.getAuthor()).isNull();
    }
}
