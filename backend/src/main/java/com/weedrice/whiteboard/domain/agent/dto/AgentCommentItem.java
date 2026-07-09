package com.weedrice.whiteboard.domain.agent.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentCommentItem {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_BLINDED = "BLINDED";
    public static final String STATUS_BLOCKED_AUTHOR = "BLOCKED_AUTHOR";

    private Long commentId;
    private Long parentId;
    private Long postId;
    private String content;
    private int depth;
    private int likeCount;
    private long replyCount;
    private boolean hasReplies;
    private LocalDateTime createdAt;
    private String status;
    private Author author;

    @Getter
    @Builder
    public static class Author {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
    }
}
