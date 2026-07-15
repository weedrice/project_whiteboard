package com.weedrice.whiteboard.domain.board.dto;

import tools.jackson.databind.JsonNode;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.List;
import java.util.stream.StreamSupport;

final class BoardSubscriptionOrderPayloadParser {

    private BoardSubscriptionOrderPayloadParser() {
    }

    static List<String> parseBoardUrls(JsonNode requestBody) {
        if (requestBody == null || requestBody.isNull()) {
            throw invalidInput();
        }
        if (requestBody.isArray()) {
            return toBoardUrls(requestBody);
        }

        JsonNode boardUrlsNode = requestBody.get("boardUrls");
        if (boardUrlsNode == null || !boardUrlsNode.isArray()) {
            throw invalidInput();
        }
        return toBoardUrls(boardUrlsNode);
    }

    private static List<String> toBoardUrls(JsonNode boardUrlsNode) {
        return StreamSupport.stream(boardUrlsNode.spliterator(), false)
                .map(node -> {
                    if (!node.isString()) {
                        throw invalidInput();
                    }
                    return node.asString();
                })
                .toList();
    }

    private static BusinessException invalidInput() {
        return new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
    }
}
