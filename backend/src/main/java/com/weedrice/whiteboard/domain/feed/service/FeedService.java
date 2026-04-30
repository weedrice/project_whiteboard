package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FeedService {

    private final UserFeedRepository userFeedRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final FeedGenerationService feedGenerationService;
    private final UserBlockService userBlockService;

    public FeedResponse getUserFeeds(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
        Page<UserFeed> feedPage = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(user, blockedUserIds, pageable);
        Map<Long, PostSummary> postSummariesById = resolvePostSummaries(feedPage, userId);
        List<UserFeed> resolvableFeeds = filterResolvableFeeds(feedPage, postSummariesById, userId);
        return FeedResponse.from(feedPage, resolvableFeeds, postSummariesById);
    }

    @Transactional
    public void generateFeeds() {
        feedGenerationService.generateFeeds();
    }

    private Map<Long, PostSummary> resolvePostSummaries(Page<UserFeed> feedPage, Long userId) {
        List<Long> postIds = feedPage.getContent().stream()
                .filter(feed -> FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType()))
                .map(UserFeed::getContentId)
                .toList();
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postService.getPostSummariesByIds(postIds, userId);
    }

    private List<UserFeed> filterResolvableFeeds(Page<UserFeed> feedPage, Map<Long, PostSummary> postSummariesById,
                                                 Long userId) {
        List<UserFeed> resolvableFeeds = feedPage.getContent().stream()
                .filter(feed -> !FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType())
                        || postSummariesById.containsKey(feed.getContentId()))
                .toList();
        int stalePostFeedCount = feedPage.getContent().size() - resolvableFeeds.size();
        if (stalePostFeedCount > 0) {
            log.warn("Excluded stale POST feeds from feed response. userId={}, count={}", userId, stalePostFeedCount);
        }
        return resolvableFeeds;
    }
}
