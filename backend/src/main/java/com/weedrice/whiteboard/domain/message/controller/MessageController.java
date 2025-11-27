package com.weedrice.whiteboard.domain.message.controller;

import com.weedrice.whiteboard.domain.message.dto.MessageCreateRequest;
import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.service.MessageService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> sendMessage(
            @Valid @RequestBody MessageCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(messageService.sendMessage(userDetails.getUserId(), request.getReceiverId(), request.getContent()).getMessageId());
    }

    @GetMapping("/received")
    public ApiResponse<MessageResponse> getReceivedMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ApiResponse.success(MessageResponse.from(messageService.getReceivedMessages(userDetails.getUserId(), pageable), userDetails.getUserId()));
    }

    @GetMapping("/sent")
    public ApiResponse<MessageResponse> getSentMessages(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ApiResponse.success(MessageResponse.from(messageService.getSentMessages(userDetails.getUserId(), pageable), userDetails.getUserId()));
    }

    @GetMapping("/{messageId}")
    public ApiResponse<MessageResponse.MessageSummary> getMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        // 이 부분은 단일 메시지를 반환하도록 수정이 필요할 수 있음
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Void> deleteMessage(
            @PathVariable Long messageId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        messageService.deleteMessage(userDetails.getUserId(), messageId);
        return ApiResponse.success(null);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadMessageCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(messageService.getUnreadMessageCount(userDetails.getUserId()));
    }
}
