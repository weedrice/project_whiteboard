package com.weedrice.whiteboard.domain.message.controller;

import com.weedrice.whiteboard.domain.message.dto.MessageCreateRequest;
import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.service.MessageService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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
                        @CurrentUserId Long userId) {
                return ApiResponse.success(messageService.sendMessage(
                                userId,
                                request.getReceiverId(),
                                request.getContent()));
        }

        @GetMapping("/received")
        public ApiResponse<MessageResponse> getReceivedMessages(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort,
                @CurrentUserId Long userId) {
                Pageable pageable = PageRequestUtils.of(page, size);
                return ApiResponse.success(messageService.getReceivedMessages(userId, pageable));
        }

        @GetMapping("/sent")
        public ApiResponse<MessageResponse> getSentMessages(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort,
                @CurrentUserId Long userId) {
                Pageable pageable = PageRequestUtils.of(page, size);
                return ApiResponse.success(messageService.getSentMessages(userId, pageable));
        }

        @GetMapping("/conversations")
        public ApiResponse<MessageResponse> getConversations(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort,
                @CurrentUserId Long userId) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                return ApiResponse.success(messageService.getConversations(userId, pageable));
        }

        @GetMapping("/conversations/{partnerId}")
        public ApiResponse<MessageResponse> getConversation(
                        @PathVariable Long partnerId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort,
                @CurrentUserId Long userId) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                return ApiResponse.success(messageService.getConversation(userId, partnerId, pageable));
        }

        @GetMapping("/{messageId}")
        public ApiResponse<MessageResponse.MessageSummary> getMessage(
                        @PathVariable Long messageId,
                        @CurrentUserId Long userId) {
                return ApiResponse.success(messageService.getMessageSummary(userId, messageId));
        }

        @PostMapping("/{messageId}/read")
        public ApiResponse<Void> markAsRead(
                        @PathVariable Long messageId,
                        @CurrentUserId Long userId) {
                messageService.markAsRead(userId, messageId);
                return ApiResponses.ok();
        }

        @DeleteMapping("/{messageId}")
        public ApiResponse<Void> deleteMessage(
                        @PathVariable Long messageId,
                        @CurrentUserId Long userId) {
                messageService.deleteMessage(userId, messageId);
                return ApiResponses.ok();
        }

        @DeleteMapping
        public ApiResponse<Void> deleteMessages(
                        @RequestBody @Size(max = 500) java.util.List<@NotNull @Positive Long> messageIds,
                        @CurrentUserId Long userId) {
                messageService.deleteMessages(userId, messageIds);
                return ApiResponses.ok();
        }

        @GetMapping("/unread-count")
        public ApiResponse<Long> getUnreadMessageCount(@CurrentUserId Long userId) {
                return ApiResponse.success(messageService.getUnreadMessageCount(userId));
        }
}
