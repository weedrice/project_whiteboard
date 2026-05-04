package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 4, 29, 10, 0);

    @Mock
    private AdRepository adRepository;
    @Mock
    private AdClickLogRepository adClickLogRepository;
    @Mock
    private UserRepository userRepository;

    private AdService adService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-04-29T01:00:00Z"), DateTimeUtils.KST_ZONE_ID);
        adService = new AdService(adRepository, adClickLogRepository, userRepository, clock);
    }

    @Test
    @DisplayName("광고 조회는 impression count 를 증가시키지 않는다")
    void getAd_success() {
        String placement = "HEADER";
        Ad ad = buildActiveAd(placement, FIXED_NOW.plusDays(1));
        when(adRepository.countActiveByPlacement(eq(placement), eq(FIXED_NOW))).thenReturn(1L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(Collections.singletonList(ad));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(ad);
        assertThat(ad.getImpressionCount()).isZero();
        verify(adRepository).countActiveByPlacement(placement, FIXED_NOW);
        Pageable pageable = captureAdCandidatePageable();
        assertThat(pageable.getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("오픈엔드 광고도 활성 광고 조회에 포함한다")
    void getAd_includesOpenEndedAd() {
        String placement = "HEADER";
        Ad ad = buildActiveAd(placement, null);
        when(adRepository.countActiveByPlacement(eq(placement), eq(FIXED_NOW))).thenReturn(1L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(Collections.singletonList(ad));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(ad);
        verify(adRepository).countActiveByPlacement(placement, FIXED_NOW);
        Pageable pageable = captureAdCandidatePageable();
        assertThat(pageable.getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 광고가 없으면 후보 조회 없이 null을 반환한다")
    void getAd_noActiveAds_returnsNull() {
        when(adRepository.countActiveByPlacement("HEADER", FIXED_NOW)).thenReturn(0L);

        Ad result = adService.getAd("HEADER");

        assertThat(result).isNull();
        verify(adRepository).countActiveByPlacement("HEADER", FIXED_NOW);
        verify(adRepository, never()).findActiveByPlacement(any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("랜덤 offset 후보가 사라지면 첫 후보를 다시 조회한다")
    void getAd_fallsBackToFirstCandidateWhenRandomOffsetWindowIsEmpty() {
        String placement = "HEADER";
        Ad ad = buildActiveAd(placement, null);
        when(adRepository.countActiveByPlacement(eq(placement), eq(FIXED_NOW))).thenReturn(2L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.singletonList(ad));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(ad);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adRepository, times(2))
                .findActiveByPlacement(eq(placement), eq(FIXED_NOW), pageableCaptor.capture());
        assertThat(pageableCaptor.getAllValues()).extracting(Pageable::getPageSize).containsOnly(1);
        assertThat(pageableCaptor.getAllValues().get(1).getPageNumber()).isZero();
    }

    @Test
    @DisplayName("impression 기록은 활성 광고에만 반영된다")
    void recordAdImpression_success() {
        when(adRepository.incrementImpressionCountForActive(1L, FIXED_NOW)).thenReturn(1);

        adService.recordAdImpression(1L);

        verify(adRepository).incrementImpressionCountForActive(1L, FIXED_NOW);
    }

    @Test
    @DisplayName("비활성 또는 만료 광고의 impression 은 기록하지 않는다")
    void recordAdImpression_inactiveAd_throwsAdNotFound() {
        when(adRepository.incrementImpressionCountForActive(1L, FIXED_NOW)).thenReturn(0);

        assertThatThrownBy(() -> adService.recordAdImpression(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_FOUND));
    }

    @Test
    @DisplayName("click 기록은 활성 광고일 때만 저장하고 targetUrl 을 반환한다")
    void recordAdClick_success() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(1);

        String targetUrl = adService.recordAdClick(1L, null, "127.0.0.1");

        assertThat(targetUrl).isEqualTo("https://example.com");
        verify(adRepository).findActiveById(1L, FIXED_NOW);
        verify(adRepository).incrementClickCountForActive(1L, FIXED_NOW);
        verify(adClickLogRepository).save(any());
    }

    @Test
    @DisplayName("click count 증가가 실패하면 로그를 저장하지 않고 AD_NOT_FOUND 를 반환한다")
    void recordAdClick_incrementFailure_throwsAdNotFound() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(0);

        assertThatThrownBy(() -> adService.recordAdClick(1L, null, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_FOUND));

        verify(adClickLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("비활성 또는 만료 광고는 click 기록 대상이 아니다")
    void recordAdClick_inactiveAd_throwsAdNotFound() {
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adService.recordAdClick(1L, null, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_FOUND));

        verify(adRepository, never()).incrementClickCountForActive(any(), any());
        verify(adClickLogRepository, never()).save(any());
    }

    private Ad buildActiveAd(String placement, LocalDateTime endDate) {
        return Ad.builder()
                .adName("Header Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement(placement)
                .targetUrl("https://example.com")
                .startDate(FIXED_NOW.minusDays(1))
                .endDate(endDate)
                .build();
    }

    private Pageable captureAdCandidatePageable() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adRepository).findActiveByPlacement(any(), eq(FIXED_NOW), pageableCaptor.capture());
        return pageableCaptor.getValue();
    }
}
