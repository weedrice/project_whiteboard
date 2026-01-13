package com.weedrice.whiteboard.domain.ad.dto;

import com.weedrice.whiteboard.domain.ad.entity.Ad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class AdResponseTest {

    @Test
    @DisplayName("AdResponse.from() 테스트")
    void from() {
        Ad ad = Ad.builder().build();
        ReflectionTestUtils.setField(ad, "adId", 1L);
        ReflectionTestUtils.setField(ad, "adName", "Test Ad");
        ReflectionTestUtils.setField(ad, "imageUrl", "http://image");
        ReflectionTestUtils.setField(ad, "targetUrl", "http://target");
        ReflectionTestUtils.setField(ad, "placement", "TOP");

        AdResponse response = AdResponse.from(ad);

        assertThat(response.adId()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Test Ad");
        assertThat(response.imageUrl()).isEqualTo("http://image");
        assertThat(response.targetUrl()).isEqualTo("http://target");
        assertThat(response.placement()).isEqualTo("TOP");
    }

    @Test
    @DisplayName("AdResponse.from() null 입력 시 null 반환")
    void from_null() {
        assertThat(AdResponse.from(null)).isNull();
    }
}
