package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdServiceTest {

    @Mock
    private AdRepository adRepository;
    @Mock
    private AdClickLogRepository adClickLogRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdService adService;

    @Test
    @DisplayName("광고 조회는 impression count를 증가시키지 않는다")
    void getAd_success() {
        String placement = "HEADER";
        Ad ad = Ad.builder()
                .adName("Header Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement(placement)
                .targetUrl("https://example.com")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        when(adRepository.findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(ad));

        Ad result = adService.getAd(placement);

        assertThat(result).isSameAs(ad);
        assertThat(ad.getImpressionCount()).isZero();
        verify(adRepository).findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(any(), any(), any(), any());
    }

    @Test
    @DisplayName("impression 기록은 원자 update를 사용한다")
    void recordAdImpression_success() {
        when(adRepository.incrementImpressionCount(1L)).thenReturn(1);

        adService.recordAdImpression(1L, 10L, "127.0.0.1");

        verify(adRepository).incrementImpressionCount(1L);
    }

    @Test
    @DisplayName("click 기록은 원자 update 후 targetUrl을 반환한다")
    void recordAdClick_success() {
        Ad ad = Ad.builder()
                .adName("Header Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement("HEADER")
                .targetUrl("https://example.com")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCount(1L)).thenReturn(1);

        String targetUrl = adService.recordAdClick(1L, null, "127.0.0.1");

        assertThat(targetUrl).isEqualTo("https://example.com");
        verify(adRepository).incrementClickCount(1L);
        verify(adClickLogRepository).save(any());
    }

    @Test
    @DisplayName("click count 증가가 실패하면 AD_NOT_FOUND를 반환한다")
    void recordAdClick_incrementFailure_throwsAdNotFound() {
        Ad ad = Ad.builder()
                .adName("Header Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement("HEADER")
                .targetUrl("https://example.com")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));
        when(adRepository.incrementClickCount(1L)).thenReturn(0);

        assertThatThrownBy(() -> adService.recordAdClick(1L, null, "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.AD_NOT_FOUND));

        verify(adClickLogRepository, never()).save(any());
    }
}
