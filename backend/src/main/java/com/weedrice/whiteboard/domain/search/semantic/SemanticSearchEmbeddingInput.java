package com.weedrice.whiteboard.domain.search.semantic;

/** Conservative plain-text limit for the fixed text-embedding-3-small byte-pair tokenizer. */
final class SemanticSearchEmbeddingInput {
    // Each ordinary token represents at least one UTF-8 byte; this stays below 8,192 tokens.
    // This is a byte upper bound, not an exact token count: multilingual inputs may be shortened sooner.
    private static final int MAX_UTF8_BYTES = 8_000;

    private SemanticSearchEmbeddingInput() {
    }

    static String limit(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int bytes = 0;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int width = codePoint <= 0x7F ? 1 : codePoint <= 0x7FF ? 2 : codePoint <= 0xFFFF ? 3 : 4;
            if (bytes + width > MAX_UTF8_BYTES) {
                break;
            }
            bytes += width;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }
}
