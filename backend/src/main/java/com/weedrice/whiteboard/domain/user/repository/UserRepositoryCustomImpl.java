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
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class UserRepositoryCustomImpl implements UserRepositoryCustom {

    private static final String ADMIN_ROLE_ADMIN = "ADMIN";
    private static final String ADMIN_ROLE_MODERATOR = "MODERATOR";

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
        AdminSearchFilters filters = AdminSearchFilters.from(condition);

        if (StringUtils.hasText(keyword)) {
            builder.and(user.displayName.containsIgnoreCase(keyword)
                    .or(user.loginId.containsIgnoreCase(keyword))
                    .or(user.email.containsIgnoreCase(keyword)));
        }

        if (filters.empty()) {
            return builder;
        }

        if (StringUtils.hasText(filters.status())) {
            builder.and(user.status.eq(filters.status()));
        }

        if (filters.isEmailVerified() != null) {
            builder.and(user.isEmailVerified.eq(filters.isEmailVerified()));
        }

        if (filters.isSuperAdmin() != null) {
            builder.and(user.isSuperAdmin.eq(filters.isSuperAdmin()));
        }

        if (filters.isWithdrawn() != null) {
            if (Boolean.TRUE.equals(filters.isWithdrawn())) {
                builder.and(user.status.eq(User.STATUS_DELETED).or(user.deletedAt.isNotNull()));
            } else {
                builder.and(user.status.ne(User.STATUS_DELETED).and(user.deletedAt.isNull()));
            }
        }

        if (filters.createdFrom() != null) {
            builder.and(user.createdAt.goe(filters.createdFrom()));
        }

        if (filters.createdTo() != null) {
            builder.and(user.createdAt.lt(filters.createdTo()));
        }

        if (filters.lastLoginFrom() != null) {
            builder.and(user.lastLoginAt.goe(filters.lastLoginFrom()));
        }

        if (filters.lastLoginTo() != null) {
            builder.and(user.lastLoginAt.lt(filters.lastLoginTo()));
        }

        if (filters.minActivityCount() != null) {
            builder.and(totalActivityCountExpression(user).goe(filters.minActivityCount()));
        }

        applyRoleFilter(builder, user, filters.role());

        return builder;
    }

    private NativeAdminQueryParts buildNativeAdminQueryParts(String keyword, UserAdminSearchCondition condition) {
        List<String> clauses = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        AdminSearchFilters filters = AdminSearchFilters.from(condition);

        if (StringUtils.hasText(keyword)) {
            clauses.add("(lower(u.display_name) like :keyword or lower(u.login_id) like :keyword or lower(u.email) like :keyword)");
            params.put("keyword", "%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }

        if (!filters.empty()) {
            if (StringUtils.hasText(filters.status())) {
                clauses.add("u.status = :status");
                params.put("status", filters.status());
            }

            if (filters.isEmailVerified() != null) {
                clauses.add("u.is_email_verified = :isEmailVerified");
                params.put("isEmailVerified", Boolean.TRUE.equals(filters.isEmailVerified()) ? "Y" : "N");
            }

            if (filters.isSuperAdmin() != null) {
                clauses.add("u.is_super_admin = :isSuperAdmin");
                params.put("isSuperAdmin", Boolean.TRUE.equals(filters.isSuperAdmin()) ? "Y" : "N");
            }

            if (filters.isWithdrawn() != null) {
                if (Boolean.TRUE.equals(filters.isWithdrawn())) {
                    clauses.add("(u.status = 'DELETED' or u.deleted_at is not null)");
                } else {
                    clauses.add("(u.status <> 'DELETED' and u.deleted_at is null)");
                }
            }

            if (filters.createdFrom() != null) {
                clauses.add("u.created_at >= :createdFrom");
                params.put("createdFrom", filters.createdFrom());
            }

            if (filters.createdTo() != null) {
                clauses.add("u.created_at < :createdTo");
                params.put("createdTo", filters.createdTo());
            }

            if (filters.lastLoginFrom() != null) {
                clauses.add("u.last_login_at >= :lastLoginFrom");
                params.put("lastLoginFrom", filters.lastLoginFrom());
            }

            if (filters.lastLoginTo() != null) {
                clauses.add("u.last_login_at < :lastLoginTo");
                params.put("lastLoginTo", filters.lastLoginTo());
            }

            if (filters.minActivityCount() != null) {
                clauses.add("coalesce(ac.activity_count, 0) >= :minActivityCount");
                params.put("minActivityCount", filters.minActivityCount());
            }

            applyNativeRoleFilter(clauses, params, filters.role());
        }

        String whereClause = clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
        return new NativeAdminQueryParts(whereClause, params);
    }

    private void applyRoleFilter(BooleanBuilder builder, QUser user, String role) {
        if (!StringUtils.hasText(role)) {
            return;
        }
        switch (role) {
            case Role.SUPER_ADMIN:
                builder.and(user.isSuperAdmin.isTrue());
                break;
            case Role.USER:
                builder.and(user.isSuperAdmin.isFalse())
                        .and(hasActiveAdmin(user).not());
                break;
            case Role.BOARD_ADMIN:
            case ADMIN_ROLE_MODERATOR:
                builder.and(hasActiveAdminRole(user, role));
                break;
            case ADMIN_ROLE_ADMIN:
                builder.and(hasActiveAdmin(user));
                break;
            default:
                break;
        }
    }

    private void applyNativeRoleFilter(List<String> clauses, Map<String, Object> params, String role) {
        if (!StringUtils.hasText(role)) {
            return;
        }
        switch (role) {
            case Role.SUPER_ADMIN:
                clauses.add("u.is_super_admin = 'Y'");
                break;
            case Role.USER:
                clauses.add("u.is_super_admin = 'N'");
                clauses.add("not exists (select 1 from admins a where a.user_id = u.user_id and a.is_active = 'Y')");
                break;
            case Role.BOARD_ADMIN:
            case ADMIN_ROLE_MODERATOR:
                clauses.add("exists (select 1 from admins a where a.user_id = u.user_id and a.is_active = 'Y' and a.role = :role)");
                params.put("role", role);
                break;
            case ADMIN_ROLE_ADMIN:
                clauses.add("exists (select 1 from admins a where a.user_id = u.user_id and a.is_active = 'Y')");
                break;
            default:
                break;
        }
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

        for (Sort.Order sort : pageable.getSort()) {
            String direction = sort.isAscending() ? "asc" : "desc";
            AdminUserSort sortMapping = AdminUserSort.fromProperty(sort.getProperty());
            if (sortMapping == null) {
                continue;
            }
            if (sortMapping == AdminUserSort.USER_ID) {
                hasUserIdOrder = true;
            }
            orderBy.add(sortMapping.nativeColumn() + " " + direction);
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

        for (Sort.Order sort : pageable.getSort()) {
            Order direction = sort.getDirection().isAscending() ? Order.ASC : Order.DESC;
            AdminUserSort sortMapping = AdminUserSort.fromProperty(sort.getProperty());
            if (sortMapping == null) {
                continue;
            }
            switch (sortMapping) {
                case USER_ID:
                    hasUserIdOrder = true;
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.userId));
                    break;
                case CREATED_AT:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.createdAt));
                    break;
                case LAST_LOGIN_AT:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.lastLoginAt));
                    break;
                case LOGIN_ID:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.loginId));
                    break;
                case DISPLAY_NAME:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.displayName));
                    break;
                case STATUS:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.status));
                    break;
                case IS_EMAIL_VERIFIED:
                    orderSpecifiers.add(new OrderSpecifier<>(direction, user.isEmailVerified));
                    break;
                case IS_SUPER_ADMIN:
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

    private record AdminSearchFilters(
            String status,
            String role,
            Boolean isEmailVerified,
            Boolean isSuperAdmin,
            Boolean isWithdrawn,
            LocalDateTime createdFrom,
            LocalDateTime createdTo,
            LocalDateTime lastLoginFrom,
            LocalDateTime lastLoginTo,
            Long minActivityCount) {

        private static AdminSearchFilters from(UserAdminSearchCondition condition) {
            if (condition == null) {
                return new AdminSearchFilters(null, null, null, null, null, null, null, null, null, null);
            }
            return new AdminSearchFilters(
                    condition.getStatus(),
                    normalizeRole(condition.getRole()),
                    condition.getIsEmailVerified(),
                    condition.getIsSuperAdmin(),
                    condition.getIsWithdrawn(),
                    condition.getCreatedFrom(),
                    condition.getCreatedTo(),
                    condition.getLastLoginFrom(),
                    condition.getLastLoginTo(),
                    condition.getMinActivityCount());
        }

        private boolean empty() {
            return !StringUtils.hasText(status)
                    && !StringUtils.hasText(role)
                    && isEmailVerified == null
                    && isSuperAdmin == null
                    && isWithdrawn == null
                    && createdFrom == null
                    && createdTo == null
                    && lastLoginFrom == null
                    && lastLoginTo == null
                    && minActivityCount == null;
        }

        private static String normalizeRole(String role) {
            return StringUtils.hasText(role) ? role.trim().toUpperCase(Locale.ROOT) : null;
        }
    }

    private enum AdminUserSort {
        USER_ID("userId", "u.user_id"),
        CREATED_AT("createdAt", "u.created_at"),
        LAST_LOGIN_AT("lastLoginAt", "u.last_login_at"),
        LOGIN_ID("loginId", "u.login_id"),
        DISPLAY_NAME("displayName", "u.display_name"),
        STATUS("status", "u.status"),
        IS_EMAIL_VERIFIED("isEmailVerified", "u.is_email_verified"),
        IS_SUPER_ADMIN("isSuperAdmin", "u.is_super_admin");

        private final String property;
        private final String nativeColumn;

        AdminUserSort(String property, String nativeColumn) {
            this.property = property;
            this.nativeColumn = nativeColumn;
        }

        private String nativeColumn() {
            return nativeColumn;
        }

        private static AdminUserSort fromProperty(String property) {
            for (AdminUserSort sort : values()) {
                if (sort.property.equals(property)) {
                    return sort;
                }
            }
            return null;
        }
    }

    private record NativeAdminQueryParts(String whereClause, Map<String, Object> params) {
    }
}

