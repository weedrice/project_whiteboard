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
    private Long commentId;
    private Long parentId; // Added parentId
    private String content;
    private AuthorInfo author;
    private int depth;
    private int likeCount;
    @JsonProperty("isDeleted")
    private boolean isDeleted;
    private LocalDateTime createdAt;
    private Long postId;
    private String boardUrl;
    private String postTitle;
    @Setter
    @Builder.Default
    private List<CommentResponse> children = new ArrayList<>();

    @Getter
    @Builder
    public static class AuthorInfo {
        private Long userId;
        private String displayName;
        private String profileImageUrl;
    }

    public static CommentResponse from(Comment comment) {
        AuthorInfo authorInfo = null;
        if (comment.getUser() != null && !comment.getIsDeleted()) {
            authorInfo = AuthorInfo.builder()
                    .userId(comment.getUser().getUserId())
                    .displayName(comment.getUser().getDisplayName())
                    .profileImageUrl(comment.getUser().getProfileImageUrl())
                    .build();
        }

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .parentId(comment.getParent() != null ? comment.getParent().getCommentId() : null) // Initialize
                                                                                                   // parentId
                .content(comment.getIsDeleted() ? "삭제된 댓글입니다." : comment.getContent())
                .author(authorInfo)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .isDeleted(comment.getIsDeleted())
                .createdAt(comment.getCreatedAt())
                .postId(comment.getPost().getPostId())
                .boardUrl(comment.getPost().getBoard().getBoardUrl())
                .postTitle(comment.getPost().getTitle())
                .build();
    }
}
