package com.weedrice.whiteboard.domain.user.integration;

import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.user.port.UserContentReindexPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserContentReindexIntegrationAdapter implements UserContentReindexPort {
    private final SemanticSearchEventPublisher eventPublisher;

    @Override
    public void reindexRestoredAuthor(Long userId) {
        eventPublisher.publishAuthorRestored(userId);
    }
}
