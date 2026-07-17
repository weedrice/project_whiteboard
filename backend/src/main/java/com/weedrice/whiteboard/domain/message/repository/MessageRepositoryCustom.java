package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepositoryCustom {
    Page<Message> findReceivedMessagesExcludingBlocked(Long userId, Boolean isDeleted, List<Long> blockedUserIds,
            Pageable pageable);
    Page<Message> findSentMessagesExcludingBlocked(Long userId, Boolean isDeleted, List<Long> blockedUserIds,
            Pageable pageable);
    Page<Message> findConversationLatestPage(Long userId, List<Long> blockedUserIds, Pageable pageable);
    Page<Message> findConversationMessages(Long userId, Long partnerId, List<Long> blockedUserIds, Pageable pageable);
    long countUnreadMessagesExcludingBlocked(Long userId, Boolean isRead, Boolean isDeleted, List<Long> blockedUserIds);
    Optional<Message> findAccessibleMessage(Long userId, Long messageId, List<Long> blockedUserIds);
    List<Message> findDeletableByMessageIdInForUpdate(Long userId, Collection<Long> messageIds);
}
