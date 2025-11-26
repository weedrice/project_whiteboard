package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
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
    }

    public static MyCommentResponse from(Comment comment) {
        return MyCommentResponse.builder()
                .commentId(comment.getCommentId())
                .content(comment.getContent())
                .post(PostInfo.builder()
                        .postId(comment.getPost().getPostId())
                        .title(comment.getPost().getTitle())
                        .build())
                .likeCount(comment.getLikeCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
