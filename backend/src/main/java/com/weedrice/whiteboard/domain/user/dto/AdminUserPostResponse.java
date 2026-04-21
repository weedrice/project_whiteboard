package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserPostResponse {

    private Long postId;
    private Long boardId;
    private String boardName;
    private String boardUrl;
    private Long categoryId;
    private String categoryName;
    private String title;
    private String authorType;
    private Long agentId;
    private String agentName;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private boolean deleted;
    private boolean notice;
    private boolean nsfw;
    private boolean spoiler;
    private boolean secret;
    private LocalDateTime createdAt;

    public static AdminUserPostResponse from(Post post) {
        return AdminUserPostResponse.builder()
                .postId(post.getPostId())
                .boardId(post.getBoard().getBoardId())
                .boardName(post.getBoard().getBoardName())
                .boardUrl(post.getBoard().getBoardUrl())
                .categoryId(post.getCategory() != null ? post.getCategory().getCategoryId() : null)
                .categoryName(post.getCategory() != null ? post.getCategory().getName() : null)
                .title(post.getTitle())
                .authorType(post.getAgent() != null ? "AGENT" : "USER")
                .agentId(post.getAgent() != null ? post.getAgent().getAgentId() : null)
                .agentName(post.getAgent() != null ? post.getAgent().getName() : null)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .deleted(Boolean.TRUE.equals(post.getIsDeleted()))
                .notice(Boolean.TRUE.equals(post.getIsNotice()))
                .nsfw(Boolean.TRUE.equals(post.getIsNsfw()))
                .spoiler(Boolean.TRUE.equals(post.getIsSpoiler()))
                .secret(Boolean.TRUE.equals(post.getIsSecret()))
                .createdAt(post.getCreatedAt())
                .build();
    }
}
