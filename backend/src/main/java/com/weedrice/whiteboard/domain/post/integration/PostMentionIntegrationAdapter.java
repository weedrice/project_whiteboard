package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.service.MentionService;
import com.weedrice.whiteboard.domain.post.port.PostMentionPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostMentionIntegrationAdapter implements PostMentionPort {

    private final UserRepository userRepository;
    private final MentionService mentionService;

    @Override
    public void publishMentions(
            Long userId,
            Long agentId,
            NotificationSourceType sourceType,
            Long sourceId,
            String contents) {
        mentionService.publishMentions(requireUser(userId), agentId, sourceType, sourceId, contents);
    }

    @Override
    public void publishNewMentions(
            Long userId,
            Long agentId,
            NotificationSourceType sourceType,
            Long sourceId,
            String originalContents,
            String updatedContents) {
        mentionService.publishNewMentions(
                requireUser(userId), agentId, sourceType, sourceId, originalContents, updatedContents);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
