package com.weedrice.whiteboard.domain.actor.query;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;

import java.time.LocalDateTime;

/**
 * Author eligibility for public content discovery, applied before ordering/pagination.
 * Personal blocks and content/board access remain separate predicates. This is not
 * a write permission check or a policy for direct reads and moderation lookups.
 *
 * Withdrawal preserves existing content. Suspended/unknown accounts and effective BANs stay hidden.
 * MUTE, future BANs and BANs ending at the query time do not hide existing content.
 */
public final class ContentAuthorVisibility {

    private ContentAuthorVisibility() {
    }

    /** A JPQL subquery usable with any author's user ID, including in @Query constants. */
    public static final String VISIBLE_AUTHOR_IDS_JPQL = """
            SELECT visibleAuthor.userId
            FROM User visibleAuthor
            WHERE (visibleAuthor.status = 'DELETED'
                   OR (visibleAuthor.status = 'ACTIVE' AND visibleAuthor.deletedAt IS NULL))
              AND NOT EXISTS (
                    SELECT 1 FROM Sanction authorBan
                    WHERE authorBan.targetUser = visibleAuthor
                      AND UPPER(authorBan.type) = 'BAN'
                      AND authorBan.startDate <= CURRENT_TIMESTAMP
                      AND (authorBan.endDate IS NULL OR authorBan.endDate > CURRENT_TIMESTAMP)
              )
            """;

    private static final String RETAINED_ACCOUNT_SQL_PREDICATE = """
                      AND (visible_author.status = 'DELETED'
                           OR (visible_author.status = 'ACTIVE' AND visible_author.deleted_at IS NULL))
                    """;

    private static final String VISIBLE_AUTHOR_SQL_PREDICATE = RETAINED_ACCOUNT_SQL_PREDICATE + """
                      AND NOT EXISTS (
                            SELECT 1 FROM sanctions s
                            WHERE s.target_user_id = visible_author.user_id
                              AND UPPER(s.type) = 'BAN'
                              AND s.start_date <= CURRENT_TIMESTAMP
                              AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
                      )
                    """;

    /** Compile-time native subquery for repository @Query annotations. */
    public static final String VISIBLE_AUTHOR_IDS_SQL =
            "SELECT visible_author.user_id FROM users visible_author WHERE 1 = 1"
                    + VISIBLE_AUTHOR_SQL_PREDICATE;

    /** Reuses the JPQL policy without adding domain entity dependencies to callers. */
    public static BooleanExpression querydsl(NumberExpression<Long> authorUserId) {
        return Expressions.booleanTemplate("{0} IN (" + VISIBLE_AUTHOR_IDS_JPQL + ")", authorUserId);
    }

    /**
     * Native SQL predicates for joined users aliases (for comments, pass both the
     * comment author and the parent post author). Aliases must be source constants.
     */
    public static String sql(String... authorAliases) {
        return forAliases(VISIBLE_AUTHOR_SQL_PREDICATE, authorAliases);
    }

    /** Account eligibility for retaining/indexing content; not authentication or BAN eligibility. */
    public static boolean retainsContent(String status, LocalDateTime deletedAt) {
        return "DELETED".equals(status) || ("ACTIVE".equals(status) && deletedAt == null);
    }

    /** Indexing keeps BAN-independent vectors so expiry does not require re-embedding. */
    public static String retainedAccountSql(String... authorAliases) {
        return forAliases(RETAINED_ACCOUNT_SQL_PREDICATE, authorAliases);
    }

    private static String forAliases(String predicate, String... authorAliases) {
        StringBuilder sql = new StringBuilder();
        for (String alias : authorAliases) {
            if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Expected a SQL author alias");
            }
            sql.append(predicate.replace("visible_author", alias));
        }
        return sql.toString();
    }
}
