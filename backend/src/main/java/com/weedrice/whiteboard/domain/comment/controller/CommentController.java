package com.weedrice.whiteboard.domain.comment.controller;

import com.weedrice.whiteboard.domain.comment.dto.CommentCreateRequest;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentUpdateRequest;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<Page<CommentResponse>> getComments(
            @PathVariable Long postId,
            Pageable pageable) {
        Page<CommentResponse> comments = commentService.getComments(postId, pageable);
        return ApiResponse.success(comments);
    }

    @GetMapping("/comments/{commentId}/replies")
    public ApiResponse<CommentListResponse> getReplies(
            @PathVariable Long commentId,
            Pageable pageable) {
        Page<Comment> replies = commentService.getReplies(commentId, pageable);
        return ApiResponse.success(CommentListResponse.from(replies));
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Comment comment = commentService.createComment(userDetails.getUserId(), postId, request.getParentId(), request.getContent());
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
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.deleteComment(userDetails.getUserId(), commentId);
        return ApiResponse.success(null);
    }

    @PostMapping("/comments/{commentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> likeComment(@PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.likeComment(userDetails.getUserId(), commentId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ApiResponse<Void> unlikeComment(@PathVariable Long commentId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.unlikeComment(userDetails.getUserId(), commentId);
        return ApiResponse.success(null);
    }
}
