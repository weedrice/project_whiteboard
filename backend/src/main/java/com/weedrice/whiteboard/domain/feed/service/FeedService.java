package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FeedService {

    private static final int MAX_REFILL_PAGE_LOOKAHEAD = 5;
    private static final int DEFAULT_FEED_PAGE_SIZE = 20;
    private static final Sort FEED_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("feedId"));

    private final UserFeedRepository userFeedRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final FeedGenerationService feedGenerationService;
    private final UserBlockService userBlockService;

    public FeedResponse getUserFeeds(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable normalizedPageable = normalizeFeedPageable(pageable);
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
        Page<UserFeed> feedPage = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                user,
                blockedUserIds,
                normalizedPageable);
        ResolvedFeedPage resolvedFeedPage = resolveFeedPage(user, blockedUserIds, userId, feedPage);
        return FeedResponse.from(
                resolvedFeedPage.page(),
                resolvedFeedPage.feeds(),
                resolvedFeedPage.postSummariesById());
    }

    @Transactional
    public void generateFeeds() {
        feedGenerationService.generateFeeds();
    }

    private Pageable normalizeFeedPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequestUtils.of(0, DEFAULT_FEED_PAGE_SIZE, FEED_LIST_SORT);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize(), FEED_LIST_SORT);
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

    private ResolvedFeedPage resolveFeedPage(User user, List<Long> blockedUserIds, Long userId,
                                             Page<UserFeed> firstFeedPage) {
        Pageable pageable = firstFeedPage.getPageable();
        int requestedSize = pageable.isPaged() ? pageable.getPageSize() : firstFeedPage.getNumberOfElements();
        List<UserFeed> responseFeeds = new ArrayList<>();
        Map<Long, PostSummary> postSummariesById = new LinkedHashMap<>();
        Set<Long> seenFeedIds = new HashSet<>();
        int droppedCount = 0;

        Page<UserFeed> currentPage = firstFeedPage;
        int additionalPageCount = 0;
        while (true) {
            Map<Long, PostSummary> currentSummariesById = resolvePostSummaries(currentPage, userId);
            postSummariesById.putAll(currentSummariesById);
            logUnresolvedPostFeeds(currentPage, currentSummariesById, userId);

            FeedFilterResult filterResult = excludeUnresolvedPostFeeds(
                    currentPage,
                    currentSummariesById,
                    seenFeedIds,
                    requestedSize - responseFeeds.size());
            responseFeeds.addAll(filterResult.feeds());
            droppedCount += filterResult.droppedCount();

            if (!shouldRefill(pageable, requestedSize, responseFeeds.size(), currentPage, additionalPageCount)) {
                break;
            }

            additionalPageCount++;
            currentPage = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                    user,
                    blockedUserIds,
                    PageRequest.of(
                            pageable.getPageNumber() + additionalPageCount,
                            pageable.getPageSize(),
                            pageable.getSort()));
        }

        Page<UserFeed> responsePage = adjustPageMetadata(
                firstFeedPage,
                responseFeeds,
                droppedCount,
                currentPage.hasNext());
        return new ResolvedFeedPage(responsePage, responseFeeds, postSummariesById);
    }

    private boolean shouldRefill(Pageable pageable, int requestedSize, int responseFeedCount, Page<UserFeed> currentPage,
                                 int additionalPageCount) {
        return pageable.isPaged()
                && responseFeedCount < requestedSize
                && currentPage.hasNext()
                && additionalPageCount < MAX_REFILL_PAGE_LOOKAHEAD;
    }

    private FeedFilterResult excludeUnresolvedPostFeeds(Page<UserFeed> feedPage,
                                                        Map<Long, PostSummary> postSummariesById,
                                                        Set<Long> seenFeedIds,
                                                        int remainingCount) {
        if (remainingCount <= 0) {
            return new FeedFilterResult(List.of(), 0);
        }
        List<UserFeed> feeds = new ArrayList<>();
        int droppedCount = 0;
        for (UserFeed feed : feedPage.getContent()) {
            Long feedId = feed.getFeedId();
            if (feedId != null && !seenFeedIds.add(feedId)) {
                continue;
            }
            if (FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType())
                    && !postSummariesById.containsKey(feed.getContentId())) {
                droppedCount++;
                continue;
            }
            if (feeds.size() < remainingCount) {
                feeds.add(feed);
            }
        }
        return new FeedFilterResult(feeds, droppedCount);
    }

    private Page<UserFeed> adjustPageMetadata(Page<UserFeed> feedPage, List<UserFeed> responseFeeds,
                                              int droppedCount, boolean hasMoreAfterFetchedRange) {
        long adjustedTotal = Math.max(0L, feedPage.getTotalElements() - droppedCount);
        if (hasMoreAfterFetchedRange && feedPage.getPageable().isPaged()) {
            long minimumTotalToKeepNextPage = feedPage.getPageable().getOffset() + feedPage.getSize() + 1L;
            adjustedTotal = Math.max(adjustedTotal, minimumTotalToKeepNextPage);
        }
        return new PageImpl<>(responseFeeds, feedPage.getPageable(), adjustedTotal);
    }

    private record ResolvedFeedPage(Page<UserFeed> page, List<UserFeed> feeds,
                                    Map<Long, PostSummary> postSummariesById) {
    }

    private record FeedFilterResult(List<UserFeed> feeds, int droppedCount) {
    }
}
