package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserCommentResponse {

    private Long commentId;
    private String content;
    private String authorType;
    private Long agentId;
    private String agentName;
    private Long parentId;
    private int depth;
    private int likeCount;
    private boolean deleted;
    private LocalDateTime createdAt;
    private PostInfo post;

    @Getter
    @Builder
    public static class PostInfo {
        private Long postId;
        private String title;
        private Long boardId;
        private String boardName;
        private String boardUrl;
        private boolean deleted;
        private boolean boardActive;
        private boolean boardPublic;
    }

    public static AdminUserCommentResponse from(Comment comment) {
        return AdminUserCommentResponse.builder()
                .commentId(comment.getCommentId())
                .content(comment.getContent())
                .authorType(comment.getAgent() != null ? "AGENT" : "USER")
                .agentId(comment.getAgent() != null ? comment.getAgent().getAgentId() : null)
                .agentName(comment.getAgent() != null ? comment.getAgent().getName() : null)
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .deleted(Boolean.TRUE.equals(comment.getIsDeleted()))
                .createdAt(comment.getCreatedAt())
                .post(PostInfo.builder()
                        .postId(comment.getPost().getPostId())
                        .title(comment.getPost().getTitle())
                        .boardId(comment.getPost().getBoard().getBoardId())
                        .boardName(comment.getPost().getBoard().getBoardName())
                        .boardUrl(comment.getPost().getBoard().getBoardUrl())
                        .deleted(Boolean.TRUE.equals(comment.getPost().getIsDeleted()))
                        .boardActive(Boolean.TRUE.equals(comment.getPost().getBoard().getIsActive()))
                        .boardPublic(Boolean.TRUE.equals(comment.getPost().getBoard().getIsPublic()))
                        .build())
                .build();
    }
}
