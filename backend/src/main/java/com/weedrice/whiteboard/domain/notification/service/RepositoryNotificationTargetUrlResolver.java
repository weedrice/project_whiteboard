package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class RepositoryNotificationTargetUrlResolver implements NotificationTargetUrlResolver {

    private static final String SOURCE_TYPE_POST = "POST";
    private static final String SOURCE_TYPE_COMMENT = "COMMENT";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Override
    public Map<Long, String> resolveAll(Collection<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Map.of();
        }

        Set<Long> postIds = notifications.stream()
                .filter(notification -> isSourceType(notification, SOURCE_TYPE_POST))
                .map(Notification::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> commentIds = notifications.stream()
                .filter(notification -> isSourceType(notification, SOURCE_TYPE_COMMENT))
                .map(Notification::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Post> postsById = postIds.isEmpty()
                ? Map.of()
                : postRepository.findByPostIdInAndIsDeletedFalse(postIds).stream()
                        .collect(Collectors.toMap(Post::getPostId, Function.identity()));
        Map<Long, Comment> commentsById = commentIds.isEmpty()
                ? Map.of()
                : commentRepository.findByCommentIdInAndIsDeletedFalse(commentIds).stream()
                        .collect(Collectors.toMap(Comment::getCommentId, Function.identity()));
        Map<Long, String> targetUrls = new HashMap<>();

        for (Notification notification : notifications) {
            if (notification.getNotificationId() == null) {
                continue;
            }

            String targetUrl = resolveTargetUrl(notification, postsById, commentsById);
            if (targetUrl != null) {
                targetUrls.put(notification.getNotificationId(), targetUrl);
            }
        }

        return targetUrls;
    }

    private String resolveTargetUrl(
            Notification notification,
            Map<Long, Post> postsById,
            Map<Long, Comment> commentsById) {
        if (isSourceType(notification, SOURCE_TYPE_POST)) {
            return buildPostTargetUrl(postsById.get(notification.getSourceId()));
        }

        if (isSourceType(notification, SOURCE_TYPE_COMMENT)) {
            return buildCommentTargetUrl(commentsById.get(notification.getSourceId()));
        }

        return null;
    }

    private boolean isSourceType(Notification notification, String sourceType) {
        return notification != null
                && notification.getSourceType() != null
                && sourceType.equalsIgnoreCase(notification.getSourceType());
    }

    private String buildPostTargetUrl(Post post) {
        if (post == null || post.getBoard() == null || post.getBoard().getBoardUrl() == null) {
            return null;
        }

        return "/board/%s/post/%d".formatted(post.getBoard().getBoardUrl(), post.getPostId());
    }

    private String buildCommentTargetUrl(Comment comment) {
        if (comment == null || comment.getPost() == null || comment.getPost().getBoard() == null) {
            return null;
        }

        Post post = comment.getPost();
        if (post.getBoard().getBoardUrl() == null || post.getPostId() == null) {
            return null;
        }

        return "/board/%s/post/%d#comment-%d".formatted(
                post.getBoard().getBoardUrl(),
                post.getPostId(),
                comment.getCommentId());
    }
}
