package com.weedrice.whiteboard.domain.user.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.user.entity.QUser;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchUsers(String keyword, Pageable pageable) {
        QUser user = QUser.user;

        BooleanExpression predicate = null;
        if (keyword != null && !keyword.trim().isEmpty()) {
            predicate = user.displayName.containsIgnoreCase(keyword)
                    .or(user.loginId.containsIgnoreCase(keyword))
                    .or(user.email.containsIgnoreCase(keyword));
        }

        List<User> content = queryFactory
                .selectFrom(user)
                .where(predicate)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(user.userId.desc())
                .fetch();

        long total = queryFactory
                .select(user.count())
                .from(user)
                .where(predicate)
                .fetchOne();

        return new PageImpl<>(content, pageable, total);
    }
}
