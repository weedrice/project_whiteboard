package com.weedrice.whiteboard.domain.board.dto;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardSubscriptionOrderPayloadParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDtoObjectBody() throws Exception {
        JsonNode payload = objectMapper.readTree("{\"boardUrls\":[\"free\",\"tech\"]}");

        assertThat(BoardSubscriptionOrderPayloadParser.parseBoardUrls(payload))
                .containsExactly("free", "tech");
    }

    @Test
    void parsesLegacyArrayBody() throws Exception {
        JsonNode payload = objectMapper.readTree("[\"free\",\"tech\"]");

        assertThat(BoardSubscriptionOrderPayloadParser.parseBoardUrls(payload))
                .containsExactly("free", "tech");
    }

    @Test
    void rejectsMissingOrNonArrayBoardUrls() throws Exception {
        assertInvalid(objectMapper.readTree("{}"));
        assertInvalid(objectMapper.readTree("{\"boardUrls\":\"free\"}"));
    }

    @Test
    void rejectsNullBodyAndNonTextArrayItems() throws Exception {
        assertInvalid(null);
        assertInvalid(objectMapper.readTree("null"));
        assertInvalid(objectMapper.readTree("{\"boardUrls\":[\"free\",1]}"));
        assertInvalid(objectMapper.readTree("[\"free\",false]"));
    }

    private void assertInvalid(JsonNode payload) {
        assertThatThrownBy(() -> BoardSubscriptionOrderPayloadParser.parseBoardUrls(payload))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
    }
}
