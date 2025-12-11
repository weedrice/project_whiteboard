package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessageRepositoryCustom {
    Page<Message> findReceivedMessagesExcludingBlocked(User user, Boolean isDeleted, List<Long> blockedUserIds, Pageable pageable);
    Page<Message> findSentMessagesExcludingBlocked(User user, Boolean isDeleted, List<Long> blockedUserIds, Pageable pageable);
    long countUnreadMessagesExcludingBlocked(User user, Boolean isRead, Boolean isDeleted, List<Long> blockedUserIds);
}
