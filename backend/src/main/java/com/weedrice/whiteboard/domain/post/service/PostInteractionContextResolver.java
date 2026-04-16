package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostLikeRepository;
import com.weedrice.whiteboard.domain.post.repository.ScrapRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class PostInteractionContextResolver {

    private final UserRepository userRepository;
    private final PostLikeRepository postLikeRepository;
    private final ScrapRepository scrapRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;

    PostInteractionContextResolver(UserRepository userRepository,
                                   PostLikeRepository postLikeRepository,
                                   ScrapRepository scrapRepository,
                                   BoardSubscriptionRepository boardSubscriptionRepository) {
        this.userRepository = userRepository;
        this.postLikeRepository = postLikeRepository;
        this.scrapRepository = scrapRepository;
        this.boardSubscriptionRepository = boardSubscriptionRepository;
    }

    PostUserInteractionContext resolve(List<Post> posts, Long currentUserId) {
        if (currentUserId == null || posts.isEmpty()) {
            return PostUserInteractionContext.empty();
        }

        User user = userRepository.findById(currentUserId).orElse(null);
        if (user == null) {
            return PostUserInteractionContext.empty();
        }

        List<Board> boards = posts.stream().map(Post::getBoard).distinct().collect(Collectors.toList());
        Set<Long> likedPostIds = postLikeRepository.findByUserAndPostIn(user, posts).stream()
                .map(like -> like.getPost().getPostId())
                .collect(Collectors.toSet());
        Set<Long> scrappedPostIds = scrapRepository.findByUserAndPostIn(user, posts).stream()
                .map(scrap -> scrap.getPost().getPostId())
                .collect(Collectors.toSet());
        Set<String> subscribedBoardUrls = boardSubscriptionRepository.findByUserAndBoardIn(user, boards).stream()
                .map(subscription -> subscription.getBoard().getBoardUrl())
                .collect(Collectors.toSet());

        return new PostUserInteractionContext(likedPostIds, scrappedPostIds, subscribedBoardUrls);
    }
}
