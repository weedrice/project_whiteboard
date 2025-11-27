package com.weedrice.whiteboard.domain.post.controller;

import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final SearchService searchService;

    @GetMapping("/boards/{boardId}/posts")
    public ApiResponse<PageResponse<PostSummary>> getPosts(
            @PathVariable Long boardId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer minLikes,
            Pageable pageable,
            Authentication authentication) {

        if (keyword != null && !keyword.trim().isEmpty()) {
            Long userId = null;
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
                userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
            }
            searchService.recordSearch(userId, keyword);
        }

        Page<Post> posts = postService.getPosts(boardId, categoryId, minLikes, pageable);
        return ApiResponse.success(new PageResponse<>(posts.map(PostSummary::from)));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostResponse> getPost(@PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;

        Post post = postService.getPostById(postId, userId);
        List<String> tags = postService.getTagsForPost(postId);
        boolean isLiked = postService.isPostLikedByUser(postId, userId); // isLiked 값을 올바르게 가져옴
        boolean isScrapped = postService.isPostScrappedByUser(postId, userId);
        ViewHistory viewHistory = postService.getViewHistory(userId, postId); // ViewHistory를 올바르게 가져옴

        return ApiResponse.success(PostResponse.from(post, tags, viewHistory, isLiked, isScrapped));
    }

    @PostMapping("/boards/{boardId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createPost(
            @PathVariable Long boardId,
            @Valid @RequestBody PostCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Post post = postService.createPost(userDetails.getUserId(), boardId, request);
        return ApiResponse.success(post.getPostId());
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<Long> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Post post = postService.updatePost(userDetails.getUserId(), postId, request);
        return ApiResponse.success(post.getPostId());
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        postService.deletePost(userDetails.getUserId(), postId);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{postId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Integer> likePost(@PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        postService.likePost(userDetails.getUserId(), postId);
        Post post = postService.getPostById(postId, null);
        return ApiResponse.success(post.getLikeCount());
    }

    @DeleteMapping("/posts/{postId}/like")
    public ApiResponse<Integer> unlikePost(@PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        postService.unlikePost(userDetails.getUserId(), postId);
        Post post = postService.getPostById(postId, null);
        return ApiResponse.success(post.getLikeCount());
    }

    @PostMapping("/posts/{postId}/scrap")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> scrapPost(
            @PathVariable Long postId,
            @RequestBody(required = false) PostScrapRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String remark = (request != null) ? request.getRemark() : null;
        postService.scrapPost(userDetails.getUserId(), postId, remark);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/posts/{postId}/scrap")
    public ApiResponse<Void> unscrapPost(@PathVariable Long postId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        postService.unscrapPost(userDetails.getUserId(), postId);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me/scraps")
    public ApiResponse<ScrapListResponse> getMyScraps(@AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ApiResponse.success(ScrapListResponse.from(postService.getMyScraps(userDetails.getUserId(), pageable)));
    }

    @GetMapping("/users/me/drafts")
    public ApiResponse<DraftListResponse> getMyDrafts(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse
                .success(DraftListResponse.from(postService.getDraftPosts(userDetails.getUserId(), pageable)));
    }

    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftResponse> getDraft(@PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(DraftResponse.from(postService.getDraftPost(userDetails.getUserId(), draftId)));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> saveDraft(
            @Valid @RequestBody PostDraftRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(postService.saveDraftPost(userDetails.getUserId(), request).getDraftId());
    }

    @DeleteMapping("/drafts/{draftId}")
    public ApiResponse<Void> deleteDraft(@PathVariable Long draftId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        postService.deleteDraftPost(userDetails.getUserId(), draftId);
        return ApiResponse.success(null);
    }

    @GetMapping("/posts/{postId}/versions")
    public ApiResponse<List<PostVersionResponse>> getPostVersions(@PathVariable Long postId) {
        return ApiResponse.success(PostVersionResponse.from(postService.getPostVersions(postId)));
    }
}
