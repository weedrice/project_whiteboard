package com.weedrice.whiteboard.domain.message.controller;

import com.weedrice.whiteboard.domain.message.dto.MessageCreateRequest;
import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.service.MessageService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

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
                return ApiResponse.success(messageService.sendMessage(
                                requiredUserId(userDetails),
                                request.getReceiverId(),
                                request.getContent()));
        }

        @GetMapping("/received")
        public ApiResponse<MessageResponse> getReceivedMessages(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                return ApiResponse.success(messageService.getReceivedMessages(requiredUserId(userDetails), pageable));
        }

        @GetMapping("/sent")
        public ApiResponse<MessageResponse> getSentMessages(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                return ApiResponse.success(messageService.getSentMessages(requiredUserId(userDetails), pageable));
        }

        @GetMapping("/{messageId}")
        public ApiResponse<MessageResponse.MessageSummary> getMessage(
                        @PathVariable Long messageId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ApiResponse.success(messageService.getMessageSummary(requiredUserId(userDetails), messageId));
        }

        @PostMapping("/{messageId}/read")
        public ApiResponse<Void> markAsRead(
                        @PathVariable Long messageId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                messageService.markAsRead(requiredUserId(userDetails), messageId);
                return okVoid();
        }

        @DeleteMapping("/{messageId}")
        public ApiResponse<Void> deleteMessage(
                        @PathVariable Long messageId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                messageService.deleteMessage(requiredUserId(userDetails), messageId);
                return okVoid();
        }

        @DeleteMapping
        public ApiResponse<Void> deleteMessages(
                        @RequestBody java.util.List<Long> messageIds,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                messageService.deleteMessages(requiredUserId(userDetails), messageIds);
                return okVoid();
        }

        @GetMapping("/unread-count")
        public ApiResponse<Long> getUnreadMessageCount(@AuthenticationPrincipal CustomUserDetails userDetails) {
                return ApiResponse.success(messageService.getUnreadMessageCount(requiredUserId(userDetails)));
        }

        private ApiResponse<Void> okVoid() {
                return ApiResponse.success();
        }
}
