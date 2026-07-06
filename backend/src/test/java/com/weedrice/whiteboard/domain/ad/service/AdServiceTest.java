package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.entity.AdClickLog;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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
import java.util.List;
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
    @DisplayName("광고 조회는 impression count를 증가시키지 않는다")
    void getAd_success() {
        String placement = "HEADER";
        Ad ad = buildActiveAd(placement, FIXED_NOW.plusDays(1));
        when(adRepository.countActiveByPlacement(placement, FIXED_NOW)).thenReturn(1L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(List.of(ad));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(ad);
        assertThat(ad.getImpressionCount()).isZero();
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(adRepository).findActiveByPlacement(eq(placement), eq(FIXED_NOW), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("오픈엔드 광고도 활성 광고 조회에 포함한다")
    void getAd_includesOpenEndedAd() {
        String placement = "HEADER";
        Ad ad = buildActiveAd(placement, null);
        when(adRepository.countActiveByPlacement(placement, FIXED_NOW)).thenReturn(1L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(List.of(ad));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(ad);
        verify(adRepository).countActiveByPlacement(placement, FIXED_NOW);
        verify(adRepository).findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class));
    }

    @Test
    @DisplayName("활성 광고가 없으면 후보 조회 없이 null을 반환한다")
    void getAd_noActiveAds_returnsNull() {
        when(adRepository.countActiveByPlacement("HEADER", FIXED_NOW)).thenReturn(0L);

        Ad result = adService.getAd("HEADER");

        assertThat(result).isNull();
        verify(adRepository).countActiveByPlacement("HEADER", FIXED_NOW);
        verify(adRepository, never()).findActiveByPlacement(any(), any(), any());
        verify(adRepository, never()).findActiveById(any(), any());
    }

    @Test
    @DisplayName("count 이후 후보가 사라지면 활성 후보 수를 다시 조회한다")
    void getAd_returnsNullWhenPagedCandidateIsNoLongerActive() {
        String placement = "HEADER";
        when(adRepository.countActiveByPlacement(placement, FIXED_NOW))
                .thenReturn(1L)
                .thenReturn(0L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(List.of());

        Ad result = adService.getAd(placement);

        assertThat(result).isNull();
        verify(adRepository, times(2)).countActiveByPlacement(placement, FIXED_NOW);
        verify(adRepository).findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class));
    }

    @Test
    @DisplayName("count 이후 후보가 사라져도 남은 활성 후보를 다시 조회한다")
    void getAd_reloadsActiveCandidateWhenPagedCandidateIsNoLongerActive() {
        String placement = "HEADER";
        Ad refreshedAd = buildActiveAd(placement, null);
        when(adRepository.countActiveByPlacement(placement, FIXED_NOW))
                .thenReturn(2L)
                .thenReturn(1L);
        when(adRepository.findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(refreshedAd));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(refreshedAd);
        verify(adRepository, times(2)).countActiveByPlacement(placement, FIXED_NOW);
        verify(adRepository, times(2)).findActiveByPlacement(eq(placement), eq(FIXED_NOW), any(Pageable.class));
    }

    @Test
    @DisplayName("impression 기록은 활성 광고에만 반영한다")
    void recordAdImpression_success() {
        when(adRepository.incrementImpressionCountForActive(1L, FIXED_NOW)).thenReturn(1);

        adService.recordAdImpression(1L);

        verify(adRepository).incrementImpressionCountForActive(1L, FIXED_NOW);
    }

    @Test
    @DisplayName("비활성 또는 만료 광고에는 impression을 기록하지 않는다")
    void recordAdImpression_inactiveAd_throwsAdNotFound() {
        when(adRepository.incrementImpressionCountForActive(1L, FIXED_NOW)).thenReturn(0);

        assertThatThrownBy(() -> adService.recordAdImpression(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_FOUND));
    }

    @Test
    void getActiveAdTargetUrl_returnsActiveAdTargetUrl() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));

        assertThat(adService.getActiveAdTargetUrl(1L)).isEqualTo("https://example.com");
    }

    @Test
    void getActiveAdTargetUrl_inactiveAd_throwsAdNotFound() {
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adService.getActiveAdTargetUrl(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_FOUND));
    }

    @Test
    @DisplayName("click 기록은 활성 광고에 대해서만 저장하고 targetUrl을 반환한다")
    void recordAdClick_success() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(1);

        String targetUrl = adService.recordAdClick(1L, null, " 203.0.113.10, 10.0.0.1 ");

        assertThat(targetUrl).isEqualTo("https://example.com");
        verify(adRepository).findActiveById(1L, FIXED_NOW);
        verify(adRepository).incrementClickCountForActive(1L, FIXED_NOW);
        ArgumentCaptor<AdClickLog> clickLogCaptor = ArgumentCaptor.forClass(AdClickLog.class);
        verify(adClickLogRepository).save(clickLogCaptor.capture());
        assertThat(clickLogCaptor.getValue().getClickedAt()).isEqualTo(FIXED_NOW);
        assertThat(clickLogCaptor.getValue().getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(clickLogCaptor.getValue().getUser()).isNull();
        assertThat(clickLogCaptor.getValue().getAnonymousReason())
                .isEqualTo(AdClickLog.ANONYMOUS_REASON_ANONYMOUS_REQUEST);
    }

    @Test
    @DisplayName("click 기록은 활성 인증 사용자를 로그에 연결한다")
    void recordAdClick_activeUser_linksUser() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        User user = buildActiveUser();
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String targetUrl = adService.recordAdClick(1L, 1L, "127.0.0.1");

        assertThat(targetUrl).isEqualTo("https://example.com");
        ArgumentCaptor<AdClickLog> clickLogCaptor = ArgumentCaptor.forClass(AdClickLog.class);
        verify(adClickLogRepository).save(clickLogCaptor.capture());
        assertThat(clickLogCaptor.getValue().getUser()).isSameAs(user);
        assertThat(clickLogCaptor.getValue().getAnonymousReason()).isNull();
    }

    @Test
    @DisplayName("click 기록은 전달된 사용자 ID를 찾지 못하면 익명화 사유를 남긴다")
    void recordAdClick_missingUser_recordsAnonymousReason() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(1);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        String targetUrl = adService.recordAdClick(1L, 99L, "127.0.0.1");

        assertThat(targetUrl).isEqualTo("https://example.com");
        ArgumentCaptor<AdClickLog> clickLogCaptor = ArgumentCaptor.forClass(AdClickLog.class);
        verify(adClickLogRepository).save(clickLogCaptor.capture());
        assertThat(clickLogCaptor.getValue().getUser()).isNull();
        assertThat(clickLogCaptor.getValue().getAnonymousReason())
                .isEqualTo(AdClickLog.ANONYMOUS_REASON_USER_NOT_FOUND);
    }

    @Test
    @DisplayName("click 기록은 비활성 사용자를 익명화하고 사유를 남긴다")
    void recordAdClick_inactiveUser_recordsAnonymousReason() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        User user = buildActiveUser();
        user.suspend();
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String targetUrl = adService.recordAdClick(1L, 1L, "127.0.0.1");

        assertThat(targetUrl).isEqualTo("https://example.com");
        ArgumentCaptor<AdClickLog> clickLogCaptor = ArgumentCaptor.forClass(AdClickLog.class);
        verify(adClickLogRepository).save(clickLogCaptor.capture());
        assertThat(clickLogCaptor.getValue().getUser()).isNull();
        assertThat(clickLogCaptor.getValue().getAnonymousReason())
                .isEqualTo(AdClickLog.ANONYMOUS_REASON_USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("click 기록 IP가 45자를 초과하면 잘라낸다")
    void recordAdClick_truncatesLongIpAddress() {
        Ad ad = buildActiveAd("HEADER", FIXED_NOW.plusDays(1));
        when(adRepository.findActiveById(1L, FIXED_NOW)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCountForActive(1L, FIXED_NOW)).thenReturn(1);

        adService.recordAdClick(1L, null, "1".repeat(46));

        ArgumentCaptor<AdClickLog> clickLogCaptor = ArgumentCaptor.forClass(AdClickLog.class);
        verify(adClickLogRepository).save(clickLogCaptor.capture());
        assertThat(clickLogCaptor.getValue().getIpAddress()).hasSize(45);
    }

    @Test
    @DisplayName("click count 증가가 실패하면 로그를 저장하지 않고 AD_NOT_FOUND를 반환한다")
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
                .adName("Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement(placement)
                .targetUrl("https://example.com")
                .startDate(FIXED_NOW.minusDays(1))
                .endDate(endDate)
                .build();
    }

    private User buildActiveUser() {
        return User.builder()
                .loginId("user")
                .password("password")
                .email("user@example.com")
                .displayName("user")
                .build();
    }
}
