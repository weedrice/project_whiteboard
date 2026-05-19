package com.weedrice.whiteboard.domain.message.service;

import com.weedrice.whiteboard.domain.message.constant.MessageConstraints;
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
import com.weedrice.whiteboard.global.util.InputSanitizer;
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
    private static final int MAX_BULK_DELETE_MESSAGE_COUNT = 500;
    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 20;
    private static final Sort MESSAGE_LIST_SORT = Sort.by(Sort.Order.desc("createdAt"));
    private static final Set<String> ALLOWED_MESSAGE_SORTS = Set.of("createdAt");

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockService userBlockService;
    private final SanctionService sanctionService;

    @Transactional
    public Long sendMessage(Long senderId, Long receiverId, String content) {
        String sanitizedContent = InputSanitizer.stripHtml(content);
        if (sanitizedContent == null || sanitizedContent.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (sanitizedContent.length() > MessageConstraints.MAX_CONTENT_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(sender);
        sanctionService.validateNotMuted(sender);
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (userBlockService.isEitherDirectionBlocked(senderId, receiverId)) {
            throw new BusinessException(ErrorCode.BLOCKED_BY_USER);
        }
        if (!receiver.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(sanitizedContent)
                .build();
        return messageRepository.save(message).getMessageId();
    }

    public MessageResponse getReceivedMessages(Long userId, Pageable pageable) {
        return getMessages(userId, pageable, MessageListDirection.RECEIVED);
    }

    public MessageResponse getSentMessages(Long userId, Pageable pageable) {
        return getMessages(userId, pageable, MessageListDirection.SENT);
    }

    private MessageResponse getMessages(Long userId, Pageable pageable, MessageListDirection direction) {
        Pageable safePageable = normalizeMessagePageable(pageable);
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        Page<Message> messages = direction.findMessages(messageRepository, userId, blockedUserIds, safePageable);
        return MessageResponse.from(messages, userId);
    }

    private enum MessageListDirection {
        RECEIVED {
            @Override
            Page<Message> findMessages(MessageRepository messageRepository, Long userId, List<Long> blockedUserIds,
                    Pageable pageable) {
                return messageRepository.findReceivedMessagesExcludingBlocked(userId, false, blockedUserIds, pageable);
            }
        },
        SENT {
            @Override
            Page<Message> findMessages(MessageRepository messageRepository, Long userId, List<Long> blockedUserIds,
                    Pageable pageable) {
                return messageRepository.findSentMessagesExcludingBlocked(userId, false, blockedUserIds, pageable);
            }
        };

        abstract Page<Message> findMessages(MessageRepository messageRepository, Long userId, List<Long> blockedUserIds,
                Pageable pageable);
    }

    public Message getMessage(Long userId, Long messageId) {
        return getAccessibleMessage(userId, messageId);
    }

    public MessageResponse.MessageSummary getMessageSummary(Long userId, Long messageId) {
        Message message = getMessage(userId, messageId);
        return MessageResponse.MessageSummary.from(message, userId);
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
        Message message = messageRepository.findDeletableByMessageIdForUpdate(userId, messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        deleteLoadedMessage(userId, message);
        deleteIfFullyDeleted(message);
    }

    @Transactional
    public void deleteMessages(Long userId, List<Long> messageIds) {
        if (messageIds == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        List<Long> requestedMessageIds = messageIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (requestedMessageIds.isEmpty()) {
            return;
        }
        if (requestedMessageIds.size() > MAX_BULK_DELETE_MESSAGE_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, Message> messagesById = findDeletableMessagesByIdsInChunks(userId, requestedMessageIds.stream()
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
        resolveDeleteDirection(userId, message).delete(message);
    }

    private void deleteIfFullyDeleted(Message message) {
        if (isFullyDeleted(message)) {
            messageRepository.delete(message);
        }
    }

    private boolean isFullyDeleted(Message message) {
        return message.getIsDeletedBySender() && message.getIsDeletedByReceiver();
    }

    private MessageDeleteDirection resolveDeleteDirection(Long userId, Message message) {
        boolean sender = message.getSender().getUserId().equals(userId);
        boolean receiver = message.getReceiver().getUserId().equals(userId);

        if (sender && receiver) {
            return MessageDeleteDirection.BOTH;
        }
        if (sender) {
            return MessageDeleteDirection.SENDER;
        }
        if (receiver) {
            return MessageDeleteDirection.RECEIVER;
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private enum MessageDeleteDirection {
        SENDER {
            @Override
            void delete(Message message) {
                message.deleteBySender();
            }
        },
        RECEIVER {
            @Override
            void delete(Message message) {
                message.deleteByReceiver();
            }
        },
        BOTH {
            @Override
            void delete(Message message) {
                message.deleteBySender();
                message.deleteByReceiver();
            }
        };

        abstract void delete(Message message);
    }

    private Map<Long, Message> findDeletableMessagesByIdsInChunks(Long userId, List<Long> messageIds) {
        Map<Long, Message> messagesById = new HashMap<>();
        for (int start = 0; start < messageIds.size(); start += MESSAGE_DELETE_FETCH_CHUNK_SIZE) {
            int end = Math.min(start + MESSAGE_DELETE_FETCH_CHUNK_SIZE, messageIds.size());
            messageRepository.findDeletableByMessageIdInForUpdate(userId, messageIds.subList(start, end)).stream()
                    .collect(Collectors.toMap(Message::getMessageId, message -> message))
                    .forEach(messagesById::put);
        }
        return messagesById;
    }

    public long getUnreadMessageCount(Long userId) {
        List<Long> blockedUserIds = getBlockedConversationUserIds(userId);
        return messageRepository.countUnreadMessagesExcludingBlocked(userId, false, false, blockedUserIds);
    }

    private Message getAccessibleMessage(Long userId, Long messageId) {
        List<Long> blockedUserIds = getBlockedConversationUserIdsForExistingUser(userId);
        return messageRepository.findAccessibleMessage(userId, messageId, blockedUserIds)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private List<Long> getBlockedConversationUserIdsForExistingUser(Long userId) {
        return userBlockService.getBlockedUserIdsEitherDirectionForExistingUser(userId);
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
