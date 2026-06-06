package com.weedrice.whiteboard.domain.comment.controller;

import com.weedrice.whiteboard.domain.comment.dto.CommentCreateRequest;
import com.weedrice.whiteboard.domain.comment.dto.CommentListResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentResponse;
import com.weedrice.whiteboard.domain.comment.dto.CommentUpdateRequest;
import com.weedrice.whiteboard.domain.comment.service.CommentReadSorts;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.optionalUserId;
import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

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
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        Pageable pageable = PageRequestUtils.of(page, size, CommentReadSorts.READ_ORDER);
        return ApiResponse.success(new PageResponse<>(commentService.getComments(postId, userId, pageable)));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ApiResponse<CommentListResponse> getReplies(
            @PathVariable Long commentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        Pageable pageable = PageRequestUtils.of(page, size, CommentReadSorts.READ_ORDER);
        return ApiResponse.success(commentService.getReplies(commentId, userId, pageable));
    }

    @GetMapping("/comments/{commentId}")
    public ApiResponse<CommentResponse> getComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = optionalUserId(userDetails);
        CommentResponse comment = commentService.getComment(commentId, userId);
        return ApiResponse.success(comment);
    }

    @PostMapping("/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long commentId = commentService.createComment(
                requiredUserId(userDetails),
                postId,
                request.getParentId(),
                request.getContent());
        return ApiResponse.success(commentId);
    }

    @PutMapping("/comments/{commentId}")
    public ApiResponse<Long> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody CommentUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long updatedCommentId = commentService.updateComment(
                requiredUserId(userDetails),
                commentId,
                request.getContent());
        return ApiResponse.success(updatedCommentId);
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.deleteComment(requiredUserId(userDetails), commentId);
        return okVoid();
    }

    @PostMapping("/comments/{commentId}/like")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> likeComment(@PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.likeComment(requiredUserId(userDetails), commentId);
        return okVoid();
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ApiResponse<Void> unlikeComment(@PathVariable Long commentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        commentService.unlikeComment(requiredUserId(userDetails), commentId);
        return okVoid();
    }

    private ApiResponse<Void> okVoid() {
        return ApiResponse.success();
    }
}
