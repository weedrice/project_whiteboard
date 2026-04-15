package com.weedrice.whiteboard.domain.ad.service;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import com.weedrice.whiteboard.domain.ad.repository.AdClickLogRepository;
import com.weedrice.whiteboard.domain.ad.repository.AdRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("광고 조회는 impression 수를 증가시키지 않는다")
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
    @DisplayName("impression 기록 시 count를 증가시킨다")
    void recordAdImpression_success() {
        Ad ad = Ad.builder()
                .adName("Header Ad")
                .imageUrl("https://cdn.test/ad.png")
                .placement("HEADER")
                .targetUrl("https://example.com")
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        when(adRepository.findById(1L)).thenReturn(Optional.of(ad));

        adService.recordAdImpression(1L, 10L, "127.0.0.1");

        assertThat(ad.getImpressionCount()).isEqualTo(1);
    }
}
