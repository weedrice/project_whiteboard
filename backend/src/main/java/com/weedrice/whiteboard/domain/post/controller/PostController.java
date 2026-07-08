package com.weedrice.whiteboard.domain.post.controller;

import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.service.PollService;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.ratelimit.CounterEventGuard;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final PollService pollService;
    private final ClientIpResolver clientIpResolver;
    private final CounterEventGuard counterEventGuard;

    @GetMapping("/boards/{boardUrl}/posts")
    public ApiResponse<PageResponse<PostSummary>> getPosts(
            @PathVariable String boardUrl,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minLikes,
            @CurrentUserId(required = false) Long userId,
            @NonNull Pageable pageable) {

        Page<PostSummary> summaryPage = postService.getPosts(boardUrl, categoryId, keyword, minLikes, userId, pageable);

        return ApiResponses.page(summaryPage);
    }

    @GetMapping("/posts/trending")
    public ApiResponse<PageResponse<PostSummary>> getTrendingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "24h") String period,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponses.page(postService.getTrendingPostsPage(PageRequestUtils.of(page, size), userId, period));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostResponse> getPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "false") boolean incrementView,
            @RequestParam(defaultValue = "20") int boardListPageSize,
            @CurrentUserId(required = false) Long userId,
            HttpServletRequest request) {
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        String ipAddress = clientIpResolver.resolve(request);
        boolean shouldIncrementView = incrementView
                && counterEventGuard.tryMarkRecorded("post-view", postId, userId, ipAddress);
        PostResponse response = postService.getPostResponse(postId, userId, shouldIncrementView,
                normalizedBoardListPageSize);
        return ApiResponse.success(
                response);
    }

    @PostMapping("/posts/{postId}/view")
    public ApiResponse<Void> incrementPostView(
            @PathVariable Long postId,
            @CurrentUserId(required = false) Long userId,
            HttpServletRequest request) {
        String ipAddress = clientIpResolver.resolve(request);
        if (!counterEventGuard.tryMarkRecorded("post-view", postId, userId, ipAddress)) {
            return ApiResponses.ok();
        }
        postService.incrementViewCount(postId, userId);
        return ApiResponses.ok();
    }

    @PutMapping("/posts/{postId}/history")
    public ApiResponse<Void> updateViewHistory(
            @PathVariable Long postId,
            @Valid @RequestBody ViewHistoryRequest request,
            @CurrentUserId Long userId) {
        postService.updateViewHistory(userId, postId, request);
        return ApiResponses.ok();
    }

    @PostMapping("/boards/{boardUrl}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostCreateResponse> createPost(
            @PathVariable String boardUrl,
            @Valid @RequestBody PostCreateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.createPostWithResponse(userId, boardUrl, request));
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<Long> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.updatePost(userId, postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        postService.deletePost(userId, postId);
        return ApiResponses.ok();
    }

    @PostMapping("/posts/{postId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Integer> likePost(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.likePost(userId, postId));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResponse<Integer> unlikePost(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.unlikePost(userId, postId));
    }

    @PostMapping("/posts/{postId}/poll/vote")
    public ApiResponse<PollResponse> votePoll(
            @PathVariable Long postId,
            @Valid @RequestBody PollVoteRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(pollService.vote(userId, postId, request.getOptionIds()));
    }

    @DeleteMapping("/posts/{postId}/poll/vote")
    public ApiResponse<PollResponse> deletePollVote(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(pollService.deleteVote(userId, postId));
    }

    @PostMapping("/posts/{postId}/scrap")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> scrapPost(
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) PostScrapRequest request,
            @CurrentUserId Long userId) {
        String remark = (request != null) ? request.getRemark() : null;
        postService.scrapPost(userId, postId, remark);
        return ApiResponses.ok();
    }

    @DeleteMapping("/posts/{postId}/scrap")
    public ApiResponse<Void> unscrapPost(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        postService.unscrapPost(userId, postId);
        return ApiResponses.ok();
    }

    @GetMapping("/users/me/scraps")
    public ApiResponse<ScrapListResponse> getMyScraps(
            @NonNull Pageable pageable,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.getMyScraps(userId, pageable));
    }

    @GetMapping("/users/me/drafts")
    public ApiResponse<DraftListResponse> getMyDrafts(
            @NonNull Pageable pageable,
            @CurrentUserId Long userId) {
        return ApiResponse
                .success(postService.getDraftPosts(userId, pageable));
    }

    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftResponse> getDraft(
            @PathVariable Long draftId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.getDraftPost(userId, draftId));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DraftResponse> saveDraft(
            @Valid @RequestBody PostDraftRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.saveDraftPost(userId, request));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ApiResponse<Void> deleteDraft(
            @PathVariable Long draftId,
            @CurrentUserId Long userId) {
        postService.deleteDraftPost(userId, draftId);
        return ApiResponses.ok();
    }

    @GetMapping("/posts/{postId}/versions")
    public ApiResponse<List<PostVersionResponse>> getPostVersions(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.getPostVersions(postId, userId));
    }
}
