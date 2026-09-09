package com.weedrice.whiteboard.domain.comment.service;

import com.weedrice.whiteboard.domain.actor.ActorBatchReadPort;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommentSummaryAssembler {

    private final ActorBatchReadPort actorBatchReadPort;
    private final CommentPostPort commentPostPort;

    public Page<CommentResponse> assemble(Page<Comment> comments) {
        Map<ContentActorRef, AuthorSnapshot> authors = actorBatchReadPort.resolveAuthors(
                comments.getContent().stream().map(this::actorRef).toList());
        Map<Long, CommentPostSnapshot> posts = commentPostPort.getAll(
                comments.getContent().stream().map(Comment::getPostId).distinct().toList());
        return comments.map(comment -> CommentResponse.from(
                comment,
                authors.get(actorRef(comment)),
                posts.get(comment.getPostId())));
    }

    private ContentActorRef actorRef(Comment comment) {
        return new ContentActorRef(comment.getUserId(), comment.getAgentId());
    }
}
