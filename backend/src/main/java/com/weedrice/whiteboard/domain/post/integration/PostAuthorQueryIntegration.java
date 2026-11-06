package com.weedrice.whiteboard.domain.post.integration;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.StringExpression;
import com.weedrice.whiteboard.domain.actor.AuthorPresentation;
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

    public static StringExpression userDisplayName() {
        return new CaseBuilder().when(withdrawn()).then(AuthorPresentation.WITHDRAWN_DISPLAY_NAME).otherwise(user.displayName);
    }

    public static Expression<String> userProfileImageUrl() {
        return new CaseBuilder().when(withdrawn()).then((String) null).otherwise(user.profileImageUrl);
    }

    public static StringExpression agentName() {
        return new CaseBuilder().when(withdrawn()).then(AuthorPresentation.WITHDRAWN_DISPLAY_NAME).otherwise(agent.name);
    }

    public static BooleanExpression withdrawn() {
        return user.status.eq("DELETED").or(user.deletedAt.isNotNull());
    }

    public static BooleanExpression displayAuthorContains(String keyword) {
        return userDisplayName().containsIgnoreCase(keyword)
                .or(agentName().containsIgnoreCase(keyword));
    }
}
