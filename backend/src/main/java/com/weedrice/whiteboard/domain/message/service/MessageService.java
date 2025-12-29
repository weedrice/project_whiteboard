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

@Service("messageDomainService") // Bean 이름 충돌을 피하기 위해 명시적으로 지정
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

        // 발신자가 수신자를 차단했는지 확인
        if (userBlockService.isBlocked(senderId, receiverId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_USER);
        }

        // 수신자가 발신자를 차단했는지 확인
        if (userBlockService.isBlocked(receiverId, senderId)) {
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
        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(userId);
        Page<Message> messages = messageRepository.findReceivedMessagesExcludingBlocked(user, false, blockedUserIds,
                pageable);
        return MessageResponse.from(messages, userId);
    }

    public MessageResponse getSentMessages(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(userId);
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

        // 차단 관계 확인
        if (userBlockService.isBlocked(userId, message.getSender().getUserId()) ||
                userBlockService.isBlocked(message.getSender().getUserId(), userId) ||
                userBlockService.isBlocked(userId, message.getReceiver().getUserId()) ||
                userBlockService.isBlocked(message.getReceiver().getUserId(), userId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_USER);
        }

        // 수신자가 읽었을 경우 읽음 처리
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

        // 양쪽 모두 삭제했다면 DB에서 Hard Delete
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
        List<Long> blockedUserIds = userBlockService.getBlockedUserIds(userId);
        return messageRepository.countUnreadMessagesExcludingBlocked(user, false, false, blockedUserIds);
    }
}
