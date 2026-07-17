package com.weedrice.whiteboard.domain.feed.repository;

public interface FeedGenerationJobRepositoryCustom {

    int insertIgnore(Long postId, Long boardId);
}
