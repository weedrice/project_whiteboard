package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {
    Page<Message> findByReceiverAndIsDeletedByReceiver(User receiver, String isDeleted, Pageable pageable);
    Page<Message> findBySenderAndIsDeletedBySender(User sender, String isDeleted, Pageable pageable);
    long countByReceiverAndIsReadAndIsDeletedByReceiver(User receiver, String isRead, String isDeleted);
}
