package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BoardSubscriptionOrderRequest(
        @NotEmpty
        @Size(max = 500)
        List<@NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z0-9_-]+$") String> boardUrls) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public BoardSubscriptionOrderRequest(JsonNode requestBody) {
        this(BoardSubscriptionOrderPayloadParser.parseBoardUrls(requestBody));
    }
}
