package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.feed.dto.FeedPostSummary;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostListReadService;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.cache.annotation.Cacheable;
import com.weedrice.whiteboard.global.config.CacheNames;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeLandingService {

    private static final int LANDING_BOARD_LIMIT = 7;
    private static final int LANDING_CURATED_POST_LIMIT = 16;
    private static final int LANDING_LATEST_POST_LIMIT = 6;

    private final PostListReadService postListReadService;
    private final BoardService boardService;
    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Cacheable(cacheNames = CacheNames.HOME_LANDING_ANONYMOUS,
            key = "T(com.weedrice.whiteboard.global.config.AnonymousCacheKey).normalizePeriod(#period)",
            condition = "#userId == null && @cacheFeatureProperties.isReadOptimizationEnabled()", sync = true)
    public HomeLandingResponse getLanding(Long userId, String period) {
        List<FeedPostSummary> curatedPosts = getCuratedPosts(userId, period);
        List<FeedPostSummary> latestPosts = getLatestPosts(userId);
        List<BoardListResponse> boards = getBoards(userId);
        HomeLandingResponse.Stats stats = getStats();

        return HomeLandingResponse.builder()
                .curatedPosts(safeList(curatedPosts))
                .latestPosts(safeList(latestPosts))
                .boards(safeList(boards))
                .stats(stats)
                .build();
    }

    private HomeLandingResponse.Stats getStats() {
        LandingStatsWindow statsWindow = currentStatsWindow();

        PostRepository.PublicLandingPostStatsProjection postStats = postRepository.countPublicLandingPostStats(
                statsWindow.todayStart(),
                statsWindow.tomorrowStart(),
                statsWindow.yesterdayStart(),
                BoardPolicyConstants.INQUIRY_BOARD_URL);
        long activeBoardCount = boardRepository.countPublicLandingVisibleBoards(BoardPolicyConstants.INQUIRY_BOARD_URL);
        UserRepository.PublicLandingUserStatsProjection userStats =
                userRepository.countPublicLandingUserStats(statsWindow.twentyFourHoursAgo());
        long commentsToday = commentRepository.countPublicLandingVisibleCommentsCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                statsWindow.todayStart(),
                statsWindow.tomorrowStart(),
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

    private LandingStatsWindow currentStatsWindow() {
        LocalDate today = LocalDate.now(clock);
        return new LandingStatsWindow(
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay(),
                today.minusDays(1).atStartOfDay(),
                LocalDateTime.now(clock).minusHours(24));
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

    private List<FeedPostSummary> getCuratedPosts(Long userId, String period) {
        return postListReadService.getTrendingFeedPosts(PageRequest.of(0, LANDING_CURATED_POST_LIMIT), userId, period);
    }

    private List<FeedPostSummary> getLatestPosts(Long userId) {
        return postListReadService.getPublicLandingLatestFeedPosts(
                PageRequest.of(0, LANDING_LATEST_POST_LIMIT),
                userId);
    }

    private List<BoardListResponse> getBoards(Long userId) {
        return boardService.getTopBoardsByUserId(userId, LANDING_BOARD_LIMIT);
    }

    private <T> List<T> safeList(List<T> items) {
        return items == null ? List.of() : items;
    }

    private record LandingStatsWindow(
            LocalDateTime todayStart,
            LocalDateTime tomorrowStart,
            LocalDateTime yesterdayStart,
            LocalDateTime twentyFourHoursAgo) {
    }
}
