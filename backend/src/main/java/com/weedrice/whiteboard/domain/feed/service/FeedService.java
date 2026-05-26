package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedVisibilityCondition;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.post.service.PostSummaryReadContext;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class FeedService {

    private static final int DEFAULT_FEED_PAGE_SIZE = 20;
    private static final Sort FEED_LIST_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("feedId"));

    private final UserFeedRepository userFeedRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final FeedGenerationService feedGenerationService;
    private final UserBlockService userBlockService;
    private final AdminRepository adminRepository;

    public FeedResponse getUserFeeds(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable normalizedPageable = normalizeFeedPageable(pageable);
        List<Long> blockedUserIds = userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
        UserFeedVisibilityCondition visibilityCondition = resolveVisibilityCondition(user, blockedUserIds);
        Page<UserFeed> feedPage = userFeedRepository.findVisibleByTargetUserOrderByCreatedAtDesc(
                visibilityCondition,
                normalizedPageable);
        ResolvedFeedPage resolvedFeedPage = resolveFeedPage(userId, visibilityCondition, feedPage);
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

    private Map<Long, PostSummary> resolvePostSummaries(
            Page<UserFeed> feedPage,
            PostSummaryReadContext readContext) {
        List<Long> postIds = feedPage.getContent().stream()
                .filter(feed -> FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType()))
                .map(UserFeed::getContentId)
                .toList();
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postService.getPostSummariesByIds(postIds, readContext);
    }

    private UserFeedVisibilityCondition resolveVisibilityCondition(User user, List<Long> blockedUserIds) {
        List<Long> activeAdminBoardIds = user.isUsableSuperAdmin()
                ? List.of()
                : adminRepository.findActiveBoardIdsByUser(user);
        return UserFeedVisibilityCondition.of(user, blockedUserIds, activeAdminBoardIds);
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

    private ResolvedFeedPage resolveFeedPage(
            Long userId,
            UserFeedVisibilityCondition visibilityCondition,
            Page<UserFeed> firstFeedPage) {
        Map<Long, PostSummary> postSummariesById = new LinkedHashMap<>();
        PostSummaryReadContext readContext = PostSummaryReadContext.of(
                userId,
                visibilityCondition.targetUser(),
                visibilityCondition.blockedUserIds(),
                visibilityCondition.activeAdminBoardIds());
        Map<Long, PostSummary> currentSummariesById = resolvePostSummaries(firstFeedPage, readContext);
        postSummariesById.putAll(currentSummariesById);
        logUnresolvedPostFeeds(firstFeedPage, currentSummariesById, userId);

        FeedFilterResult filterResult = excludeUnresolvedPostFeeds(firstFeedPage, currentSummariesById);
        Page<UserFeed> resolvedPage = toResolvedPage(firstFeedPage, filterResult);

        return new ResolvedFeedPage(resolvedPage, filterResult.feeds(), postSummariesById);
    }

    private FeedFilterResult excludeUnresolvedPostFeeds(Page<UserFeed> feedPage,
                                                        Map<Long, PostSummary> postSummariesById) {
        List<UserFeed> feeds = new ArrayList<>();
        int excludedCount = 0;
        for (UserFeed feed : feedPage.getContent()) {
            if (FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType())
                    && !postSummariesById.containsKey(feed.getContentId())) {
                excludedCount++;
                continue;
            }
            feeds.add(feed);
        }
        return new FeedFilterResult(feeds, excludedCount);
    }

    private Page<UserFeed> toResolvedPage(Page<UserFeed> feedPage, FeedFilterResult filterResult) {
        if (filterResult.excludedCount() == 0) {
            return feedPage;
        }
        long totalElements = resolveFilteredTotalElements(feedPage, filterResult);
        return new PageImpl<>(filterResult.feeds(), feedPage.getPageable(), totalElements);
    }

    private long resolveFilteredTotalElements(Page<UserFeed> feedPage, FeedFilterResult filterResult) {
        if (feedPage.hasNext()) {
            return feedPage.getTotalElements();
        }
        long offset = feedPage.getPageable().isPaged() ? feedPage.getPageable().getOffset() : 0L;
        return offset + filterResult.feeds().size();
    }

    private record ResolvedFeedPage(Page<UserFeed> page, List<UserFeed> feeds,
                                    Map<Long, PostSummary> postSummariesById) {
    }

    private record FeedFilterResult(List<UserFeed> feeds, int excludedCount) {
    }
}
