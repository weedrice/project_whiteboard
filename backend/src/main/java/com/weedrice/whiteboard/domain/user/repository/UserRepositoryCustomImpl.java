package com.weedrice.whiteboard.domain.user.repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weedrice.whiteboard.domain.admin.entity.QAdmin;
import com.weedrice.whiteboard.domain.comment.entity.QComment;
import com.weedrice.whiteboard.domain.post.entity.QPost;
import com.weedrice.whiteboard.domain.user.dto.UserAdminSearchCondition;
import com.weedrice.whiteboard.domain.user.entity.QUser;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<User> searchUsers(String keyword, Pageable pageable) {
        return searchUsersForAdmin(keyword, UserAdminSearchCondition.builder().build(), pageable);
    }

    @Override
    public Page<User> searchUsersForAdmin(String keyword, UserAdminSearchCondition condition, Pageable pageable) {
        QUser user = QUser.user;
        BooleanBuilder predicate = buildPredicate(user, keyword, condition);

        List<User> content = queryFactory
                .selectFrom(user)
                .where(predicate)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(getOrderSpecifiers(pageable, user))
                .fetch();

        Long total = queryFactory
                .select(user.count())
                .from(user)
                .where(predicate)
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    private BooleanBuilder buildPredicate(QUser user, String keyword, UserAdminSearchCondition condition) {
        BooleanBuilder builder = new BooleanBuilder();

        if (StringUtils.hasText(keyword)) {
            builder.and(user.displayName.containsIgnoreCase(keyword)
                    .or(user.loginId.containsIgnoreCase(keyword))
                    .or(user.email.containsIgnoreCase(keyword)));
        }

        if (condition == null) {
            return builder;
        }

        if (StringUtils.hasText(condition.getStatus())) {
            builder.and(user.status.eq(condition.getStatus()));
        }

        if (condition.getIsEmailVerified() != null) {
            builder.and(user.isEmailVerified.eq(condition.getIsEmailVerified()));
        }

        if (condition.getIsSuperAdmin() != null) {
            builder.and(user.isSuperAdmin.eq(condition.getIsSuperAdmin()));
        }

        if (condition.getIsWithdrawn() != null) {
            if (Boolean.TRUE.equals(condition.getIsWithdrawn())) {
                builder.and(user.status.eq("DELETED").or(user.deletedAt.isNotNull()));
            } else {
                builder.and(user.status.ne("DELETED").and(user.deletedAt.isNull()));
            }
        }

        if (condition.getCreatedFrom() != null) {
            builder.and(user.createdAt.goe(condition.getCreatedFrom()));
        }

        if (condition.getCreatedTo() != null) {
            builder.and(user.createdAt.lt(condition.getCreatedTo()));
        }

        if (condition.getLastLoginFrom() != null) {
            builder.and(user.lastLoginAt.goe(condition.getLastLoginFrom()));
        }

        if (condition.getLastLoginTo() != null) {
            builder.and(user.lastLoginAt.lt(condition.getLastLoginTo()));
        }

        if (condition.getMinActivityCount() != null) {
            builder.and(totalActivityCountExpression(user).goe(condition.getMinActivityCount()));
        }

        if (StringUtils.hasText(condition.getRole())) {
            String role = condition.getRole().trim().toUpperCase(Locale.ROOT);
            switch (role) {
                case "SUPER_ADMIN":
                    builder.and(user.isSuperAdmin.isTrue());
                    break;
                case "USER":
                    builder.and(user.isSuperAdmin.isFalse())
                            .and(hasActiveAdmin(user).not());
                    break;
                case "BOARD_ADMIN":
                case "MODERATOR":
                    builder.and(hasActiveAdminRole(user, role));
                    break;
                case "ADMIN":
                    builder.and(hasActiveAdmin(user));
                    break;
                default:
                    break;
            }
        }

        return builder;
    }

    private NumberExpression<Long> totalActivityCountExpression(QUser user) {
        QPost post = QPost.post;
        QComment comment = QComment.comment;

        Expression<Long> postCount = JPAExpressions
                .select(post.count())
                .from(post)
                .where(post.user.eq(user), post.isDeleted.eq(false));

        Expression<Long> commentCount = JPAExpressions
                .select(comment.count())
                .from(comment)
                .where(comment.user.eq(user), comment.isDeleted.eq(false));

        return Expressions.numberTemplate(Long.class, "({0} + {1})", postCount, commentCount);
    }

    private BooleanExpression hasActiveAdmin(QUser user) {
        QAdmin adminSub = new QAdmin("adminSub");
        return JPAExpressions
                .selectOne()
                .from(adminSub)
                .where(
                        adminSub.user.eq(user),
                        adminSub.isActive.eq(true))
                .exists();
    }

    private BooleanExpression hasActiveAdminRole(QUser user, String role) {
        QAdmin adminSub = new QAdmin("adminRoleSub");
        return JPAExpressions
                .selectOne()
                .from(adminSub)
                .where(
                        adminSub.user.eq(user),
                        adminSub.isActive.eq(true),
                        adminSub.role.eq(role))
                .exists();
    }

    private OrderSpecifier<?>[] getOrderSpecifiers(Pageable pageable, QUser user) {
        if (pageable.getSort().isEmpty()) {
            return new OrderSpecifier[]{new OrderSpecifier<>(Order.DESC, user.userId)};
        }

        List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
        boolean hasUserIdOrder = false;

        for (org.springframework.data.domain.Sort.Order sort : pageable.getSort()) {
            Order direction = sort.getDirection().isAscending() ? Order.ASC : Order.DESC;
            switch (sort.getProperty()) {
                case "userId":
                    hasUserIdOrder = true;
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.userId));
                    break;
                case "createdAt":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.createdAt));
                    break;
                case "lastLoginAt":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.lastLoginAt));
                    break;
                case "loginId":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.loginId));
                    break;
                case "displayName":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.displayName));
                    break;
                case "status":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.status));
                    break;
                case "isEmailVerified":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.isEmailVerified));
                    break;
                case "isSuperAdmin":
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.isSuperAdmin));
                    break;
                default:
                    break;
            }
        }

        if (orderSpecifiers.isEmpty()) {
            orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, user.userId));
        } else if (!hasUserIdOrder) {
            orderSpecifiers.add(new OrderSpecifier<>(Order.DESC, user.userId));
        }

        return orderSpecifiers.toArray(new OrderSpecifier[0]);
    }
}

