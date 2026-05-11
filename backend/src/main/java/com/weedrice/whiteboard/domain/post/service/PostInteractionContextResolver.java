package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostInteractionContextResolver {

    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;

    PostUserInteractionContext resolve(List<Post> posts, Long currentUserId) {
        if (currentUserId == null) {
            return PostUserInteractionContext.empty();
        }

        userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (posts.isEmpty()) {
            return PostUserInteractionContext.empty();
        }

        Set<Long> postIds = resolvePostIds(posts);

        return new PostUserInteractionContext(
                resolveLikedPostIds(currentUserId, postIds),
                resolveScrappedPostIds(currentUserId, postIds),
                resolveSubscribedBoardUrls(currentUserId, posts));
    }

    PostUserInteractionContext resolvePostReactionsForExistingUser(List<Post> posts, Long currentUserId) {
        if (currentUserId == null || posts.isEmpty()) {
            return PostUserInteractionContext.empty();
        }

        Set<Long> postIds = resolvePostIds(posts);

        return new PostUserInteractionContext(
                resolveLikedPostIds(currentUserId, postIds),
                resolveScrappedPostIds(currentUserId, postIds),
                Set.of());
    }

    private Set<Long> resolvePostIds(List<Post> posts) {
        return posts.stream()
                .map(Post::getPostId)
                .collect(Collectors.toSet());
    }

    private Set<String> resolveSubscribedBoardUrls(Long currentUserId, List<Post> posts) {
        Set<Long> boardIds = posts.stream()
                .map(post -> post.getBoard().getBoardId())
                .collect(Collectors.toSet());
        return boardSubscriptionRepository.findBoardUrlsByUserIdAndBoardIdIn(currentUserId, boardIds).stream()
                .collect(Collectors.toSet());
    }

    private Set<Long> resolveLikedPostIds(Long currentUserId, Set<Long> postIds) {
        return postLikeRepository.findPostIdsByUserIdAndPostIdIn(currentUserId, postIds).stream()
                .collect(Collectors.toSet());
    }

    private Set<Long> resolveScrappedPostIds(Long currentUserId, Set<Long> postIds) {
        return scrapRepository.findPostIdsByUserIdAndPostIdIn(currentUserId, postIds).stream()
                .collect(Collectors.toSet());
    }
}
