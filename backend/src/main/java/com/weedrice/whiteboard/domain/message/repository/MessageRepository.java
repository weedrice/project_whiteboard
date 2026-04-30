package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long>, MessageRepositoryCustom {
    Page<Message> findByReceiverAndIsDeletedByReceiver(User receiver, Boolean isDeleted, Pageable pageable);
    Page<Message> findBySenderAndIsDeletedBySender(User sender, Boolean isDeleted, Pageable pageable);
    long countByReceiverAndIsReadAndIsDeletedByReceiver(User receiver, Boolean isRead, Boolean isDeleted);

    @EntityGraph(attributePaths = {"sender", "receiver"})
    List<Message> findByMessageIdIn(Collection<Long> messageIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"sender", "receiver"})
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.messageId = :messageId
              and (
                (
                  m.sender.userId = :userId
                  and m.receiver.userId = :userId
                  and m.isDeletedBySender = false
                  and m.isDeletedByReceiver = false
                )
                or (
                  m.sender.userId = :userId
                  and m.receiver.userId <> :userId
                  and m.isDeletedBySender = false
                )
                or (
                  m.receiver.userId = :userId
                  and m.sender.userId <> :userId
                  and m.isDeletedByReceiver = false
                )
              )
            """)
    Optional<Message> findDeletableByMessageIdForUpdate(
            @Param("userId") Long userId,
            @Param("messageId") Long messageId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"sender", "receiver"})
    @Query("""
            SELECT m
            FROM Message m
            WHERE m.messageId in :messageIds
              and (
                (
                  m.sender.userId = :userId
                  and m.receiver.userId = :userId
                  and m.isDeletedBySender = false
                  and m.isDeletedByReceiver = false
                )
                or (
                  m.sender.userId = :userId
                  and m.receiver.userId <> :userId
                  and m.isDeletedBySender = false
                )
                or (
                  m.receiver.userId = :userId
                  and m.sender.userId <> :userId
                  and m.isDeletedByReceiver = false
                )
              )
            ORDER BY m.messageId asc
            """)
    List<Message> findDeletableByMessageIdInForUpdate(
            @Param("userId") Long userId,
            @Param("messageIds") Collection<Long> messageIds);
}
