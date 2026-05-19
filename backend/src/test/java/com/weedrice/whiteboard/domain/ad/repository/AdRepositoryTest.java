package com.weedrice.whiteboard.domain.ad.repository;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class AdRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AdRepository adRepository;

    @Test
    @DisplayName("활성 광고 조회는 오픈엔드 광고를 포함하고 만료 광고를 제외한다")
    void findActiveIdsByPlacement_includesOpenEndedAndExcludesExpired() {
        LocalDateTime now = LocalDateTime.now();
        Ad openEndedAd = persistAd("TOP", now.minusDays(1), null, true);
        persistAd("TOP", now.minusDays(2), now.minusMinutes(1), true);
        persistAd("TOP", now.plusMinutes(1), now.plusDays(1), true);
        persistAd("TOP", now.minusDays(1), now.plusDays(1), false);

        List<Long> activeAdIds = adRepository.findActiveIdsByPlacement("TOP", now);

        assertThat(activeAdIds).containsExactly(openEndedAd.getAdId());
    }

    @Test
    @DisplayName("활성 광고 ID 조회는 adId 오름차순 후보 목록을 반환한다")
    void findActiveIdsByPlacement_returnsOrderedCandidateIds() {
        LocalDateTime now = LocalDateTime.now();
        Ad firstAd = persistAd("TOP", now.minusDays(1), null, true);
        Ad secondAd = persistAd("TOP", now.minusDays(1), null, true);

        List<Long> activeAdIds = adRepository.findActiveIdsByPlacement("TOP", now);

        assertThat(activeAdIds).containsExactly(firstAd.getAdId(), secondAd.getAdId());
    }

    @Test
    @DisplayName("활성 광고 단건 조회는 오픈엔드 광고를 포함한다")
    void findActiveById_includesOpenEndedAd() {
        LocalDateTime now = LocalDateTime.now();
        Ad ad = persistAd("TOP", now.minusDays(1), null, true);

        assertThat(adRepository.findActiveById(ad.getAdId(), now)).contains(ad);
    }

    @Test
    @DisplayName("활성 광고 ID 조회는 시작 포함과 종료 제외 경계를 적용한다")
    void findActiveIdsByPlacement_appliesStartInclusiveAndEndExclusiveBoundary() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 29, 10, 0);
        Ad startsNow = persistAd("TOP", now, now.plusHours(1), true);
        Ad endsNow = persistAd("TOP", now.minusHours(1), now, true);

        List<Long> activeAdIds = adRepository.findActiveIdsByPlacement("TOP", now);

        assertThat(activeAdIds).containsExactly(startsNow.getAdId());
        assertThat(activeAdIds).doesNotContain(endsNow.getAdId());
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
