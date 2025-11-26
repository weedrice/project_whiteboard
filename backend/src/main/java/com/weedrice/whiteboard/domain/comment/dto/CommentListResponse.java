package com.weedrice.whiteboard.domain.comment.dto;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class CommentListResponse {
    private List<CommentResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class CommentResponse {
        private Long commentId;
        private String content;
        private AuthorInfo author;
        private int depth;
        private int likeCount;
        private boolean isDeleted;
        private LocalDateTime createdAt;
        private List<CommentResponse> children; // 대댓글

        @Getter
        @Builder
        public static class AuthorInfo {
            private Long userId;
            private String displayName;
            private String profileImageUrl;
        }
    }

    public static CommentListResponse from(Page<Comment> commentPage) {
        List<CommentResponse> content = commentPage.getContent().stream()
                .map(CommentListResponse::mapToCommentResponse)
                .collect(Collectors.toList());

        return CommentListResponse.builder()
                .content(content)
                .page(commentPage.getNumber())
                .size(commentPage.getSize())
                .totalElements(commentPage.getTotalElements())
                .totalPages(commentPage.getTotalPages())
                .hasNext(commentPage.hasNext())
                .hasPrevious(commentPage.hasPrevious())
                .build();
    }

    private static CommentResponse mapToCommentResponse(Comment comment) {
        CommentResponse.AuthorInfo authorInfo = null;
        if (comment.getUser() != null) { // 삭제된 댓글은 user가 null일 수 있음
            authorInfo = CommentResponse.AuthorInfo.builder()
                    .userId(comment.getUser().getUserId())
                    .displayName(comment.getUser().getDisplayName())
                    .profileImageUrl(comment.getUser().getProfileImageUrl())
                    .build();
        }

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .content(comment.getIsDeleted().equals("Y") ? "삭제된 댓글입니다." : comment.getContent())
                .author(authorInfo)
                .depth(comment.getDepth())
                .likeCount(comment.getLikeCount())
                .isDeleted(comment.getIsDeleted().equals("Y"))
                .createdAt(comment.getCreatedAt())
                .children(comment.getChildren().stream() // Assuming children are loaded or fetched
                        .map(CommentListResponse::mapToCommentResponse)
                        .collect(Collectors.toList()))
                .build();
    }
}
