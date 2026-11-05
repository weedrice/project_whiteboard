package com.weedrice.whiteboard.domain.search.semantic;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fuses bounded, already-authorized candidates before calculating both the page and its total. */
final class SemanticSearchHybridRanking {
    private static final double RANK_OFFSET = 60.0;
    private static final Comparator<SemanticSearchRow> STABLE_ORDER = Comparator
            .comparing(SemanticSearchRow::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(SemanticSearchRow::contentType)
            .thenComparing(SemanticSearchRow::contentId, Comparator.reverseOrder());

    private SemanticSearchHybridRanking() {
    }

    static SemanticSearchQueryRows merge(List<SemanticSearchRow> vectors, List<SemanticSearchRow> keywords,
                                         String keyword, int candidateLimit, int limit, long offset) {
        Map<ContentKey, RankedCandidate> candidates = new HashMap<>();
        add(candidates, keywords, false);
        add(candidates, vectors, true);
        List<SemanticSearchRow> ranked = candidates.values().stream()
                .sorted(Comparator.<RankedCandidate, Boolean>comparing(candidate -> exactTitle(candidate.row, keyword))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble((RankedCandidate candidate) -> candidate.score).reversed())
                        .thenComparing(candidate -> candidate.row, STABLE_ORDER))
                .limit(candidateLimit)
                .map(candidate -> candidate.row)
                .toList();
        return page(ranked, candidateLimit, limit, offset);
    }

    static SemanticSearchQueryRows page(List<SemanticSearchRow> candidates, int candidateLimit, int limit, long offset) {
        int total = Math.min(candidates.size(), candidateLimit);
        if (offset >= total) {
            return new SemanticSearchQueryRows(List.of(), total);
        }
        int start = Math.toIntExact(offset);
        int end = (int) Math.min((long) total, offset + limit);
        return new SemanticSearchQueryRows(List.copyOf(candidates.subList(start, end)), total);
    }

    private static boolean exactTitle(SemanticSearchRow row, String keyword) {
        return "POST".equals(row.contentType()) && row.title() != null && row.title().equalsIgnoreCase(keyword);
    }

    private static void add(Map<ContentKey, RankedCandidate> candidates, List<SemanticSearchRow> rows, boolean vector) {
        for (int index = 0; index < rows.size(); index++) {
            SemanticSearchRow row = rows.get(index);
            ContentKey key = new ContentKey(row.contentType(), row.contentId());
            RankedCandidate candidate = candidates.computeIfAbsent(key, ignored -> new RankedCandidate(row));
            candidate.score += 1.0 / (RANK_OFFSET + index + 1);
            // Keep the measured similarity and existing VECTOR source for overlapping candidates.
            if (vector) {
                candidate.row = row;
            }
        }
    }

    private record ContentKey(String type, Long id) {
    }

    private static final class RankedCandidate {
        private SemanticSearchRow row;
        private double score;

        private RankedCandidate(SemanticSearchRow row) {
            this.row = row;
        }
    }
}
