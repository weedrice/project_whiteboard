package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.feed.dto.FeedResponse;
import com.weedrice.whiteboard.domain.feed.entity.UserFeed;
import com.weedrice.whiteboard.domain.feed.repository.UserFeedRepository;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedService {
    @PersistenceContext
    private EntityManager entityManager;

    private final UserFeedRepository userFeedRepository;
    private final UserRepository userRepository;
    private final PostService postService;
    private final FeedGenerationService feedGenerationService;

    @Transactional
    public FeedResponse getUserFeeds(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<UserFeed> feedPage = loadVisibleFeedPage(user, userId, pageable);
        Map<Long, PostSummary> postSummariesById = resolvePostSummaries(feedPage, userId);
        return FeedResponse.from(feedPage, postSummariesById);
    }

    @Transactional
    public void generateFeeds() {
        feedGenerationService.generateFeeds();
    }

    private Page<UserFeed> loadVisibleFeedPage(User user, Long userId, Pageable pageable) {
        Page<UserFeed> feedPage = userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable);
        Set<Long> cleanedInvalidFeedIds = new HashSet<>();

        while (true) {
            Map<Long, PostSummary> postSummariesById = resolvePostSummaries(feedPage, userId);
            List<UserFeed> invalidPostFeeds = findInvalidPostFeeds(feedPage, postSummariesById);
            if (invalidPostFeeds.isEmpty()) {
                return feedPage;
            }
            boolean madeProgress = cleanedInvalidFeedIds.addAll(invalidPostFeeds.stream()
                    .map(UserFeed::getFeedId)
                    .toList());
            if (!madeProgress) {
                throw new IllegalStateException("Failed to sanitize invalid feeds for user feed page");
            }
            userFeedRepository.deleteAllInBatch(invalidPostFeeds);
            userFeedRepository.flush();
            entityManager.clear();
            feedPage = userFeedRepository.findByTargetUserOrderByCreatedAtDesc(user, pageable);
        }
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

    private List<UserFeed> findInvalidPostFeeds(Page<UserFeed> feedPage, Map<Long, PostSummary> postSummariesById) {
        return feedPage.getContent().stream()
                .filter(feed -> FeedGenerationService.CONTENT_TYPE_POST.equals(feed.getContentType()))
                .filter(feed -> !postSummariesById.containsKey(feed.getContentId()))
                .toList();
    }
}
