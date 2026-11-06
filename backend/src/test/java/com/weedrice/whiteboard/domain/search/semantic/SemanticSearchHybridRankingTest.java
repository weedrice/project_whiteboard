package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticSearchHybridRankingTest {
    @Test
    void exactUnindexedTitleSurvivesFusionAndTypedIdsDoNotCollide() {
        SemanticSearchRow vectorPost = row("POST", 1, "other", "VECTOR", 0.8);
        SemanticSearchRow vectorComment = row("COMMENT", 1, "other", "VECTOR", 0.9);
        SemanticSearchRow keywordOverlap = row("POST", 1, "other", "KEYWORD_FALLBACK", null);
        SemanticSearchRow unindexed = row("POST", 2, "Needle", "KEYWORD_FALLBACK", null);
        var result = SemanticSearchHybridRanking.merge(List.of(vectorComment, vectorPost),
                List.of(unindexed, keywordOverlap), "needle", 20, 20, 0);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.results()).extracting(row -> row.contentType() + ":" + row.contentId())
                .containsExactly("POST:2", "POST:1", "COMMENT:1");
        assertThat(result.results().get(1).rankSource()).isEqualTo("VECTOR");
        assertThat(result.results().get(1).similarity()).isEqualTo(0.8);
    }

    @Test
    void fixedPoolProducesConsistentTotalsAcrossPagesAndOutOfRangeRequests() {
        var vectors = List.of(row("POST", 1, "other", "VECTOR", 0.9),
                row("POST", 2, "other", "VECTOR", 0.8));
        var keywords = List.of(row("POST", 3, "needle", "KEYWORD_FALLBACK", null));
        var first = SemanticSearchHybridRanking.merge(vectors, keywords, "needle", 2, 1, 0);
        var second = SemanticSearchHybridRanking.merge(vectors, keywords, "needle", 2, 1, 1);
        var pastEnd = SemanticSearchHybridRanking.merge(vectors, keywords, "needle", 2, 1, Long.MAX_VALUE);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(second.totalElements()).isEqualTo(2);
        assertThat(pastEnd.totalElements()).isEqualTo(2);
        assertThat(first.results()).extracting(SemanticSearchRow::contentId).containsExactly(3L);
        assertThat(second.results()).extracting(SemanticSearchRow::contentId).containsExactly(1L);
        assertThat(pastEnd.results()).isEmpty();
    }

    @Test
    void tiedRanksAndTimestampsUseContentTypeBeforeId() {
        var result = SemanticSearchHybridRanking.merge(List.of(row("POST", 1, "other", "VECTOR", 0.9)),
                List.of(row("COMMENT", 1, "other", "KEYWORD_FALLBACK", null)), "needle", 20, 20, 0);
        assertThat(result.results()).extracting(SemanticSearchRow::contentType).containsExactly("COMMENT", "POST");
    }

    @Test
    void emptyAndKeywordOnlyPoolsHaveTheSameBoundedPageContract() {
        assertThat(SemanticSearchHybridRanking.page(List.of(), 20, 10, 0).totalElements()).isZero();
        var rows = List.of(row("POST", 1, "one", "KEYWORD_FALLBACK", null),
                row("POST", 2, "two", "KEYWORD_FALLBACK", null), row("POST", 3, "three", "KEYWORD_FALLBACK", null));
        var result = SemanticSearchHybridRanking.page(rows, 2, 10, 1);
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.results()).extracting(SemanticSearchRow::contentId).containsExactly(2L);
    }

    static SemanticSearchRow row(String type, long id, String title, String source, Double similarity) {
        return new SemanticSearchRow(type, id, id, 1L, "board", "Board", title, "body", similarity, source,
                LocalDateTime.of(2026, 1, 1, 0, 0), 1L, null, "USER", "Author", null);
    }
}
