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

    static MapSqlParameterSource commonParams(SemanticSearchSqlCriteria query) {
        return new MapSqlParameterSource()
                .addValue("boardUrl", query.boardUrl())
                .addValue("viewerUserId", query.viewerUserId())
                .addValue("viewerSuperAdmin", query.viewerSuperAdmin())
                .addValue("blockedUserIds", blockedUserIdsOrSentinel(query))
                .addValue("limit", query.limit())
                .addValue("offset", query.offset());
    }

    private static List<Long> blockedUserIdsOrSentinel(SemanticSearchSqlCriteria query) {
        return query.hasBlockedUserIds() ? query.blockedUserIds() : List.of(-1L);
    }
}
