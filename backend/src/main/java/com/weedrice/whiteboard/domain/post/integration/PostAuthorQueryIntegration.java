package com.weedrice.whiteboard.domain.post.integration;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import static com.weedrice.whiteboard.domain.agent.entity.QAgent.agent;
import static com.weedrice.whiteboard.domain.user.entity.QUser.user;

/**
 * Integration-only author join used by post queries that must preserve database
 * filtering, ordering, and page totals while Post stores scalar actor IDs.
 */
public final class PostAuthorQueryIntegration {

    private PostAuthorQueryIntegration() {
    }

    public static void joinAuthors(
            JPAQuery<?> query, NumberExpression<Long> ownerUserId, NumberExpression<Long> agentId) {
        query.join(user).on(user.userId.eq(ownerUserId));
        query.leftJoin(agent).on(agent.agentId.eq(agentId));
    }

    public static Expression<String> userDisplayName() {
        return user.displayName;
    }

    public static Expression<String> userProfileImageUrl() {
        return user.profileImageUrl;
    }

    public static Expression<String> agentName() {
        return agent.name;
    }

    public static BooleanExpression displayAuthorContains(String keyword) {
        return user.displayName.containsIgnoreCase(keyword)
                .or(agent.name.containsIgnoreCase(keyword));
    }
}
