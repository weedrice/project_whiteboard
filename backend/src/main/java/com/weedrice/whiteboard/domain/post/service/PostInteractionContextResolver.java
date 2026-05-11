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

        Set<Long> postIds = posts.stream()
                .map(Post::getPostId)
                .collect(Collectors.toSet());
        Set<Long> boardIds = posts.stream()
                .map(post -> post.getBoard().getBoardId())
                .collect(Collectors.toSet());
        Set<Long> likedPostIds = postLikeRepository.findPostIdsByUserIdAndPostIdIn(currentUserId, postIds).stream()
                .collect(Collectors.toSet());
        Set<Long> scrappedPostIds = scrapRepository.findPostIdsByUserIdAndPostIdIn(currentUserId, postIds).stream()
                .collect(Collectors.toSet());
        Set<String> subscribedBoardUrls = boardSubscriptionRepository
                .findBoardUrlsByUserIdAndBoardIdIn(currentUserId, boardIds).stream()
                .collect(Collectors.toSet());

        return new PostUserInteractionContext(likedPostIds, scrappedPostIds, subscribedBoardUrls);
    }
}
