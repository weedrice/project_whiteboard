package com.weedrice.whiteboard.domain.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder(toBuilder = true)
public class CommentResponse {
    public static final String DELETED_CONTENT = "삭제된 댓글입니다.";

    private Long commentId;
    private Long parentId;
    private String content;
    private AuthorInfo author;
    private int depth;
    private int likeCount;
    @JsonProperty("isDeleted")
    private boolean isDeleted;
    @JsonProperty("isBlockedAuthor")
    private boolean isBlockedAuthor;
    private Long maskedAuthorId;
    private LocalDateTime createdAt;
    private Long postId;
    private String boardUrl;
    private String postTitle;
    @Builder.Default
    private long replyCount = 0L;
    @Builder.Default
    private boolean hasReplies = false;
    @Setter
    @Builder.Default
    private List<CommentResponse> children = new ArrayList<>();

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;
    }

    public static CommentResponse from(Comment comment) {
        AuthorInfo authorInfo = null;
        if (comment.getUser() != null && !comment.getIsDeleted()) {
            authorInfo = AuthorInfo.builder()
                    .userId(comment.getUser().getUserId())
                    .agentId(comment.getAgent() != null ? comment.getAgent().getAgentId() : null)
                    .authorType(comment.getAgent() != null ? "AGENT" : "USER")
                    .displayName(comment.getAgent() != null ? comment.getAgent().getName() : comment.getUser().getDisplayName())
                    .profileImageUrl(comment.getAgent() != null ? null : comment.getUser().getProfileImageUrl())
                    .build();
        }

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null)
                .content(comment.getIsDeleted() ? DELETED_CONTENT : comment.getContent())
                .author(authorInfo)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .isDeleted(comment.getIsDeleted())
                .isBlockedAuthor(false)
                .maskedAuthorId(null)
                .createdAt(comment.getCreatedAt())
                .postId(comment.getPost().getPostId())
                .boardUrl(comment.getPost().getBoard().getBoardUrl())
                .postTitle(comment.getPost().getTitle())
                .build();
    }
}
