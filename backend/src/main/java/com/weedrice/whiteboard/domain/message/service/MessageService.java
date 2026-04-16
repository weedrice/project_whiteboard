package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service("messageDomainService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;

    @Transactional
    public Message sendMessage(Long senderId, Long receiverId, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (userBlockService.isEitherDirectionBlocked(senderId, receiverId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_USER);
        }

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content)
                .build();
        return messageRepository.save(message);
    }

    public MessageResponse getReceivedMessages(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        Page<Message> messages = messageRepository.findReceivedMessagesExcludingBlocked(user, false, blockedUserIds,
                pageable);
        return MessageResponse.from(messages, userId);
    }

    public MessageResponse getSentMessages(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        Page<Message> messages = messageRepository.findSentMessagesExcludingBlocked(user, false, blockedUserIds,
                pageable);
        return MessageResponse.from(messages, userId);
    }

    @Transactional
    public Message getMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!message.getReceiver().getUserId().equals(userId) && !message.getSender().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Long partnerUserId = message.getSender().getUserId().equals(userId)
                ? message.getReceiver().getUserId()
                : message.getSender().getUserId();
        if (userBlockService.isEitherDirectionBlocked(userId, partnerUserId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_USER);
        }

        if (message.getReceiver().getUserId().equals(userId)) {
            message.markAsRead();
        }

        return message;
    }

    @Transactional
    public MessageResponse.MessageSummary getMessageSummary(Long userId, Long messageId) {
        Message message = getMessage(userId, messageId);
        User partner = message.getSender().getUserId().equals(userId) ? message.getReceiver() : message.getSender();

        return MessageResponse.MessageSummary.builder()
                .messageId(message.getMessageId())
                .partner(MessageResponse.UserInfo.builder()
                        .userId(partner.getUserId())
                        .displayName(partner.getDisplayName())
                        .build())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (message.getSender().getUserId().equals(userId)) {
            message.deleteBySender();
        } else if (message.getReceiver().getUserId().equals(userId)) {
            message.deleteByReceiver();
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if (message.getIsDeletedBySender() && message.getIsDeletedByReceiver()) {
            messageRepository.delete(message);
        }
    }

    @Transactional
    public void deleteMessages(Long userId, java.util.List<Long> messageIds) {
        for (Long messageId : messageIds) {
            deleteMessage(userId, messageId);
        }
    }

    public long getUnreadMessageCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        return messageRepository.countUnreadMessagesExcludingBlocked(user, false, false, blockedUserIds);
    }

    private List<Long> getBlockedConversationUserIds(Long userId) {
        return userBlockService.getBlockedUserIdsEitherDirection(userId);
    }
}
