package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedGenerationService {

    static final String FEED_TYPE_SUBSCRIPTION_POST = "SUBSCRIPTION_POST";
    static final String CONTENT_TYPE_POST = "POST";
    static final String SOURCE_CRITERIA_BOARD_SUBSCRIPTION = "BOARD_SUBSCRIPTION";

    private final UserFeedRepository userFeedRepository;
    private final PostRepository postRepository;

    public void generateFeeds() {
        // Batch feed generation remains out of scope for this change set.
    }

    @Transactional
    public void generatePostFeeds(Board board, Long postId) {
        postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        userFeedRepository.insertSubscriptionPostFeeds(
                board.getBoardId(),
                FEED_TYPE_SUBSCRIPTION_POST,
                CONTENT_TYPE_POST,
                postId,
                SOURCE_CRITERIA_BOARD_SUBSCRIPTION,
                board.getBoardId());
    }
}
