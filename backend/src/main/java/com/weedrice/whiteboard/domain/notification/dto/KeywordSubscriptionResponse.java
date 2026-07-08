package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class KeywordSubscriptionResponse {
    private Long subscriptionId;
    private String keyword;
    private LocalDateTime createdAt;

    public static KeywordSubscriptionResponse from(UserKeywordSubscription subscription) {
        return KeywordSubscriptionResponse.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .keyword(subscription.getKeyword())
                .createdAt(subscription.getCreatedAt())
                .build();
    }
}
