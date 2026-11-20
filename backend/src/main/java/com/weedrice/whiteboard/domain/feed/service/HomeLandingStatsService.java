package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class HomeLandingStatsService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    @Cacheable(cacheNames = CacheNames.HOME_LANDING_PUBLIC_STATS,
            key = "T(com.weedrice.whiteboard.global.config.AnonymousCacheKey).landingStatsWindow(#observedAt)",
            condition = "@cacheFeatureProperties.isReadOptimizationEnabled()", sync = true)
    public HomeLandingResponse.Stats getStats(LocalDateTime observedAt) {
        LocalDateTime todayStart = observedAt.toLocalDate().atStartOfDay();

        PostRepository.PublicLandingPostStatsProjection postStats = postRepository.countPublicLandingPostStats(
                todayStart,
                todayStart.plusDays(1),
                todayStart.minusDays(1),
                BoardPolicyConstants.INQUIRY_BOARD_URL);
        long activeBoardCount = boardRepository.countPublicLandingVisibleBoards(BoardPolicyConstants.INQUIRY_BOARD_URL);
        UserRepository.PublicLandingUserStatsProjection userStats =
                userRepository.countPublicLandingUserStats(observedAt.minusHours(24));
        long commentsToday = commentRepository.countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                todayStart,
                todayStart.plusDays(1),
                BoardPolicyConstants.INQUIRY_BOARD_URL);
        long totalPosts = countOrZero(postStats.getTotalPosts());
        long postsToday = countOrZero(postStats.getPostsToday());
        long postsYesterday = countOrZero(postStats.getPostsYesterday());
        long newMembersLast24Hours = countOrZero(userStats.getNewMembersLast24Hours());
        long onlineCount = countOrZero(userStats.getOnlineCount());

        return HomeLandingResponse.Stats.builder()
                .boardCount(activeBoardCount)
                .postCount(totalPosts)
                .liveCount(commentsToday)
                .onlineCount(onlineCount)
                .postsToday(postsToday)
                .postsTodayDeltaPercent(calculateDeltaPercent(postsToday, postsYesterday))
                .activeBoardCount(activeBoardCount)
                .newMembersLast24Hours(newMembersLast24Hours)
                .commentsToday(commentsToday)
                .build();
    }

    private Integer calculateDeltaPercent(long current, long previous) {
        if (previous <= 0L) {
            return null;
        }
        return (int) Math.round(((double) (current - previous) / previous) * 100);
    }

    private long countOrZero(Long count) {
        return count == null ? 0L : count;
    }
}
