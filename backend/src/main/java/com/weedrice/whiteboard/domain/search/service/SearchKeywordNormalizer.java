package com.weedrice.whiteboard.domain.search.service;

import java.util.Locale;

final class SearchKeywordNormalizer {

    private SearchKeywordNormalizer() {
    }

    static String normalize(String keyword) {
        String normalizedKeyword = SearchRequestNormalizer.canonicalizeKeyword(keyword)
                .toLowerCase(Locale.ROOT);
        return SearchRequestNormalizer.truncateKeyword(normalizedKeyword);
    }
}
