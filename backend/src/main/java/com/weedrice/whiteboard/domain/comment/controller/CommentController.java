package com.weedrice.whiteboard.domain.comment.controller;

import com.weedrice.whiteboard.domain.comment.dto.CommentCreateRequest;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentUpdateRequest;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> getComments(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        Pageable pageable = PageRequestUtils.of(page, size, sort);
        return ApiResponse.success(new PageResponse<>(commentService.getComments(postId, userId, pageable)));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ApiResponse<CommentListResponse> getReplies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        Pageable pageable = PageRequestUtils.of(page, size, sort);
        return ApiResponse.success(commentService.getReplies(commentId, userId, pageable));
    }

    @GetMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> getComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = (userDetails != null) ? userDetails.getUserId() : null;
        CommentResponse comment = commentService.getComment(commentId, userId);
        return ApiResponse.success(comment);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Comment comment = commentService.createComment(userDetails.getUserId(), postId, request.getParentId(),
                request.getContent());
        return ApiResponse.success(comment.getCommentId());
    }

    @PutMapping("/comments/{commentId}")
    public ApiResponse<Long> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Comment comment = commentService.updateComment(userDetails.getUserId(), commentId, request.getContent());
        return ApiResponse.success(comment.getCommentId());
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.deleteComment(userDetails.getUserId(), commentId);
        return ApiResponse.success(null);
    }

    @PostMapping("/comments/{commentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> likeComment(@PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.likeComment(userDetails.getUserId(), commentId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ApiResponse<Void> unlikeComment(@PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.unlikeComment(userDetails.getUserId(), commentId);
        return ApiResponse.success(null);
    }
}
