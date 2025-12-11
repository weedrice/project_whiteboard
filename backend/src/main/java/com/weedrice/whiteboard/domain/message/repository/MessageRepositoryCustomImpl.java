package com.weedrice.whiteboard.domain.message.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;

import static com.weedrice.whiteboard.domain.message.entity.QMessage.message;
import static com.weedrice.whiteboard.domain.user.entity.QUser.user;

@Repository
@RequiredArgsConstructor
public class MessageRepositoryCustomImpl implements MessageRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Message> findReceivedMessagesExcludingBlocked(User currentUser, Boolean isDeleted, List<Long> blockedUserIds, Pageable pageable) {
        List<Message> content = queryFactory
                .selectFrom(message)
                .where(
                        message.receiver.eq(currentUser),
                        message.isDeletedByReceiver.eq(isDeleted),
                        notBlockedSenderCondition(blockedUserIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(message.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.receiver.eq(currentUser),
                        message.isDeletedByReceiver.eq(isDeleted),
                        notBlockedSenderCondition(blockedUserIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public Page<Message> findSentMessagesExcludingBlocked(User currentUser, Boolean isDeleted, List<Long> blockedUserIds, Pageable pageable) {
        List<Message> content = queryFactory
                .selectFrom(message)
                .where(
                        message.sender.eq(currentUser),
                        message.isDeletedBySender.eq(isDeleted),
                        notBlockedReceiverCondition(blockedUserIds)
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(message.createdAt.desc())
                .fetch();

        Long total = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.sender.eq(currentUser),
                        message.isDeletedBySender.eq(isDeleted),
                        notBlockedReceiverCondition(blockedUserIds)
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    @Override
    public long countUnreadMessagesExcludingBlocked(User currentUser, Boolean isRead, Boolean isDeleted, List<Long> blockedUserIds) {
        Long count = queryFactory
                .select(message.count())
                .from(message)
                .where(
                        message.receiver.eq(currentUser),
                        message.isRead.eq(isRead),
                        message.isDeletedByReceiver.eq(isDeleted),
                        notBlockedSenderCondition(blockedUserIds)
                )
                .fetchOne();
        return count != null ? count : 0L;
    }

    private BooleanExpression notBlockedSenderCondition(List<Long> blockedUserIds) {
        return (blockedUserIds != null && !blockedUserIds.isEmpty()) ? message.sender.userId.notIn(blockedUserIds) : null;
    }

    private BooleanExpression notBlockedReceiverCondition(List<Long> blockedUserIds) {
        return (blockedUserIds != null && !blockedUserIds.isEmpty()) ? message.receiver.userId.notIn(blockedUserIds) : null;
    }
}
