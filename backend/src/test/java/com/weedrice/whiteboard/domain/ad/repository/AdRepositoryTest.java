package com.weedrice.whiteboard.domain.ad.repository;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class AdRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AdRepository adRepository;

    @Test
    @DisplayName("활성 광고 count는 오픈엔드 광고를 포함하고 만료 광고를 제외한다")
    void countActiveByPlacement_includesOpenEndedAndExcludesExpired() {
        LocalDateTime now = LocalDateTime.now();
        persistAd("TOP", now.minusDays(1), null, true);
        persistAd("TOP", now.minusDays(2), now.minusMinutes(1), true);
        persistAd("TOP", now.plusMinutes(1), now.plusDays(1), true);
        persistAd("TOP", now.minusDays(1), now.plusDays(1), false);

        long activeAdCount = adRepository.countActiveByPlacement("TOP", now);

        assertThat(activeAdCount).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 광고 후보 조회는 adId 오름차순 후보 목록을 반환한다")
    void findActiveByPlacement_returnsOrderedCandidates() {
        LocalDateTime now = LocalDateTime.now();
        Ad firstAd = persistAd("TOP", now.minusDays(1), null, true);
        Ad secondAd = persistAd("TOP", now.minusDays(1), null, true);

        var activeAds = adRepository.findActiveByPlacement("TOP", now, PageRequest.of(0, 2));

        assertThat(activeAds).containsExactly(firstAd, secondAd);
    }

    @Test
    @DisplayName("활성 광고 후보 조회는 pageable offset을 적용한다")
    void findActiveByPlacement_appliesPageableOffset() {
        LocalDateTime now = LocalDateTime.now();
        persistAd("TOP", now.minusDays(1), null, true);
        Ad secondAd = persistAd("TOP", now.minusDays(1), null, true);
        persistAd("TOP", now.minusDays(1), null, true);

        var activeAds = adRepository.findActiveByPlacement("TOP", now, PageRequest.of(1, 1));

        assertThat(activeAds).containsExactly(secondAd);
    }

    @Test
    @DisplayName("활성 광고 단건 조회는 오픈엔드 광고를 포함한다")
    void findActiveById_includesOpenEndedAd() {
        LocalDateTime now = LocalDateTime.now();
        Ad ad = persistAd("TOP", now.minusDays(1), null, true);

        assertThat(adRepository.findActiveById(ad.getAdId(), now)).contains(ad);
    }

    @Test
    @DisplayName("활성 광고 후보 조회는 시작 포함과 종료 제외 경계를 적용한다")
    void activePlacementQueries_applyStartInclusiveAndEndExclusiveBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 29, 10, 0);
        persistAd("TOP", now, now.plusHours(1), true);
        persistAd("TOP", now.minusHours(1), now, true);

        long activeAdCount = adRepository.countActiveByPlacement("TOP", now);
        var activeAds = adRepository.findActiveByPlacement("TOP", now, PageRequest.of(0, 1));

        assertThat(activeAdCount).isEqualTo(1);
        assertThat(activeAds).extracting(Ad::getStartDate).containsExactly(now);
    }

    @Test
    @DisplayName("활성 광고 impression 집계는 만료 광고를 제외한다")
    void incrementImpressionCountForActive_updatesOnlyActiveAds() {
        LocalDateTime now = LocalDateTime.now();
        Ad activeAd = persistAd("TOP", now.minusDays(1), now.plusDays(1), true);
        Ad expiredAd = persistAd("TOP", now.minusDays(2), now.minusMinutes(1), true);

        int activeUpdated = adRepository.incrementImpressionCountForActive(activeAd.getAdId(), now);
        int expiredUpdated = adRepository.incrementImpressionCountForActive(expiredAd.getAdId(), now);
        entityManager.clear();

        assertThat(activeUpdated).isEqualTo(1);
        assertThat(expiredUpdated).isZero();
        assertThat(adRepository.findById(activeAd.getAdId())).get().extracting(Ad::getImpressionCount).isEqualTo(1);
        assertThat(adRepository.findById(expiredAd.getAdId())).get().extracting(Ad::getImpressionCount).isEqualTo(0);
    }

    @Test
    @DisplayName("활성 광고 click 집계는 비활성 광고를 제외한다")
    void incrementClickCountForActive_updatesOnlyActiveAds() {
        LocalDateTime now = LocalDateTime.now();
        Ad activeAd = persistAd("TOP", now.minusDays(1), now.plusDays(1), true);
        Ad inactiveAd = persistAd("TOP", now.minusDays(1), now.plusDays(1), false);

        int activeUpdated = adRepository.incrementClickCountForActive(activeAd.getAdId(), now);
        int inactiveUpdated = adRepository.incrementClickCountForActive(inactiveAd.getAdId(), now);
        entityManager.clear();

        assertThat(activeUpdated).isEqualTo(1);
        assertThat(inactiveUpdated).isZero();
        assertThat(adRepository.findById(activeAd.getAdId())).get().extracting(Ad::getClickCount).isEqualTo(1);
        assertThat(adRepository.findById(inactiveAd.getAdId())).get().extracting(Ad::getClickCount).isEqualTo(0);
    }

    private Ad persistAd(String placement, LocalDateTime startDate, LocalDateTime endDate, boolean isActive) {
        Ad ad = Ad.builder()
                .adName("Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement(placement)
                .targetUrl("https://example.com")
                .startDate(startDate)
                .endDate(endDate)
                .build();
        ReflectionTestUtils.setField(ad, "isActive", isActive);
        return entityManager.persistAndFlush(ad);
    }
}
