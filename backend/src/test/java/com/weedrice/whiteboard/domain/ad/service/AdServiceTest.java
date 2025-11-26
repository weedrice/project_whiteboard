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

import java.util.Collections;

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
    @DisplayName("광고 조회 성공")
    void getAd_success() {
        // given
        String placement = "HEADER";
        Ad ad = Ad.builder().build();
        when(adRepository.findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(any(), any(), any(), any())).thenReturn(Collections.singletonList(ad));

        // when
        adService.getAd(placement);

        // then
        verify(adRepository).findByPlacementAndIsActiveAndStartDateBeforeAndEndDateAfter(any(), any(), any(), any());
    }
}
