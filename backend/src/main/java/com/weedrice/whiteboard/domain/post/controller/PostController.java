package com.weedrice.whiteboard.domain.post.controller;

import com.weedrice.whiteboard.domain.post.dto.*;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.search.service.SearchService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final SearchService searchService; // SearchService 주입

    @GetMapping("/boards/{boardId}/posts")
    public ApiResponse<PostListResponse> getPosts(
            @PathVariable Long boardId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword, // 검색어 추가
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) { // Authentication 추가
        
        // 검색어 기록
        if (keyword != null && !keyword.trim().isEmpty()) {
            Long userId = null;
            if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails) {
                userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
            }
            searchService.recordSearch(userId, keyword);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Post> posts = postService.getPosts(boardId, categoryId, pageable); // TODO: keyword를 이용한 검색 로직 추가 필요
        return ApiResponse.success(PostListResponse.from(posts));
    }

    @GetMapping("/posts/{postId}")
    public ApiResponse<PostResponse> getPost(@PathVariable Long postId) {
        Post post = postService.getPostById(postId);
        postService.incrementViewCount(postId); // 조회수 증가
        List<String> tags = postService.getTagsForPost(postId); // 태그 정보 가져오기
        return ApiResponse.success(PostResponse.from(post, tags));
    }

    @PostMapping("/boards/{boardId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createPost(
            @PathVariable Long boardId,
            @Valid @RequestBody PostCreateRequest request,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Post post = postService.createPost(userId, boardId, request);
        return ApiResponse.success(post.getPostId());
    }

    @PutMapping("/posts/{postId}")
    public ApiResponse<Long> updatePost(
            @PathVariable Long postId,
            @Valid @RequestBody PostUpdateRequest request,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Post post = postService.updatePost(userId, postId, request);
        return ApiResponse.success(post.getPostId());
    }

    @DeleteMapping("/posts/{postId}")
    public ApiResponse<Void> deletePost(@PathVariable Long postId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        postService.deletePost(userId, postId);
        return ApiResponse.success(null);
    }

    @PostMapping("/posts/{postId}/like")
    public ApiResponse<Integer> togglePostLike(@PathVariable Long postId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        postService.togglePostLike(userId, postId);
        Post post = postService.getPostById(postId); // 좋아요 수 업데이트된 게시글 다시 조회
        return ApiResponse.success(post.getLikeCount());
    }

    @PostMapping("/posts/{postId}/scrap")
    public ApiResponse<Void> togglePostScrap(
            @PathVariable Long postId,
            @RequestBody(required = false) PostScrapRequest request,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        String remark = (request != null) ? request.getRemark() : null;
        postService.togglePostScrap(userId, postId, remark);
        return ApiResponse.success(null);
    }

    @GetMapping("/users/me/scraps")
    public ApiResponse<ScrapListResponse> getMyScraps(Authentication authentication, Pageable pageable) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(ScrapListResponse.from(postService.getMyScraps(userId, pageable)));
    }

    @GetMapping("/users/me/drafts")
    public ApiResponse<DraftListResponse> getMyDrafts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(DraftListResponse.from(postService.getDraftPosts(userId, pageable)));
    }

    @GetMapping("/drafts/{draftId}")
    public ApiResponse<DraftResponse> getDraft(@PathVariable Long draftId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(DraftResponse.from(postService.getDraftPost(userId, draftId)));
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> saveDraft(
            @Valid @RequestBody PostDraftRequest request,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(postService.saveDraftPost(userId, request).getDraftId());
    }

    @DeleteMapping("/drafts/{draftId}")
    public ApiResponse<Void> deleteDraft(@PathVariable Long draftId, Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        postService.deleteDraftPost(userId, draftId);
        return ApiResponse.success(null);
    }

    @GetMapping("/posts/{postId}/versions")
    public ApiResponse<List<PostVersionResponse>> getPostVersions(@PathVariable Long postId) {
        return ApiResponse.success(PostVersionResponse.from(postService.getPostVersions(postId)));
    }
}
