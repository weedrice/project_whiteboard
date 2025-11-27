package com.weedrice.whiteboard.domain.message.dto;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
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
        private boolean isRead;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String displayName;
    }

    public static MessageResponse from(Page<Message> messagePage, Long currentUserId) {
        List<MessageSummary> content = messagePage.getContent().stream()
                .map(message -> {
                    User partner = message.getSender().getUserId().equals(currentUserId) ? message.getReceiver() : message.getSender();
                    return MessageSummary.builder()
                            .messageId(message.getMessageId())
                            .partner(UserInfo.builder()
                                    .userId(partner.getUserId())
                                    .displayName(partner.getDisplayName())
                                    .build())
                            .content(message.getContent())
                            .isRead("Y".equals(message.getIsRead()))
                            .createdAt(message.getCreatedAt())
                            .build();
                })
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
