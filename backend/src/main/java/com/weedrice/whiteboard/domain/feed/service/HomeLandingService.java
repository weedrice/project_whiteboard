package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.dto.BoardListResponse;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.feed.dto.HomeLandingResponse;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeLandingService {

    private final PostService postService;
    private final BoardService boardService;

    public HomeLandingResponse getLanding(CustomUserDetails userDetails) {
        Long userId = userDetails != null ? userDetails.getUserId() : null;
        List<PostSummary> curatedPosts = getCuratedPosts(userId);
        List<BoardListResponse> boards = getBoards(userDetails);

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
                .stats(HomeLandingResponse.Stats.builder()
                        .boardCount(boards.size())
                        .postCount(curatedPosts.size())
                        .liveCount(liveActivity.size())
                        .build())
                .build();
    }

    private List<PostSummary> getCuratedPosts(Long userId) {
        try {
            return postService.getTrendingPosts(PageRequest.of(0, 16), userId);
        } catch (RuntimeException exception) {
            log.warn("Failed to load home landing curated posts for userId={}", userId, exception);
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
