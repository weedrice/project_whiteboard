package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.dto.MessageResponse;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service("messageDomainService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageService {

    private static final int MESSAGE_DELETE_FETCH_CHUNK_SIZE = 500;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 20;
    private static final Sort MESSAGE_LIST_SORT = Sort.by(Sort.Order.desc("createdAt"));
    private static final Set<String> ALLOWED_MESSAGE_SORTS = Set.of("createdAt");

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final SanctionService sanctionService;

    @Transactional
    public Message sendMessage(Long senderId, Long receiverId, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(sender);
        sanctionService.validateNotMuted(sender);
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
        Pageable safePageable = normalizeMessagePageable(pageable);
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        Page<Message> messages = messageRepository.findReceivedMessagesExcludingBlocked(user, false, blockedUserIds,
                safePageable);
        return MessageResponse.from(messages, userId);
    }

    public MessageResponse getSentMessages(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable safePageable = normalizeMessagePageable(pageable);
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        Page<Message> messages = messageRepository.findSentMessagesExcludingBlocked(user, false, blockedUserIds,
                safePageable);
        return MessageResponse.from(messages, userId);
    }

    public Message getMessage(Long userId, Long messageId) {
        return getAccessibleMessage(userId, messageId);
    }

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
    public void markAsRead(Long userId, Long messageId) {
        Message message = getAccessibleMessage(userId, messageId);
        if (!message.getReceiver().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        message.markAsRead();
    }

    @Transactional
    public void deleteMessage(Long userId, Long messageId) {
        Message message = messageRepository.findByMessageIdForUpdate(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        deleteLoadedMessage(userId, message);
        deleteIfFullyDeleted(message);
    }

    @Transactional
    public void deleteMessages(Long userId, List<Long> messageIds) {
        List<Long> requestedMessageIds = messageIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requestedMessageIds.isEmpty()) {
            return;
        }

        Map<Long, Message> messagesById = findMessagesByIdsInChunks(requestedMessageIds.stream()
                .sorted()
                .toList());

        List<Message> messagesToDelete = new ArrayList<>();
        for (Long messageId : requestedMessageIds) {
            Message message = messagesById.get(messageId);
            if (message == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            deleteLoadedMessage(userId, message);
            if (isFullyDeleted(message)) {
                messagesToDelete.add(message);
            }
        }

        if (!messagesToDelete.isEmpty()) {
            messageRepository.deleteAll(messagesToDelete);
        }
    }

    private void deleteLoadedMessage(Long userId, Message message) {
        boolean selfMessage = message.getSender().getUserId().equals(userId)
                && message.getReceiver().getUserId().equals(userId);

        if (selfMessage) {
            message.deleteBySender();
            message.deleteByReceiver();
        } else if (message.getSender().getUserId().equals(userId)) {
            message.deleteBySender();
        } else if (message.getReceiver().getUserId().equals(userId)) {
            message.deleteByReceiver();
        } else {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void deleteIfFullyDeleted(Message message) {
        if (isFullyDeleted(message)) {
            messageRepository.delete(message);
        }
    }

    private boolean isFullyDeleted(Message message) {
        return message.getIsDeletedBySender() && message.getIsDeletedByReceiver();
    }

    private Map<Long, Message> findMessagesByIdsInChunks(List<Long> messageIds) {
        Map<Long, Message> messagesById = new HashMap<>();
        for (int start = 0; start < messageIds.size(); start += MESSAGE_DELETE_FETCH_CHUNK_SIZE) {
            int end = Math.min(start + MESSAGE_DELETE_FETCH_CHUNK_SIZE, messageIds.size());
            messageRepository.findByMessageIdInForUpdate(messageIds.subList(start, end)).stream()
                    .collect(Collectors.toMap(Message::getMessageId, message -> message))
                    .forEach(messagesById::put);
        }
        return messagesById;
    }

    public long getUnreadMessageCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        return messageRepository.countUnreadMessagesExcludingBlocked(user, false, false, blockedUserIds);
    }

    private Message getAccessibleMessage(Long userId, Long messageId) {
        Message message = messageRepository.findAccessibleMessage(userId, messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        Long partnerUserId = message.getSender().getUserId().equals(userId)
                ? message.getReceiver().getUserId()
                : message.getSender().getUserId();
        if (userBlockService.isEitherDirectionBlocked(userId, partnerUserId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_USER);
        }
        return message;
    }

    private List<Long> getBlockedConversationUserIds(Long userId) {
        return userBlockService.getBlockedUserIdsEitherDirection(userId);
    }

    private Pageable normalizeMessagePageable(Pageable pageable) {
        return PageRequestUtils.of(
                pageable,
                DEFAULT_MESSAGE_PAGE_SIZE,
                MESSAGE_LIST_SORT,
                ALLOWED_MESSAGE_SORTS);
    }
}
