package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AnonymousCacheKeyTest {

    @Test
    void normalizesEquivalentPeriods() {
        assertThat(AnonymousCacheKey.normalizePeriod(null)).isEqualTo("24h");
        assertThat(AnonymousCacheKey.normalizePeriod(" 7D ")).isEqualTo("7d");
        assertThat(AnonymousCacheKey.normalizePeriod("unsupported")).isEqualTo("24h");
    }

    @Test
    void trendingKeyUsesNormalizedPeriodAndBoundedPageSize() {
        assertThat(AnonymousCacheKey.trending(PageRequest.of(2, 1000), " 30D "))
                .isEqualTo("30d:2:100");
        assertThat(AnonymousCacheKey.trending(null, null)).isEqualTo("24h:0:20");
    }
}
