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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Page<User> searchUsers(String keyword, Pageable pageable) {
        return searchUsersForAdmin(keyword, UserAdminSearchCondition.builder().build(), pageable);
    }

    @Override
    public Page<User> searchUsersVisibleTo(String keyword, List<Long> blockedUserIds, Pageable pageable) {
        QUser user = QUser.user;
        BooleanBuilder predicate = new BooleanBuilder();

        predicate.and(user.status.eq("ACTIVE"));
        predicate.and(user.deletedAt.isNull());

        if (StringUtils.hasText(keyword)) {
            predicate.and(user.displayName.containsIgnoreCase(keyword));
        }

        if (blockedUserIds != null && !blockedUserIds.isEmpty()) {
            predicate.and(user.userId.notIn(blockedUserIds));
        }

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

    @Override
    public Page<User> searchUsersForAdmin(String keyword, UserAdminSearchCondition condition, Pageable pageable) {
        if (condition != null && condition.getMinActivityCount() != null) {
            return searchUsersForAdminByActivity(keyword, condition, pageable);
        }

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

    private Page<User> searchUsersForAdminByActivity(String keyword, UserAdminSearchCondition condition, Pageable pageable) {
        NativeAdminQueryParts queryParts = buildNativeAdminQueryParts(keyword, condition);
        long total = fetchNativeCount(queryParts);
        if (total == 0L) {
            return new PageImpl<>(List.of(), pageable, 0L);
        }

        List<Long> userIds = fetchNativeUserIds(queryParts, pageable);
        if (userIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        QUser user = QUser.user;
        Map<Long, User> usersById = queryFactory.selectFrom(user)
                .where(user.userId.in(userIds))
                .fetch()
                .stream()
                .collect(Collectors.toMap(User::getUserId, fetchedUser -> fetchedUser, (left, right) -> left, LinkedHashMap::new));

        List<User> content = userIds.stream()
                .map(usersById::get)
                .filter(Objects::nonNull)
                .toList();

        return new PageImpl<>(content, pageable, total);
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

    private NativeAdminQueryParts buildNativeAdminQueryParts(String keyword, UserAdminSearchCondition condition) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();

        if (StringUtils.hasText(keyword)) {
            clauses.add("(lower(u.display_name) like :keyword or lower(u.login_id) like :keyword or lower(u.email) like :keyword)");
            params.put("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }

        if (condition != null) {
            if (StringUtils.hasText(condition.getStatus())) {
                clauses.add("u.status = :status");
                params.put("status", condition.getStatus());
            }

            if (condition.getIsEmailVerified() != null) {
                clauses.add("u.is_email_verified = :isEmailVerified");
                params.put("isEmailVerified", Boolean.TRUE.equals(condition.getIsEmailVerified()) ? "Y" : "N");
            }

            if (condition.getIsSuperAdmin() != null) {
                clauses.add("u.is_super_admin = :isSuperAdmin");
                params.put("isSuperAdmin", Boolean.TRUE.equals(condition.getIsSuperAdmin()) ? "Y" : "N");
            }

            if (condition.getIsWithdrawn() != null) {
                if (Boolean.TRUE.equals(condition.getIsWithdrawn())) {
                    clauses.add("(u.status = 'DELETED' or u.deleted_at is not null)");
                } else {
                    clauses.add("(u.status <> 'DELETED' and u.deleted_at is null)");
                }
            }

            if (condition.getCreatedFrom() != null) {
                clauses.add("u.created_at >= :createdFrom");
                params.put("createdFrom", condition.getCreatedFrom());
            }

            if (condition.getCreatedTo() != null) {
                clauses.add("u.created_at < :createdTo");
                params.put("createdTo", condition.getCreatedTo());
            }

            if (condition.getLastLoginFrom() != null) {
                clauses.add("u.last_login_at >= :lastLoginFrom");
                params.put("lastLoginFrom", condition.getLastLoginFrom());
            }

            if (condition.getLastLoginTo() != null) {
                clauses.add("u.last_login_at < :lastLoginTo");
                params.put("lastLoginTo", condition.getLastLoginTo());
            }

            if (condition.getMinActivityCount() != null) {
                clauses.add("coalesce(ac.activity_count, 0) >= :minActivityCount");
                params.put("minActivityCount", condition.getMinActivityCount());
            }

            if (StringUtils.hasText(condition.getRole())) {
                String role = condition.getRole().trim().toUpperCase(Locale.ROOT);
                switch (role) {
                    case "SUPER_ADMIN":
                        clauses.add("u.is_super_admin = 'Y'");
                        break;
                    case "USER":
                        clauses.add("u.is_super_admin = 'N'");
                        clauses.add("not exists (select 1 from admins a where a.user_id = u.user_id and a.is_active = 'Y')");
                        break;
                    case "BOARD_ADMIN":
                    case "MODERATOR":
                        clauses.add("exists (select 1 from admins a where a.user_id = u.user_id and a.is_active = 'Y' and a.role = :role)");
                        params.put("role", role);
                        break;
                    case "ADMIN":
                        clauses.add("exists (select 1 from admins a where a.user_id = u.user_id and a.is_active = 'Y')");
                        break;
                    default:
                        break;
                }
            }
        }

        String whereClause = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
        return new NativeAdminQueryParts(whereClause, params);
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

    private long fetchNativeCount(NativeAdminQueryParts queryParts) {
        Query countQuery = entityManager.createNativeQuery(activityCountCte()
                + " select count(*) "
                + activityCountFromClause()
                + queryParts.whereClause());
        applyParameters(countQuery, queryParts.params());
        return ((Number) countQuery.getSingleResult()).longValue();
    }

    private List<Long> fetchNativeUserIds(NativeAdminQueryParts queryParts, Pageable pageable) {
        Query contentQuery = entityManager.createNativeQuery(activityCountCte()
                + " select u.user_id "
                + activityCountFromClause()
                + queryParts.whereClause()
                + buildActivityOrderBy(pageable));
        applyParameters(contentQuery, queryParts.params());
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Number> rows = contentQuery.getResultList();
        return rows.stream()
                .map(Number::longValue)
                .toList();
    }

    private void applyParameters(Query query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private String activityCountCte() {
        return """
                with activity_counts as (
                    select activity.user_id, sum(activity.activity_count) as activity_count
                    from (
                        select p.user_id, count(*) as activity_count
                        from posts p
                        where p.is_deleted = 'N'
                        group by p.user_id
                        union all
                        select c.user_id, count(*) as activity_count
                        from comments c
                        where c.is_deleted = 'N'
                        group by c.user_id
                    ) activity
                    group by activity.user_id
                )
                """;
    }

    private String activityCountFromClause() {
        return """
                 from users u
                 left join activity_counts ac on ac.user_id = u.user_id
                """;
    }

    private String buildActivityOrderBy(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return " order by u.user_id desc";
        }

        List<String> orderBy = new ArrayList<>();
        boolean hasUserIdOrder = false;

        for (org.springframework.data.domain.Sort.Order sort : pageable.getSort()) {
            String direction = sort.isAscending() ? "asc" : "desc";
            String column = switch (sort.getProperty()) {
                case "userId" -> "u.user_id";
                case "createdAt" -> "u.created_at";
                case "lastLoginAt" -> "u.last_login_at";
                case "loginId" -> "u.login_id";
                case "displayName" -> "u.display_name";
                case "status" -> "u.status";
                case "isEmailVerified" -> "u.is_email_verified";
                case "isSuperAdmin" -> "u.is_super_admin";
                default -> null;
            };
            if (column == null) {
                continue;
            }
            if ("userId".equals(sort.getProperty())) {
                hasUserIdOrder = true;
            }
            orderBy.add(column + " " + direction);
        }

        if (orderBy.isEmpty()) {
            orderBy.add("u.user_id desc");
        } else if (!hasUserIdOrder) {
            orderBy.add("u.user_id desc");
        }

        return " order by " + String.join(", ", orderBy);
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

    private record NativeAdminQueryParts(String whereClause, Map<String, Object> params) {
    }
}

