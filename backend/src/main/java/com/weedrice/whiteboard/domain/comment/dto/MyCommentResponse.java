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
        private String boardUrl;
        private String boardName;
    }

    public static MyCommentResponse from(Comment comment) {
        return MyCommentResponse.builder()
                .commentId(comment.getCommentId())
                .content(comment.getContent())
                .post(PostInfo.builder()
                        .postId(comment.getPost().getPostId())
                        .title(comment.getPost().getTitle())
                        .boardUrl(comment.getPost().getBoard().getBoardUrl())
                        .boardName(comment.getPost().getBoard().getBoardName())
                        .build())
                .likeCount(comment.getLikeCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
