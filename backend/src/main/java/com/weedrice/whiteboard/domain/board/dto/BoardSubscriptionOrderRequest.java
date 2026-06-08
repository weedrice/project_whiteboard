package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BoardSubscriptionOrderRequest(
        @NotEmpty List<@NotBlank String> boardUrls) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public BoardSubscriptionOrderRequest(JsonNode requestBody) {
        this(BoardSubscriptionOrderPayloadParser.parseBoardUrls(requestBody));
    }
}
