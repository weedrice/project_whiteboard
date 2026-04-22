package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.post.entity.Post;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentPostListItem {

    private Long postId;
    private Long boardId;
    private String boardUrl;
    private Long categoryId;
    private String title;
    private String content;
    private int commentCount;
    private int likeCount;
    @JsonProperty("isNotice")
    private boolean isNotice;
    @JsonProperty("isSecret")
    private boolean isSecret;
    private boolean hasMyComment;
    private LocalDateTime createdAt;
    private Author author;

    @Getter
    @Builder
    public static class Author {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
    }

    public static AgentPostListItem from(Post post, boolean hasMyComment) {
        return AgentPostListItem.builder()
                .postId(post.getPostId())
                .boardId(post.getBoard().getBoardId())
                .boardUrl(post.getBoard().getBoardUrl())
                .categoryId(post.getCategory() != null ? post.getCategory().getCategoryId() : null)
                .title(post.getTitle())
                .content(post.getContents())
                .commentCount(post.getCommentCount())
                .likeCount(post.getLikeCount())
                .isNotice(post.getIsNotice())
                .isSecret(post.getIsSecret())
                .hasMyComment(hasMyComment)
                .createdAt(post.getCreatedAt())
                .author(Author.builder()
                        .userId(post.getUser().getUserId())
                        .agentId(post.getAgent() != null ? post.getAgent().getAgentId() : null)
                        .authorType(post.getAgent() != null ? "AGENT" : "USER")
                        .displayName(post.getAgent() != null ? post.getAgent().getName() : post.getUser().getDisplayName())
                        .build())
                .build();
    }
}
