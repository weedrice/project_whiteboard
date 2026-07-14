package com.weedrice.whiteboard.domain.board.util;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.global.common.util.TextInputNormalizer;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.List;
import java.util.regex.Pattern;

public final class BoardUrlNormalizer {

    public static final int MAX_BOARD_URL_LENGTH = 100;
    public static final String BOARD_URL_PATTERN = "^[a-z0-9_-]+$";

    private static final Pattern BOARD_URL_PATTERN_MATCHER = Pattern.compile(BOARD_URL_PATTERN);

    private BoardUrlNormalizer() {
    }

    public static String normalizeLookup(String boardUrl) {
        String normalizedBoardUrl = TextInputNormalizer.normalizeNullable(boardUrl);
        if (!isValidBoardUrl(normalizedBoardUrl)) {
            throw new BusinessException(ErrorCode.BOARD_NOT_FOUND);
        }
        return normalizedBoardUrl;
    }

    public static String normalizeCreatable(String boardUrl) {
        String normalizedBoardUrl = normalizeWritable(boardUrl);
        if (BoardPolicyConstants.INQUIRY_BOARD_URL.equalsIgnoreCase(normalizedBoardUrl)) {
            throw BusinessException.withMessageKey(ErrorCode.VALIDATION_ERROR, "validation.board.url.reserved");
        }
        return normalizedBoardUrl;
    }

    public static String normalizeOptionalCreatable(String boardUrl) {
        if (boardUrl == null) {
            return null;
        }
        return normalizeCreatable(boardUrl);
    }

    public static List<String> normalizeOrderList(List<String> boardUrls) {
        if (boardUrls == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return boardUrls.stream()
                .map(BoardUrlNormalizer::normalizeWritable)
                .toList();
    }

    private static String normalizeWritable(String boardUrl) {
        String normalizedBoardUrl = TextInputNormalizer.normalizeNullable(boardUrl);
        if (!isValidBoardUrl(normalizedBoardUrl)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedBoardUrl;
    }

    private static boolean isValidBoardUrl(String boardUrl) {
        return boardUrl != null
                && !boardUrl.isBlank()
                && boardUrl.length() <= MAX_BOARD_URL_LENGTH
                && BOARD_URL_PATTERN_MATCHER.matcher(boardUrl).matches();
    }
}
