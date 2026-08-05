package com.weedrice.whiteboard.domain.post.controller;

import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.post.constant.PostDraftPolicy;
import com.weedrice.whiteboard.domain.post.dto.*;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.jspecify.annotations.NonNull;
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

    @GetMapping("/posts/{postId}/related")
    public ApiResponse<List<PostSummary>> getRelatedPosts(
            @PathVariable Long postId,
            @RequestParam(required = false) Integer size,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(postService.getRelatedPosts(postId, userId, size));
    }

    @PostMapping("/posts/{postId}/manager/pin")
    public ApiResponse<Void> pinPostByManager(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        postService.pinPostByManager(userId, postId);
        return ApiResponses.ok();
    }

    @DeleteMapping("/posts/{postId}/manager/pin")
    public ApiResponse<Void> unpinPostByManager(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        postService.unpinPostByManager(userId, postId);
        return ApiResponses.ok();
    }

    @PostMapping("/posts/{postId}/manager/blind")
    public ApiResponse<Void> blindPostByManager(
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) PostManagerBlindRequest request,
            @CurrentUserId Long userId) {
        postService.blindPostByManager(userId, postId, request != null ? request.getReason() : null);
        return ApiResponses.ok();
    }

    @DeleteMapping("/posts/{postId}/manager/blind")
    public ApiResponse<Void> unblindPostByManager(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        postService.unblindPostByManager(userId, postId);
        return ApiResponses.ok();
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
        Long folderId = (request != null) ? request.getFolderId() : null;
        postService.scrapPost(userId, postId, remark, folderId);
        return ApiResponses.ok();
    }

    @DeleteMapping("/posts/{postId}/scrap")
    public ApiResponse<Void> unscrapPost(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        postService.unscrapPost(userId, postId);
        return ApiResponses.ok();
    }

    @PatchMapping("/users/me/scraps/{postId}")
    public ApiResponse<Void> moveScrap(
            @PathVariable Long postId,
            @Valid @RequestBody ScrapFolderAssignmentRequest request,
            @CurrentUserId Long userId) {
        postService.moveScrap(userId, postId, request.getFolderId());
        return ApiResponses.ok();
    }

    @GetMapping("/users/me/scraps")
    public ApiResponse<ScrapListResponse> getMyScraps(
            @NonNull Pageable pageable,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false, name = "q") String keyword,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.getMyScraps(userId, folderId, keyword, pageable));
    }

    @GetMapping("/users/me/scrap-folders")
    public ApiResponse<List<ScrapFolderResponse>> getScrapFolders(@CurrentUserId Long userId) {
        return ApiResponse.success(postService.getScrapFolders(userId));
    }

    @PostMapping("/users/me/scrap-folders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ScrapFolderResponse> createScrapFolder(
            @Valid @RequestBody ScrapFolderRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.createScrapFolder(userId, request));
    }

    @PatchMapping("/users/me/scrap-folders/{folderId}")
    public ApiResponse<ScrapFolderResponse> updateScrapFolder(
            @PathVariable Long folderId,
            @Valid @RequestBody ScrapFolderRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.updateScrapFolder(userId, folderId, request));
    }

    @DeleteMapping("/users/me/scrap-folders/{folderId}")
    public ApiResponse<Void> deleteScrapFolder(
            @PathVariable Long folderId,
            @CurrentUserId Long userId) {
        postService.deleteScrapFolder(userId, folderId);
        return ApiResponses.ok();
    }

    @GetMapping("/users/me/post-series")
    public ApiResponse<List<PostSeriesResponse>> getMySeries(@CurrentUserId Long userId) {
        return ApiResponse.success(postService.getMySeries(userId));
    }

    @PostMapping("/users/me/post-series")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PostSeriesResponse> createSeries(
            @Valid @RequestBody PostSeriesRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.createSeries(userId, request));
    }

    @PatchMapping("/users/me/post-series/{seriesId}")
    public ApiResponse<PostSeriesResponse> updateSeries(
            @PathVariable Long seriesId,
            @Valid @RequestBody PostSeriesRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.updateSeries(userId, seriesId, request));
    }

    @DeleteMapping("/users/me/post-series/{seriesId}")
    public ApiResponse<Void> deleteSeries(
            @PathVariable Long seriesId,
            @CurrentUserId Long userId) {
        postService.deleteSeries(userId, seriesId);
        return ApiResponses.ok();
    }

    @GetMapping("/users/me/drafts")
    public ApiResponse<DraftListResponse> getMyDrafts(
            @NonNull Pageable pageable,
            @CurrentUserId Long userId) {
        return ApiResponse
                .success(postService.getDraftPosts(userId, pageable));
    }

    @GetMapping("/users/me/drafts/match")
    public ApiResponse<DraftMatchResponse> getMatchingDraft(
            @RequestParam
            @NotBlank
            @Size(max = BoardUrlNormalizer.MAX_BOARD_URL_LENGTH)
            @Pattern(regexp = BoardUrlNormalizer.BOARD_URL_PATTERN, message = "{validation.board.url.pattern}")
            String boardUrl,
            @RequestParam(required = false) Long originalPostId,
            @RequestParam(required = false)
            @Size(max = PostDraftPolicy.MAX_CLIENT_DRAFT_KEY_LENGTH)
            @Pattern(regexp = PostDraftPolicy.CLIENT_DRAFT_KEY_PATTERN)
            String clientDraftKey,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.getMatchingDraft(
                userId, boardUrl, originalPostId, clientDraftKey));
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
            @RequestParam(required = false) @PositiveOrZero Long version,
            @CurrentUserId Long userId) {
        postService.deleteDraftPost(userId, draftId, version);
        return ApiResponses.ok();
    }

    @GetMapping("/posts/{postId}/versions")
    public ApiResponse<List<PostVersionResponse>> getPostVersions(
            @PathVariable Long postId,
            @CurrentUserId Long userId) {
        return ApiResponse.success(postService.getPostVersions(postId, userId));
    }
}
