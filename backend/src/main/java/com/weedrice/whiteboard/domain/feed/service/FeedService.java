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
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserReadableResolver userReadableResolver;
    private final PostService postService;
    private final FeedGenerationService feedGenerationService;
    private final UserBlockService userBlockService;
    private final AdminRepository adminRepository;

    public FeedResponse getUserFeeds(Long userId, Pageable pageable) {
        User user = userReadableResolver.resolve(userId);
        Pageable normalizedPageable = PageRequestUtils.of(pageable, DEFAULT_FEED_PAGE_SIZE, FEED_LIST_SORT);
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
            log.warn("Resolved POST feed summaries missing. userId={}, count={}", userId,
                    unresolvedPostFeedCount);
        }
    }

    private ResolvedFeedPage resolveFeedPage(
            Long userId,
            UserFeedVisibilityCondition visibilityCondition,
            Page<UserFeed> firstFeedPage) {
        PostSummaryReadContext readContext = PostSummaryReadContext.of(
                userId,
                visibilityCondition.targetUser(),
                visibilityCondition.blockedUserIds(),
                visibilityCondition.activeAdminBoardIds());
        Map<Long, PostSummary> postSummariesById = resolvePostSummaries(firstFeedPage, readContext);
        logUnresolvedPostFeeds(firstFeedPage, postSummariesById, userId);

        return new ResolvedFeedPage(firstFeedPage, firstFeedPage.getContent(), postSummariesById);
    }

    private record ResolvedFeedPage(Page<UserFeed> page, List<UserFeed> feeds,
                                    Map<Long, PostSummary> postSummariesById) {
    }
}
