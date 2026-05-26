package com.weedrice.whiteboard.domain.post.controller;

import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.optionalUserId;
import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @GetMapping("/boards/{boardUrl}/posts")
    public ApiResponse<PageResponse<PostSummary>> getPosts(
            @PathVariable String boardUrl,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minLikes,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @NonNull Pageable pageable) {

        Long userId = optionalUserId(userDetails);
        Page<PostSummary> summaryPage = postService.getPosts(boardUrl, categoryId, keyword, minLikes, userId, pageable);

        return pageResponse(summaryPage);
    }

    @GetMapping("/posts/trending")
    public ApiResponse<PageResponse<PostSummary>> getTrendingPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "24h") String period,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        return pageResponse(postService.getTrendingPostsPage(PageRequestUtils.of(page, size), userId, period));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostResponse> getPost(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "false") boolean incrementView,
            @RequestParam(defaultValue = "20") int boardListPageSize,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        int normalizedBoardListPageSize = PageRequestUtils.of(0, boardListPageSize).getPageSize();
        return ApiResponse.success(
                postService.getPostResponse(postId, userId, incrementView, normalizedBoardListPageSize));
    }

    @PostMapping("/posts/{postId}/view")
    public ApiResponse<Void> incrementPostView(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        postService.incrementViewCount(postId, userId);
        return ApiResponse.success(null);
    }

    @PutMapping("/posts/{postId}/history")
    public ApiResponse<Void> updateViewHistory(
            @PathVariable Long postId,
            @Valid @RequestBody ViewHistoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        postService.updateViewHistory(userId, postId, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/boards/{boardUrl}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createPost(
            @PathVariable String boardUrl,
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.createPost(userId, boardUrl, request));
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<Long> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.updatePost(userId, postId, request));
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        postService.deletePost(userId, postId);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{postId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Integer> likePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.likePost(userId, postId));
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResponse<Integer> unlikePost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.unlikePost(userId, postId));
    }

    @PostMapping("/posts/{postId}/scrap")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> scrapPost(
            @PathVariable Long postId,
            @Valid @RequestBody(required = false) PostScrapRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String remark = (request != null) ? request.getRemark() : null;
        Long userId = requiredUserId(userDetails);
        postService.scrapPost(userId, postId, remark);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/posts/{postId}/scrap")
    public ApiResponse<Void> unscrapPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        postService.unscrapPost(userId, postId);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me/scraps")
    public ApiResponse<ScrapListResponse> getMyScraps(
            @NonNull Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.getMyScraps(userId, pageable));
    }

    @GetMapping("/users/me/drafts")
    public ApiResponse<DraftListResponse> getMyDrafts(
            @NonNull Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse
                .success(postService.getDraftPosts(userId, pageable));
    }

    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftResponse> getDraft(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.getDraftPost(userId, draftId));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DraftResponse> saveDraft(
            @Valid @RequestBody PostDraftRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.saveDraftPost(userId, request));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ApiResponse<Void> deleteDraft(
            @PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        postService.deleteDraftPost(userId, draftId);
        return ApiResponse.success(null);
    }

    @GetMapping("/posts/{postId}/versions")
    public ApiResponse<List<PostVersionResponse>> getPostVersions(
            @PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(postService.getPostVersions(postId, userId));
    }

    private <T> ApiResponse<PageResponse<T>> pageResponse(Page<T> page) {
        return ApiResponse.success(new PageResponse<>(page));
    }
}
