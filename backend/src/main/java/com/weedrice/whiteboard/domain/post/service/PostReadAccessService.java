package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostReadAccessService {

    private final PostReadContextResolver postReadContextResolver;
    private final PostAccessPolicy postAccessPolicy;

    public boolean isReadable(Post post, User viewer) {
        if (post == null) {
            return false;
        }
        return findReadablePostIds(viewer, List.of(post)).contains(post.getPostId());
    }

    public Set<Long> findReadablePostIds(User viewer, Collection<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Set.of();
        }

        List<Post> candidates = posts.stream()
                .filter(Objects::nonNull)
                .filter(post -> post.getPostId() != null)
                .toList();
        if (candidates.isEmpty()) {
            return Set.of();
        }

        PostReadContext context = postReadContextResolver.withAdminBoardIdsForPosts(
                postReadContextResolver.resolveForResolvedUser(viewer),
                candidates);
        return candidates.stream()
                .filter(post -> postAccessPolicy.isReadable(
                        post,
                        context.viewer(),
                        context.isAuthorBlocked(post),
                        context.activeAdminBoardIds()))
                .map(Post::getPostId)
                .collect(Collectors.toUnmodifiableSet());
    }
}
