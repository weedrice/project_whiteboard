package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class CommentPostAccessService {

    private final CommentPostPort commentPostPort;

    void validateReadable(Long postId, CommentReadContext context) {
        commentPostPort.validateReadable(postId, context.viewerUserId(), context.blockedUserIds());
    }
}
