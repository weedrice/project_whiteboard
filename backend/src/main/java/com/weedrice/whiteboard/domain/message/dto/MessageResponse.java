package com.weedrice.whiteboard.domain.message.dto;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.util.InputSanitizer;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class MessageResponse {
    private List<MessageSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class MessageSummary {
        private Long messageId;
        private UserInfo partner; // 상대방 정보
        private String content;
        private Boolean isRead;
        private Boolean sentByMe;
        private LocalDateTime createdAt;

        public static MessageSummary from(Message message, Long currentUserId) {
            boolean sentByCurrentUser = message.getSender().getUserId().equals(currentUserId);
            User partner = sentByCurrentUser ? message.getReceiver()
                    : message.getSender();
            return MessageSummary.builder()
                    .messageId(message.getMessageId())
                    .partner(UserInfo.from(partner))
                    .content(InputSanitizer.stripHtml(message.getContent()))
                    .isRead(message.getIsRead())
                    .sentByMe(sentByCurrentUser)
                    .createdAt(message.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String displayName;

        public static UserInfo from(User user) {
            return UserInfo.builder()
                    .userId(user.getUserId())
                    .displayName(user.getDisplayName())
                    .build();
        }
    }

    public static MessageResponse from(Page<Message> messagePage, Long currentUserId) {
        List<MessageSummary> content = messagePage.getContent().stream()
                .map(message -> MessageSummary.from(message, currentUserId))
                .collect(Collectors.toList());

        return MessageResponse.builder()
                .content(content)
                .page(messagePage.getNumber())
                .size(messagePage.getSize())
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .hasNext(messagePage.hasNext())
                .hasPrevious(messagePage.hasPrevious())
                .build();
    }
}
