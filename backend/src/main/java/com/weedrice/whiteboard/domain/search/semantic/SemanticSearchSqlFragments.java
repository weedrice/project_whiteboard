package com.weedrice.whiteboard.domain.search.semantic;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.ArrayList;
import java.util.List;

final class SemanticSearchSqlFragments {

    private SemanticSearchSqlFragments() {
    }

    static String buildUnionSql(SemanticSearchSqlCriteria query, String postSelect, String commentSelect) {
        List<String> selects = new ArrayList<>();
        String boardPredicate = boardPredicate(query);
        String postPrivacyPredicate = postPrivacyPredicate(query);
        if (query.contentType().includesPosts()) {
            selects.add(String.format(postSelect, boardPredicate, postPrivacyPredicate));
        }
        if (query.contentType().includesComments()) {
            selects.add(String.format(commentSelect, boardPredicate, postPrivacyPredicate));
        }
        return String.join("\nUNION ALL\n", selects) + "\n";
    }

    static String boardPredicate(SemanticSearchSqlCriteria query) {
        String blockedPost = query.hasBlockedUserIds() ? " AND p.user_id NOT IN (:blockedUserIds)" : "";
        String boardUrlPredicate = query.hasBoardUrl()
                ? " AND b.board_url = :boardUrl"
                : " AND (b.is_listed = 'Y' OR b.is_listed IS NULL)";
        return indexedBoardPredicate() + boardUrlPredicate + blockedPost;
    }

    static String postPrivacyPredicate(SemanticSearchSqlCriteria query) {
        String blockedComment = query.hasBlockedUserIds() ? " AND u.user_id NOT IN (:blockedUserIds)" : "";
        return indexedPostPrivacyPredicate() + blockedComment;
    }

    static String indexedBoardPredicate() {
        return "b.is_active = 'Y' AND b.is_public = 'Y'";
    }

    static String indexedPostPrivacyPredicate() {
        return "p.is_secret = 'N'";
    }

    static String authorVisibilityPredicate(boolean includePostAuthor) {
        List<String> authorAliases = includePostAuthor ? List.of("u", "post_author") : List.of("u");
        StringBuilder sql = new StringBuilder();
        for (String alias : authorAliases) {
            sql.append("  AND ").append(alias).append(".status = 'ACTIVE'\n")
                    .append("  AND ").append(alias).append(".deleted_at IS NULL\n");
        }
        for (String alias : authorAliases) {
            sql.append("""
                      AND NOT EXISTS (
                            SELECT 1 FROM sanctions s
                            WHERE s.target_user_id = %s.user_id
                              AND UPPER(s.type) = 'BAN'
                              AND s.start_date <= CURRENT_TIMESTAMP
                              AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP)
                      )
                    """.formatted(alias));
        }
        return sql.toString();
    }

    static MapSqlParameterSource commonParams(SemanticSearchSqlCriteria query) {
        return new MapSqlParameterSource()
                .addValue("boardUrl", query.boardUrl())
                .addValue("blockedUserIds", blockedUserIdsOrSentinel(query))
                .addValue("limit", query.limit())
                .addValue("offset", query.offset());
    }

    private static List<Long> blockedUserIdsOrSentinel(SemanticSearchSqlCriteria query) {
        return query.hasBlockedUserIds() ? query.blockedUserIds() : List.of(-1L);
    }
}
