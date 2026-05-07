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
import org.springframework.data.domain.PageImpl;
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
        logUnresolvedPostFeeds(feedPage, postSummariesById, userId);
        List<UserFeed> responseFeeds = excludeUnresolvedPostFeeds(feedPage, postSummariesById);
        Page<UserFeed> responsePage = adjustPageMetadata(feedPage, responseFeeds);
        return FeedResponse.from(responsePage, responseFeeds, postSummariesById);
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

    private void logUnresolvedPostFeeds(Page<UserFeed> feedPage, Map<Long, PostSummary> postSummariesById,
                                        Long userId) {
        long unresolvedPostFeedCount = feedPage.getContent().stream()
                .filter(feed -> FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType()))
                .filter(feed -> !postSummariesById.containsKey(feed.getContentId()))
                .count();
        if (unresolvedPostFeedCount > 0) {
            log.warn("Excluded POST feeds without resolved summaries. userId={}, count={}", userId,
                    unresolvedPostFeedCount);
        }
    }

    private List<UserFeed> excludeUnresolvedPostFeeds(Page<UserFeed> feedPage, Map<Long, PostSummary> postSummariesById) {
        return feedPage.getContent().stream()
                .filter(feed -> !FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType())
                        || postSummariesById.containsKey(feed.getContentId()))
                .toList();
    }

    private Page<UserFeed> adjustPageMetadata(Page<UserFeed> feedPage, List<UserFeed> responseFeeds) {
        int droppedCount = feedPage.getNumberOfElements() - responseFeeds.size();
        if (droppedCount <= 0) {
            return feedPage;
        }
        long adjustedTotal = Math.max(0L, feedPage.getTotalElements() - droppedCount);
        if (feedPage.hasNext() && feedPage.getPageable().isPaged()) {
            long minimumTotalToKeepNextPage = feedPage.getPageable().getOffset() + feedPage.getSize() + 1L;
            adjustedTotal = Math.max(adjustedTotal, minimumTotalToKeepNextPage);
        }
        return new PageImpl<>(responseFeeds, feedPage.getPageable(), adjustedTotal);
    }
}
