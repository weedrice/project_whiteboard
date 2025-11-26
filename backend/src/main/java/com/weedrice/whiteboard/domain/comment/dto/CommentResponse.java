package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Builder
public class CommentResponse {
    private Long commentId;
    private String content;
    private AuthorInfo author;
    private int depth;
    private int likeCount;
    private boolean isDeleted;
    private LocalDateTime createdAt;
    @Setter
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
        if (comment.getUser() != null && "N".equals(comment.getIsDeleted())) {
            authorInfo = AuthorInfo.builder()
                    .userId(comment.getUser().getUserId())
                    .displayName(comment.getUser().getDisplayName())
                    .profileImageUrl(comment.getUser().getProfileImageUrl())
                    .build();
        }

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .content("Y".equals(comment.getIsDeleted()) ? "삭제된 댓글입니다." : comment.getContent())
                .author(authorInfo)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .isDeleted("Y".equals(comment.getIsDeleted()))
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
