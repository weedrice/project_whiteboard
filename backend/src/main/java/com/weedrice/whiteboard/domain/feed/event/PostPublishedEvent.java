package com.weedrice.whiteboard.domain.feed.event;

public record PostPublishedEvent(Long postId, Long boardId) {
}
