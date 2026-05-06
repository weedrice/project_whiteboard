package com.weedrice.whiteboard.domain.search.service;

import java.util.Locale;

final class SearchKeywordNormalizer {

    private SearchKeywordNormalizer() {
    }

    static String normalize(String keyword) {
        return keyword.trim().toLowerCase(Locale.ROOT);
    }
}
