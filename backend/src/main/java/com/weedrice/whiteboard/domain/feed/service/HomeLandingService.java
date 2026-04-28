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
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
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

        long postsToday = postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                todayStart,
                tomorrowStart);
        long totalPosts = postRepository.countVisiblePostsForAdminDashboard();
        long postsYesterday = postRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                yesterdayStart,
                todayStart);
        long activeBoardCount = boardRepository.countByIsActiveTrueAndIsPublicTrue();
        long newMembersLast24Hours = userRepository.countByStatusAndDeletedAtIsNullAndCreatedAtAfter(
                User.STATUS_ACTIVE,
                twentyFourHoursAgo);
        long onlineCount = userRepository.countRecentlyLoggedInActiveUsersForAdminDashboard(twentyFourHoursAgo);
        long commentsToday = commentRepository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndIsDeletedFalse(
                todayStart,
                tomorrowStart);

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

    private List<PostSummary> getCuratedPosts(Long userId, String period) {
        return postService.getTrendingPosts(PageRequest.of(0, 16), userId, period);
    }

    private List<BoardListResponse> getBoards(CustomUserDetails userDetails) {
        return boardService.getTopBoards(userDetails).stream()
                .limit(6)
                .toList();
    }

    private List<PostSummary> slice(List<PostSummary> posts, int startInclusive, int endExclusive) {
        if (posts == null || posts.isEmpty() || startInclusive >= posts.size()) {
            return List.of();
        }
        return posts.subList(startInclusive, Math.min(posts.size(), endExclusive));
    }
}
