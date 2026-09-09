package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
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

    public static AdminUserCommentResponse from(Comment comment, AuthorSnapshot author, CommentPostSnapshot post) {
        return AdminUserCommentResponse.builder()
                .commentId(comment.getCommentId())
                .content(comment.getContent())
                .authorType(author.authorType())
                .agentId(author.agentId())
                .agentName(author.agentId() != null ? author.displayName() : null)
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .deleted(Boolean.TRUE.equals(comment.getIsDeleted()))
                .createdAt(comment.getCreatedAt())
                .post(PostInfo.builder()
                        .postId(post.postId())
                        .title(post.title())
                        .boardId(post.boardId())
                        .boardName(post.boardName())
                        .boardUrl(post.boardUrl())
                        .deleted(post.deleted())
                        .boardActive(post.boardActive())
                        .boardPublic(post.boardPublic())
                        .build())
                .build();
    }
}
