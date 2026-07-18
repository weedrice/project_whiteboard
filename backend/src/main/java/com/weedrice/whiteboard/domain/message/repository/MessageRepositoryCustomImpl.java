package com.weedrice.whiteboard.domain.message.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.message.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static com.weedrice.whiteboard.domain.message.entity.QMessage.message;
import static com.weedrice.whiteboard.domain.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class MessageRepositoryCustomImpl implements MessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;
    private final EntityManager entityManager;

    private static final String CONVERSATION_VISIBLE_PREDICATE = """
            (
                (m.sender_id = :userId AND m.receiver_id = :userId
                    AND m.is_deleted_by_sender = 'N' AND m.is_deleted_by_receiver = 'N')
                OR
                (m.sender_id <> m.receiver_id AND (
                    (m.sender_id = :userId AND m.is_deleted_by_sender = 'N')
                    OR (m.receiver_id = :userId AND m.is_deleted_by_receiver = 'N')
                ))
            )
            AND (CASE WHEN m.sender_id = :userId THEN m.receiver_id ELSE m.sender_id END)
                NOT IN (:blockedUserIds)
            """;

    @Override
    public Page<Message> findReceivedMessagesExcludingBlocked(Long userId, Boolean isDeleted,
            List<Long> blockedUserIds, Pageable pageable) {
        List<Message> content = queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        message.receiver.userId.eq(userId),
                        message.isDeletedByReceiver.eq(isDeleted),
                        notBlockedSenderCondition(blockedUserIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(messageListOrder(pageable))
                .fetch();

        Long total = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.receiver.userId.eq(userId),
                        message.isDeletedByReceiver.eq(isDeleted),
                        notBlockedSenderCondition(blockedUserIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Message> findSentMessagesExcludingBlocked(Long userId, Boolean isDeleted, List<Long> blockedUserIds,
            Pageable pageable) {
        List<Message> content = queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        message.sender.userId.eq(userId),
                        message.isDeletedBySender.eq(isDeleted),
                        notBlockedReceiverCondition(blockedUserIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(messageListOrder(pageable))
                .fetch();

        Long total = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.sender.userId.eq(userId),
                        message.isDeletedBySender.eq(isDeleted),
                        notBlockedReceiverCondition(blockedUserIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Message> findConversationLatestPage(Long userId, List<Long> blockedUserIds, Pageable pageable) {
        List<Long> safeBlockedUserIds = blockedUserIds == null || blockedUserIds.isEmpty()
                ? List.of(-1L)
                : blockedUserIds;
        long total = countConversations(userId, safeBlockedUserIds);
        long offset = pageable.getOffset();
        if (offset >= total || offset > Integer.MAX_VALUE) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        String idsSql = """
                WITH ranked_conversations AS (
                    SELECT m.message_id,
                           m.created_at,
                           ROW_NUMBER() OVER (
                               PARTITION BY CASE
                                   WHEN m.sender_id = :userId THEN m.receiver_id
                                   ELSE m.sender_id
                               END
                               ORDER BY m.created_at DESC, m.message_id DESC
                           ) AS row_number
                    FROM messages m
                    WHERE %s
                )
                SELECT message_id
                FROM ranked_conversations
                WHERE row_number = 1
                ORDER BY created_at DESC, message_id DESC
                """.formatted(CONVERSATION_VISIBLE_PREDICATE);
        Query idsQuery = entityManager.createNativeQuery(idsSql);
        bindConversationParameters(idsQuery, userId, safeBlockedUserIds);
        idsQuery.setFirstResult(Math.toIntExact(offset));
        idsQuery.setMaxResults(pageable.getPageSize());
        @SuppressWarnings("unchecked")
        List<Number> rawIds = idsQuery.getResultList();
        List<Long> messageIds = rawIds.stream().map(Number::longValue).toList();

        List<Message> orderedMessages = loadMessagesInOrder(
                messageIds,
                userId,
                safeBlockedUserIds);
        return new PageImpl<>(orderedMessages, pageable, total);
    }

    private List<Message> loadMessagesInOrder(
            List<Long> messageIds,
            Long userId,
            List<Long> blockedUserIds) {
        if (messageIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Message> messagesById = queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        message.messageId.in(messageIds),
                        senderOrReceiverCanAccess(userId),
                        notBlockedConversationPartnerCondition(userId, blockedUserIds)
                )
                .fetch()
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        Message::getMessageId,
                        value -> value,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return messageIds.stream().map(messagesById::get).filter(java.util.Objects::nonNull).toList();
    }

    private long countConversations(Long userId, List<Long> blockedUserIds) {
        String countSql = """
                SELECT COUNT(DISTINCT CASE
                    WHEN m.sender_id = :userId THEN m.receiver_id
                    ELSE m.sender_id
                END)
                FROM messages m
                WHERE %s
                """.formatted(CONVERSATION_VISIBLE_PREDICATE);
        Query countQuery = entityManager.createNativeQuery(countSql);
        bindConversationParameters(countQuery, userId, blockedUserIds);
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    private void bindConversationParameters(Query query, Long userId, List<Long> blockedUserIds) {
        query.setParameter("userId", userId);
        query.setParameter("blockedUserIds", blockedUserIds);
    }

    @Override
    public Page<Message> findConversationMessages(Long userId, Long partnerId, List<Long> blockedUserIds,
            Pageable pageable) {
        BooleanExpression conversationAccess = message.sender.userId.eq(userId)
                .and(message.receiver.userId.eq(partnerId))
                .and(message.isDeletedBySender.isFalse())
                .or(message.receiver.userId.eq(userId)
                        .and(message.sender.userId.eq(partnerId))
                        .and(message.isDeletedByReceiver.isFalse()));

        List<Message> content = queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        conversationAccess,
                        notBlockedConversationPartnerCondition(userId, blockedUserIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(messageListOrder(pageable))
                .fetch();

        Long total = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        conversationAccess,
                        notBlockedConversationPartnerCondition(userId, blockedUserIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public long countUnreadMessagesExcludingBlocked(Long userId, Boolean isRead, Boolean isDeleted,
            List<Long> blockedUserIds) {
        Long count = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.receiver.userId.eq(userId),
                        message.isRead.eq(isRead),
                        message.isDeletedByReceiver.eq(isDeleted),
                        notBlockedSenderCondition(blockedUserIds)
                )
                .fetchOne();
        return count != null ? count : 0L;
    }

    @Override
    public Optional<Message> findAccessibleMessage(Long userId, Long messageId, List<Long> blockedUserIds) {
        Message result = queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        message.messageId.eq(messageId),
                        senderOrReceiverCanAccess(userId),
                        notBlockedConversationPartnerCondition(userId, blockedUserIds)
                )
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public Optional<Message> findAccessibleMessageForUpdate(Long userId, Long messageId, List<Long> blockedUserIds) {
        Message result = queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        message.messageId.eq(messageId),
                        senderOrReceiverCanAccess(userId),
                        notBlockedConversationPartnerCondition(userId, blockedUserIds)
                )
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetchOne();
        return Optional.ofNullable(result);
    }

    @Override
    public List<Message> findDeletableByMessageIdInForUpdate(Long userId, Collection<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(message)
                .join(message.sender, user).fetchJoin()
                .join(message.receiver).fetchJoin()
                .where(
                        message.messageId.in(messageIds),
                        senderOrReceiverCanAccess(userId)
                )
                .orderBy(message.messageId.asc())
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .fetch();
    }

    private BooleanExpression notBlockedSenderCondition(List<Long> blockedUserIds) {
        return (blockedUserIds != null && !blockedUserIds.isEmpty()) ? message.sender.userId.notIn(blockedUserIds) : null;
    }

    private OrderSpecifier<?>[] messageListOrder(Pageable pageable) {
        Sort.Order createdAtOrder = pageable.getSort().getOrderFor("createdAt");
        if (createdAtOrder != null && createdAtOrder.isAscending()) {
            return new OrderSpecifier<?>[] { message.createdAt.asc(), message.messageId.asc() };
        }
        return new OrderSpecifier<?>[] { message.createdAt.desc(), message.messageId.desc() };
    }

    private BooleanExpression notBlockedReceiverCondition(List<Long> blockedUserIds) {
        return (blockedUserIds != null && !blockedUserIds.isEmpty()) ? message.receiver.userId.notIn(blockedUserIds) : null;
    }

    private BooleanExpression notBlockedConversationPartnerCondition(Long userId, List<Long> blockedUserIds) {
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return null;
        }
        return message.sender.userId.eq(userId)
                .and(message.receiver.userId.notIn(blockedUserIds))
                .or(message.receiver.userId.eq(userId)
                        .and(message.sender.userId.notIn(blockedUserIds)));
    }

    private BooleanExpression senderOrReceiverCanAccess(Long userId) {
        BooleanExpression selfMessage = message.sender.userId.eq(userId)
                .and(message.receiver.userId.eq(userId));
        BooleanExpression selfMessageAccess = selfMessage
                .and(message.isDeletedBySender.isFalse())
                .and(message.isDeletedByReceiver.isFalse());
        BooleanExpression regularMessageAccess = selfMessage.not().and(
                message.sender.userId.eq(userId).and(message.isDeletedBySender.isFalse())
                        .or(message.receiver.userId.eq(userId).and(message.isDeletedByReceiver.isFalse())));
        return selfMessageAccess.or(regularMessageAccess);
    }
}
