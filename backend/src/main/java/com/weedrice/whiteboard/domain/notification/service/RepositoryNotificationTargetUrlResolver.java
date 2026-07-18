package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostReadAccessService;
import com.weedrice.whiteboard.domain.user.entity.User;
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

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostReadAccessService postReadAccessService;

    @Override
    public Map<Long, String> resolveAll(Collection<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Map.of();
        }

        Set<Long> postIds = notifications.stream()
                .filter(notification -> isSourceType(notification, NotificationSourceType.POST))
                .map(Notification::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> commentIds = notifications.stream()
                .filter(notification -> isSourceType(notification, NotificationSourceType.COMMENT))
                .map(Notification::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Comment> commentsById = commentIds.isEmpty()
                ? Map.of()
                : commentRepository.findByCommentIdInAndIsDeletedFalse(commentIds).stream()
                        .filter(comment -> !Boolean.TRUE.equals(comment.getIsBlinded()))
                        .collect(Collectors.toMap(Comment::getCommentId, Function.identity()));
        Set<Long> targetPostIds = new java.util.HashSet<>(postIds);
        commentsById.values().stream()
                .map(Comment::getPost)
                .filter(Objects::nonNull)
                .map(Post::getPostId)
                .filter(Objects::nonNull)
                .forEach(targetPostIds::add);
        Map<Long, Post> postsById = targetPostIds.isEmpty()
                ? Map.of()
                : postRepository.findByPostIdInAndIsDeletedFalseAndIsBlindedFalse(targetPostIds).stream()
                        .collect(Collectors.toMap(Post::getPostId, Function.identity()));
        Map<Long, Set<Long>> readablePostIdsByUserId = resolveReadablePostIdsByUser(
                notifications,
                postsById.values());
        Map<Long, String> targetUrls = new HashMap<>();

        for (Notification notification : notifications) {
            if (notification.getNotificationId() == null) {
                continue;
            }

            Long receiverUserId = notification.getUser() != null ? notification.getUser().getUserId() : null;
            Set<Long> readablePostIds = receiverUserId == null
                    ? Set.of()
                    : readablePostIdsByUserId.getOrDefault(receiverUserId, Set.of());
            String targetUrl = resolveTargetUrl(notification, postsById, commentsById, readablePostIds);
            if (targetUrl != null) {
                targetUrls.put(notification.getNotificationId(), targetUrl);
            }
        }

        return targetUrls;
    }

    private Map<Long, Set<Long>> resolveReadablePostIdsByUser(
            Collection<Notification> notifications,
            Collection<Post> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        Map<Long, User> receiversById = notifications.stream()
                .map(Notification::getUser)
                .filter(Objects::nonNull)
                .filter(user -> user.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, Function.identity(), (left, right) -> left));
        Map<Long, Set<Long>> readablePostIdsByUserId = new HashMap<>();
        receiversById.forEach((userId, user) -> readablePostIdsByUserId.put(
                userId,
                postReadAccessService.findReadablePostIds(user, posts)));
        return readablePostIdsByUserId;
    }

    private String resolveTargetUrl(
            Notification notification,
            Map<Long, Post> postsById,
            Map<Long, Comment> commentsById,
            Set<Long> readablePostIds) {
        if (isSourceType(notification, NotificationSourceType.POST)) {
            Post post = postsById.get(notification.getSourceId());
            return post != null && readablePostIds.contains(post.getPostId())
                    ? buildPostTargetUrl(post)
                    : null;
        }

        if (isSourceType(notification, NotificationSourceType.COMMENT)) {
            Comment comment = commentsById.get(notification.getSourceId());
            Post post = comment != null && comment.getPost() != null
                    ? postsById.get(comment.getPost().getPostId())
                    : null;
            return post != null && readablePostIds.contains(post.getPostId())
                    ? buildCommentTargetUrl(comment, post)
                    : null;
        }

        if (isSourceType(notification, NotificationSourceType.MESSAGE)) {
            return "/mypage/messages";
        }

        return null;
    }

    private boolean isSourceType(Notification notification, NotificationSourceType sourceType) {
        return notification != null
                && notification.getSourceType() != null
                && NotificationSourceType.fromDatabaseValue(notification.getSourceType())
                        .filter(sourceType::equals)
                        .isPresent();
    }

    private String buildPostTargetUrl(Post post) {
        if (post == null || post.getBoard() == null || post.getBoard().getBoardUrl() == null) {
            return null;
        }

        return "/board/%s/post/%d".formatted(post.getBoard().getBoardUrl(), post.getPostId());
    }

    private String buildCommentTargetUrl(Comment comment, Post post) {
        if (comment == null || post == null || post.getBoard() == null) {
            return null;
        }

        if (post.getBoard().getBoardUrl() == null || post.getPostId() == null) {
            return null;
        }

        return "/board/%s/post/%d#comment-%d".formatted(
                post.getBoard().getBoardUrl(),
                post.getPostId(),
                comment.getCommentId());
    }
}
