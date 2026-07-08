package com.weedrice.whiteboard.domain.comment.controller;

import com.weedrice.whiteboard.domain.comment.dto.CommentCreateRequest;
import com.weedrice.whiteboard.domain.comment.dto.CommentCreateResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentUpdateRequest;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSorts;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @CurrentUserId(required = false) Long userId) {
        Pageable pageable = PageRequestUtils.of(
                page,
                size,
                sort,
                CommentReadSorts.READ_ORDER,
                CommentReadSorts.ALLOWED_ROOT_SORT_PROPERTIES);
        return ApiResponses.page(commentService.getComments(postId, userId, pageable));
    }

    @GetMapping("/posts/{postId}/comments/best")
    public ApiResponse<List<CommentResponse>> getBestComments(
            @PathVariable Long postId,
            @CurrentUserId(required = false) Long userId) {
        return ApiResponse.success(commentService.getBestComments(postId, userId));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ApiResponse<CommentListResponse> getReplies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserId(required = false) Long userId) {
        Pageable pageable = PageRequestUtils.of(page, size, CommentReadSorts.READ_ORDER);
        return ApiResponse.success(commentService.getReplies(commentId, userId, pageable));
    }

    @GetMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> getComment(
            @PathVariable Long commentId,
            @CurrentUserId(required = false) Long userId) {
        CommentResponse comment = commentService.getComment(commentId, userId);
        return ApiResponse.success(comment);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommentCreateResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @CurrentUserId Long userId) {
        CommentCreateResponse response = commentService.createCommentWithResponse(
                userId,
                postId,
                request.getParentId(),
                request.getContent(),
                request.getMentionedUserIds());
        return ApiResponse.success(response);
    }

    @PutMapping("/comments/{commentId}")
    public ApiResponse<Long> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @CurrentUserId Long userId) {
        Long updatedCommentId = commentService.updateComment(
                userId,
                commentId,
                request.getContent(),
                request.getMentionedUserIds());
        return ApiResponse.success(updatedCommentId);
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId,
            @CurrentUserId Long userId) {
        commentService.deleteComment(userId, commentId);
        return ApiResponses.ok();
    }

    @PostMapping("/comments/{commentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> likeComment(@PathVariable Long commentId,
            @CurrentUserId Long userId) {
        commentService.likeComment(userId, commentId);
        return ApiResponses.ok();
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ApiResponse<Void> unlikeComment(@PathVariable Long commentId,
            @CurrentUserId Long userId) {
        commentService.unlikeComment(userId, commentId);
        return ApiResponses.ok();
    }
}
