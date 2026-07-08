package com.weedrice.whiteboard.domain.search.service;

import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

public final class SearchRequestNormalizer {

    static final int MAX_KEYWORD_LENGTH = 255;

    private static final int MAX_POPULAR_KEYWORD_LIMIT = 100;
    private static final int DEFAULT_POST_SEARCH_PAGE_SIZE = 20;
    private static final Sort DEFAULT_POST_SEARCH_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("postId"));
    private static final int DEFAULT_RECENT_SEARCH_PAGE_SIZE = 20;
    private static final Sort DEFAULT_RECENT_SEARCH_SORT = Sort.by(
            Sort.Order.desc("searchedAt"),
            Sort.Order.desc("logId"));
    private static final Set<String> ALLOWED_POST_SEARCH_SORTS = Set.of(
            "createdAt", "postId", "viewCount", "likeCount");

    private SearchRequestNormalizer() {
    }

    public static String canonicalizeKeyword(String keyword) {
        return canonicalizeKeyword(keyword, true);
    }

    public static String canonicalizeOptionalKeyword(String keyword) {
        return canonicalizeKeyword(keyword, false);
    }

    public static String canonicalizeOptionalBoardUrl(String boardUrl) {
        if (boardUrl == null) {
            return null;
        }
        if (boardUrl.isBlank()) {
            return null;
        }
        return BoardUrlNormalizer.normalizeLookup(boardUrl);
    }

    public static String canonicalizeOptionalAuthor(String author) {
        return canonicalizeKeyword(author, false);
    }

    public static String normalizeSearchPeriod(String period) {
        if (period == null || period.isBlank()) {
            return null;
        }
        String normalizedPeriod = period.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedPeriod) {
            case "TODAY", "WEEK", "MONTH", "CUSTOM" -> normalizedPeriod;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

    public static LocalDate parseOptionalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private static String canonicalizeKeyword(String keyword, boolean required) {
        if (keyword == null) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return null;
        }
        String canonicalKeyword = keyword.trim();
        if (canonicalKeyword.isEmpty()) {
            if (required) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            return null;
        }
        return truncateKeyword(canonicalKeyword);
    }

    static String truncateKeyword(String keyword) {
        if (keyword == null || keyword.length() <= MAX_KEYWORD_LENGTH) {
            return keyword;
        }
        int endIndex = MAX_KEYWORD_LENGTH;
        if (Character.isHighSurrogate(keyword.charAt(endIndex - 1))) {
            endIndex--;
        }
        return keyword.substring(0, endIndex);
    }

    public static int normalizePopularKeywordLimit(int limit) {
        if (limit < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return Math.min(limit, MAX_POPULAR_KEYWORD_LIMIT);
    }

    public static String normalizePopularKeywordPeriod(String period) {
        if (period == null || period.isBlank()) {
            return "DAILY";
        }
        String normalizedPeriod = period.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedPeriod) {
            case "DAILY", "WEEKLY", "MONTHLY" -> normalizedPeriod;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        };
    }

    public static Pageable normalizePostSearchPageable(int page, int size, Sort sort) {
        return PageRequestUtils.of(
                page,
                size,
                sort,
                DEFAULT_POST_SEARCH_SORT,
                ALLOWED_POST_SEARCH_SORTS);
    }

    public static Pageable normalizePostSearchPageable(Pageable pageable) {
        return PageRequestUtils.of(
                pageable,
                DEFAULT_POST_SEARCH_PAGE_SIZE,
                DEFAULT_POST_SEARCH_SORT,
                ALLOWED_POST_SEARCH_SORTS);
    }

    public static Pageable normalizeRecentSearchPageable(Pageable pageable) {
        return PageRequestUtils.of(pageable, DEFAULT_RECENT_SEARCH_PAGE_SIZE, DEFAULT_RECENT_SEARCH_SORT);
    }
}
