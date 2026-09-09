package com.weedrice.whiteboard.domain.user.integration;

import com.weedrice.whiteboard.domain.actor.ActorBatchReadPort;
import com.weedrice.whiteboard.domain.actor.AuthorSnapshot;
import com.weedrice.whiteboard.domain.actor.ContentActorRef;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.port.CommentPostPort;
import com.weedrice.whiteboard.domain.comment.port.CommentPostSnapshot;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import com.weedrice.whiteboard.domain.user.port.UserActivityPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserActivityIntegrationAdapter implements UserActivityPort {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ActorBatchReadPort actorBatchReadPort;
    private final CommentPostPort commentPostPort;

    @Override
    public PublicActivityCounts getPublicActivityCounts(Long userId) {
        return new PublicActivityCounts(
                postRepository.countPublicProfilePostsByUserId(userId),
                commentRepository.countPublicProfileCommentsByUser(userId));
    }

    @Override
    public Page<AdminUserPostResponse> getPostsForAdmin(Long userId, Pageable pageable) {
        Page<Post> posts = postRepository.findByUserIdOrderByCreatedAtDescPostIdDesc(userId, pageable);
        Map<ContentActorRef, AuthorSnapshot> authors = actorBatchReadPort.resolveAuthors(
                posts.getContent().stream().map(this::actorRef).toList());
        return posts.map(post -> AdminUserPostResponse.from(post, authors.get(actorRef(post))));
    }

    @Override
    public Page<AdminUserCommentResponse> getCommentsForAdmin(Long userId, Pageable pageable) {
        Page<Comment> comments = commentRepository.findByUserIdOrderByCreatedAtDescCommentIdDesc(userId, pageable);
        Map<ContentActorRef, AuthorSnapshot> authors = actorBatchReadPort.resolveAuthors(
                comments.getContent().stream().map(this::actorRef).toList());
        Map<Long, CommentPostSnapshot> posts = commentPostPort.getAll(
                comments.getContent().stream().map(Comment::getPostId).distinct().toList());
        return comments.map(comment -> AdminUserCommentResponse.from(
                comment, authors.get(actorRef(comment)), posts.get(comment.getPostId())));
    }

    private ContentActorRef actorRef(Post post) {
        return new ContentActorRef(post.getUserId(), post.getAgentId());
    }

    private ContentActorRef actorRef(Comment comment) {
        return new ContentActorRef(comment.getUserId(), comment.getAgentId());
    }
}
