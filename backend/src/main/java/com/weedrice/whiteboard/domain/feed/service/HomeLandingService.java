package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeLandingService {

    private final PostService postService;
    private final BoardService boardService;
    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public HomeLandingResponse getLanding(CustomUserDetails userDetails, String period) {
        Long userId = userDetails != null ? userDetails.getUserId() : null;
        List<PostSummary> curatedPosts = getCuratedPosts(userId, period);
        List<BoardListResponse> boards = getBoards(userDetails);
        HomeLandingResponse.Stats stats = getStats();

        PostSummary featuredPost = curatedPosts.isEmpty() ? null : curatedPosts.getFirst();
        List<PostSummary> editorPicks = slice(curatedPosts, 1, 4);
        List<PostSummary> trendingPosts = slice(curatedPosts, 4, 10);
        List<PostSummary> liveActivity = slice(curatedPosts, 0, 6);

        return HomeLandingResponse.builder()
                .featuredPost(featuredPost)
                .editorPicks(editorPicks)
                .trendingPosts(trendingPosts)
                .liveActivity(liveActivity)
                .boards(boards)
                .stats(stats)
                .build();
    }

    private HomeLandingResponse.Stats getStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minusHours(24);

        long postsToday = safeCount(
                () -> postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                        todayStart,
                        tomorrowStart),
                "home landing postsToday");
        long totalPosts = safeCount(
                postRepository::countVisiblePostsForAdminDashboard,
                "home landing totalPosts");
        long postsYesterday = safeCount(
                () -> postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                        yesterdayStart,
                        todayStart),
                "home landing postsYesterday");
        long activeBoardCount = safeCount(
                boardRepository::countByIsActiveTrueAndIsPublicTrue,
                "home landing activeBoardCount");
        long newMembersLast24Hours = safeCount(
                () -> userRepository.countByStatusAndDeletedAtIsNullAndCreatedAtAfter(
                        User.STATUS_ACTIVE,
                        twentyFourHoursAgo),
                "home landing newMembersLast24Hours");
        long onlineCount = safeCount(
                () -> userRepository.countRecentlyLoggedInActiveUsersForAdminDashboard(twentyFourHoursAgo),
                "home landing onlineCount");
        long commentsToday = safeCount(
                () -> commentRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                        todayStart,
                        tomorrowStart),
                "home landing commentsToday");

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

    private long safeCount(Supplier<Long> supplier, String metricName) {
        try {
            return supplier.get();
        } catch (RuntimeException exception) {
            log.warn("Failed to load {}", metricName, exception);
            return 0L;
        }
    }

    private Integer calculateDeltaPercent(long current, long previous) {
        if (previous <= 0L) {
            return null;
        }
        return (int) Math.round(((double) (current - previous) / previous) * 100);
    }

    private List<PostSummary> getCuratedPosts(Long userId, String period) {
        try {
            return postService.getTrendingPosts(PageRequest.of(0, 16), userId, period);
        } catch (RuntimeException exception) {
            log.warn("Failed to load home landing curated posts for userId={} period={}", userId, period, exception);
            return List.of();
        }
    }

    private List<BoardListResponse> getBoards(CustomUserDetails userDetails) {
        try {
            return boardService.getTopBoards(userDetails).stream()
                    .limit(6)
                    .toList();
        } catch (RuntimeException exception) {
            Long userId = userDetails != null ? userDetails.getUserId() : null;
            log.warn("Failed to load home landing boards for userId={}", userId, exception);
            return List.of();
        }
    }

    private List<PostSummary> slice(List<PostSummary> posts, int startInclusive, int endExclusive) {
        if (posts == null || posts.isEmpty() || startInclusive >= posts.size()) {
            return List.of();
        }
        return posts.subList(startInclusive, Math.min(posts.size(), endExclusive));
    }
}
