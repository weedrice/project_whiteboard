package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long>, MessageRepositoryCustom {
    Page<Message> findByReceiverAndIsDeletedByReceiver(User receiver, Boolean isDeleted, Pageable pageable);
    Page<Message> findBySenderAndIsDeletedBySender(User sender, Boolean isDeleted, Pageable pageable);
    long countByReceiverAndIsReadAndIsDeletedByReceiver(User receiver, Boolean isRead, Boolean isDeleted);
}
