package com.weedrice.whiteboard.domain.search.semantic;

import java.util.Locale;

final class SemanticSearchVectorFormatter {
    private SemanticSearchVectorFormatter() {
    }

    static String format(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.8f", embedding[i]));
        }
        return builder.append(']').toString();
    }
}
