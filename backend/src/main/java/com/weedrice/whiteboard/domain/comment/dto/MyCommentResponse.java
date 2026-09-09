package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MyCommentResponse {
    private Long commentId;
    private String content;
    private PostInfo post;
    private int likeCount;
    private LocalDateTime createdAt;

    @Getter
    @Builder
    public static class PostInfo {
        private Long postId;
        private String title;
        private String boardUrl;
        private String boardName;
    }

    public static MyCommentResponse from(Comment comment, CommentPostSnapshot post) {
        return MyCommentResponse.builder()
                .commentId(comment.getCommentId())
                .content(comment.getContent())
                .post(PostInfo.builder()
                        .postId(post.postId())
                        .title(post.title())
                        .boardUrl(post.boardUrl())
                        .boardName(post.boardName())
                        .build())
                .likeCount(comment.getLikeCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
