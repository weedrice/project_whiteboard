package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.stream.StreamSupport;

public record BoardSubscriptionOrderRequest(
        @NotEmpty List<@NotBlank String> boardUrls) {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public BoardSubscriptionOrderRequest(JsonNode requestBody) {
        this(extractBoardUrls(requestBody));
    }

    private static List<String> extractBoardUrls(JsonNode requestBody) {
        if (requestBody == null || requestBody.isNull()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (requestBody.isArray()) {
            return toBoardUrls(requestBody);
        }

        JsonNode boardUrlsNode = requestBody.get("boardUrls");
        if (boardUrlsNode == null || !boardUrlsNode.isArray()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return toBoardUrls(boardUrlsNode);
    }

    private static List<String> toBoardUrls(JsonNode boardUrlsNode) {
        return StreamSupport.stream(boardUrlsNode.spliterator(), false)
                .map(node -> {
                    if (!node.isTextual()) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
                    }
                    return node.asText();
                })
                .toList();
    }
}
