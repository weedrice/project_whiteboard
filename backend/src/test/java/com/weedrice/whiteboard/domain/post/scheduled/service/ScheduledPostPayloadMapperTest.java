package com.weedrice.whiteboard.domain.post.scheduled.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledPostPayloadMapperTest {

    @Test
    void detailResponseRestoresEveryEditablePayloadField() {
        User user = User.builder().email("scheduled@example.com").displayName("scheduled-user").build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        Board board = Board.builder().boardName("Scheduled Board").boardUrl("scheduled-board").build();
        ReflectionTestUtils.setField(board, "boardId", 2L);
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 20, 12, 30);
        ScheduledPost scheduledPost = ScheduledPost.builder()
                .user(user)
                .board(board)
                .categoryId(3L)
                .title("Scheduled title")
                .contents("<p>Scheduled contents</p>")
                .tagsJson("[\"spring\",\"vue\"]")
                .isNotice(true)
                .isNsfw(true)
                .isSpoiler(true)
                .isSecret(true)
                .fileIdsJson("[10,11]")
                .pollJson("{\"question\":\"Choice?\",\"options\":[\"A\",\"B\"],\"multipleChoiceEnabled\":true,\"anonymousEnabled\":false}")
                .seriesId(4L)
                .draftId(5L)
                .scheduledAt(scheduledAt)
                .build();
        ReflectionTestUtils.setField(scheduledPost, "scheduledPostId", 9L);

        var response = new ScheduledPostPayloadMapper(new ObjectMapper()).toDetailResponse(scheduledPost);

        assertThat(response.getScheduledPostId()).isEqualTo(9L);
        assertThat(response.getBoardUrl()).isEqualTo("scheduled-board");
        assertThat(response.getCategoryId()).isEqualTo(3L);
        assertThat(response.getTitle()).isEqualTo("Scheduled title");
        assertThat(response.getContents()).isEqualTo("<p>Scheduled contents</p>");
        assertThat(response.getTags()).containsExactly("spring", "vue");
        assertThat(response.isNotice()).isTrue();
        assertThat(response.isNsfw()).isTrue();
        assertThat(response.isSpoiler()).isTrue();
        assertThat(response.isSecret()).isTrue();
        assertThat(response.getFileIds()).containsExactly(10L, 11L);
        assertThat(response.getPoll().getQuestion()).isEqualTo("Choice?");
        assertThat(response.getPoll().getOptions()).containsExactly("A", "B");
        assertThat(response.getSeriesId()).isEqualTo(4L);
        assertThat(response.getDraftId()).isEqualTo(5L);
        assertThat(response.getScheduledAt()).isEqualTo(scheduledAt);

        JsonNode json = new ObjectMapper().valueToTree(response);
        assertThat(json.get("isNotice").asBoolean()).isTrue();
        assertThat(json.get("isNsfw").asBoolean()).isTrue();
        assertThat(json.get("isSpoiler").asBoolean()).isTrue();
        assertThat(json.get("isSecret").asBoolean()).isTrue();
        assertThat(json.has("notice")).isFalse();
        assertThat(json.has("nsfw")).isFalse();
        assertThat(json.has("spoiler")).isFalse();
        assertThat(json.has("secret")).isFalse();
    }
}
