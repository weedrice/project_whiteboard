package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.Post;

import java.time.LocalDateTime;

public record PostSummaryFields(
        Long postId,
        String title,
        Author author,
        Category category,
        Counts counts,
        Flags flags,
        LocalDateTime createdAt,
        Board board) {

    public static PostSummaryFields from(Post post, String boardIconUrl) {
        boolean agentPost = post.getAgent() != null;
        return new PostSummaryFields(
                post.getPostId(),
                post.getTitle(),
                new Author(
                        post.getUser().getUserId(),
                        agentPost ? post.getAgent().getAgentId() : null,
                        agentPost ? "AGENT" : "USER",
                        agentPost ? post.getAgent().getName() : post.getUser().getDisplayName(),
                        agentPost ? null : post.getUser().getProfileImageUrl(),
                        agentPost ? post.getAgent().getName() : post.getUser().getDisplayName()),
                post.getCategory() != null
                        ? new Category(post.getCategory().getCategoryId(), post.getCategory().getName())
                        : null,
                new Counts(post.getViewCount(), post.getLikeCount(), post.getCommentCount()),
                new Flags(post.getIsNotice(), post.getIsNsfw(), post.getIsSpoiler(), post.getIsSecret()),
                post.getCreatedAt(),
                new Board(
                        post.getBoard().getBoardId(),
                        post.getBoard().getBoardUrl(),
                        post.getBoard().getBoardName(),
                        boardIconUrl));
    }

    public Long boardId() {
        return board.boardId();
    }

    public Long categoryId() {
        return category != null ? category.categoryId() : null;
    }

    public String boardUrl() {
        return board.boardUrl();
    }

    public String boardName() {
        return board.boardName();
    }

    public String boardIconUrl() {
        return board.boardIconUrl();
    }

    public String authorName() {
        return author.authorName();
    }

    public record Author(Long userId, Long agentId, String authorType, String displayName, String profileImageUrl,
            String authorName) {
    }

    public record Category(Long categoryId, String name) {
    }

    public record Counts(int viewCount, int likeCount, int commentCount) {
    }

    public record Flags(boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret) {
    }

    public record Board(Long boardId, String boardUrl, String boardName, String boardIconUrl) {
    }
}
