package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.CommentStreamEvent;

public interface CommentStreamPublisher {

    void publishCommentEvent(CommentStreamEvent event);
}
