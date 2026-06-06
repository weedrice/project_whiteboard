package com.weedrice.whiteboard.domain.search.semantic;

import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

final class SemanticSearchRowMapper {
    static final RowMapper<SemanticSearchRow> INSTANCE = (rs, rowNum) -> new SemanticSearchRow(
            rs.getString("content_type"),
            rs.getLong("content_id"),
            rs.getLong("post_id"),
            rs.getLong("board_id"),
            rs.getString("board_url"),
            rs.getString("board_name"),
            rs.getString("title"),
            rs.getString("excerpt"),
            getNullableDouble(rs, "similarity"),
            rs.getString("rank_source"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            getNullableLong(rs, "author_user_id"),
            getNullableLong(rs, "author_agent_id"),
            rs.getString("author_type"),
            rs.getString("author_display_name"),
            rs.getString("author_profile_image_url"));

    private SemanticSearchRowMapper() {
    }

    private static Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Double getNullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
