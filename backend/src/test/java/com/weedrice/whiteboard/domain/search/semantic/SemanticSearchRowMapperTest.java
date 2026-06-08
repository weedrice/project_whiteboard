package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSearchRowMapperTest {

    @Test
    void mapsNullableColumnsAndTimestamp() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 6, 12, 30, 15);

        when(resultSet.getString("content_type")).thenReturn("POST");
        when(resultSet.getLong("content_id")).thenReturn(10L);
        when(resultSet.getLong("post_id")).thenReturn(10L);
        when(resultSet.getLong("board_id")).thenReturn(2L);
        when(resultSet.getString("board_url")).thenReturn("free");
        when(resultSet.getString("board_name")).thenReturn("Free");
        when(resultSet.getString("title")).thenReturn("Title");
        when(resultSet.getString("excerpt")).thenReturn("Excerpt");
        when(resultSet.getDouble("similarity")).thenReturn(0.0);
        when(resultSet.getString("rank_source")).thenReturn("KEYWORD_FALLBACK");
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.valueOf(createdAt));
        when(resultSet.getLong("author_user_id")).thenReturn(7L);
        when(resultSet.getLong("author_agent_id")).thenReturn(0L);
        when(resultSet.getString("author_type")).thenReturn("USER");
        when(resultSet.getString("author_display_name")).thenReturn("Author");
        when(resultSet.getString("author_profile_image_url")).thenReturn("https://example.com/profile.png");
        when(resultSet.wasNull()).thenReturn(true, false, true);

        SemanticSearchRow row = SemanticSearchRowMapper.INSTANCE.mapRow(resultSet, 0);

        assertThat(row.contentType()).isEqualTo("POST");
        assertThat(row.contentId()).isEqualTo(10L);
        assertThat(row.similarity()).isNull();
        assertThat(row.createdAt()).isEqualTo(createdAt);
        assertThat(row.authorUserId()).isEqualTo(7L);
        assertThat(row.authorAgentId()).isNull();
    }
}
