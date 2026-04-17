package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedGenerationService {

    static final String FEED_TYPE_SUBSCRIPTION_POST = "SUBSCRIPTION_POST";
    static final String CONTENT_TYPE_POST = "POST";
    static final String SOURCE_CRITERIA_BOARD_SUBSCRIPTION = "BOARD_SUBSCRIPTION";

    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final UserFeedRepository userFeedRepository;
    private final PostRepository postRepository;
    private final UserFeedCommandService userFeedCommandService;

    public void generateFeeds() {
        // Batch feed generation remains out of scope for this change set.
    }

    @Transactional
    public void generatePostFeeds(Board board, Long postId) {
        postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        List<BoardSubscription> subscriptions = boardSubscriptionRepository.findAllByBoard(board);
        if (subscriptions.isEmpty()) {
            return;
        }

        Map<Long, BoardSubscription> subscriptionsByUserId = subscriptions.stream()
                .collect(Collectors.toMap(
                        subscription -> subscription.getUser().getUserId(),
                        subscription -> subscription,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<Long> subscriberUserIds = subscriptionsByUserId.keySet().stream().toList();
        Set<Long> existingTargetUserIds = userFeedRepository.findExistingTargetUserIds(
                        subscriberUserIds,
                        FEED_TYPE_SUBSCRIPTION_POST,
                        CONTENT_TYPE_POST,
                        postId,
                        SOURCE_CRITERIA_BOARD_SUBSCRIPTION,
                        board.getBoardId())
                .stream()
                .collect(Collectors.toSet());

        List<UserFeed> newFeeds = subscriptionsByUserId.entrySet().stream()
                .filter(entry -> !existingTargetUserIds.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .map(subscription -> UserFeed.builder()
                        .targetUser(subscription.getUser())
                        .feedType(FEED_TYPE_SUBSCRIPTION_POST)
                        .contentType(CONTENT_TYPE_POST)
                        .contentId(postId)
                        .sourceCriteria(SOURCE_CRITERIA_BOARD_SUBSCRIPTION)
                        .criteriaId(board.getBoardId())
                        .build())
                .toList();

        for (UserFeed newFeed : newFeeds) {
            userFeedCommandService.saveFeedIfAbsent(newFeed);
        }
    }
}
