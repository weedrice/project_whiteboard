package com.weedrice.whiteboard.domain.message.service;

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

    public Page<Message> getReceivedMessages(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return messageRepository.findByReceiverAndIsDeletedByReceiver(user, "N", pageable);
    }

    public Page<Message> getSentMessages(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return messageRepository.findBySenderAndIsDeletedBySender(user, "N", pageable);
    }

    @Transactional
    public Message getMessage(Long userId, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (!message.getReceiver().getUserId().equals(userId) && !message.getSender().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 수신자가 읽었을 경우 읽음 처리
        if (message.getReceiver().getUserId().equals(userId)) {
            message.markAsRead();
        }

        return message;
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
        if ("Y".equals(message.getIsDeletedBySender()) && "Y".equals(message.getIsDeletedByReceiver())) {
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
        return messageRepository.countByReceiverAndIsReadAndIsDeletedByReceiver(user, "N", "N");
    }
}
