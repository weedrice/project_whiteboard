package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeUtilsTest {

    @Test
    @DisplayName("현재 KST 시간 조회")
    void nowKST() {
        LocalDateTime now = DateTimeUtils.nowKST();
        assertThat(now).isNotNull();
        // 대략적인 검증: 시스템 시간과 너무 차이나지 않는지 (초 단위)
        // 로컬 테스트 환경이 KST가 아닐 수 있으므로 정확한 시간 비교는 어려움
    }

    @Test
    @DisplayName("LocalDateTime -> KST ZonedDateTime 변환")
    void toKSTZonedDateTime() {
        LocalDateTime localDateTime = LocalDateTime.of(2023, 1, 1, 12, 0);
        ZonedDateTime zonedDateTime = DateTimeUtils.toKSTZonedDateTime(localDateTime);
        
        assertThat(zonedDateTime.getZone()).isEqualTo(DateTimeUtils.KST_ZONE_ID);
        assertThat(zonedDateTime.toLocalDateTime()).isEqualTo(localDateTime);
    }

    @Test
    @DisplayName("포맷팅 및 파싱")
    void formatAndParse() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 25, 15, 30, 0);
        String formatted = DateTimeUtils.format(dateTime);
        assertThat(formatted).isEqualTo("2023-12-25 15:30:00");

        LocalDateTime parsed = DateTimeUtils.parse(formatted);
        assertThat(parsed).isEqualTo(dateTime);
    }

    @Test
    @DisplayName("사용자 지정 포맷")
    void customFormat() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 12, 25, 15, 30, 0);
        String pattern = "yyyy/MM/dd HH:mm:ss";
        String formatted = DateTimeUtils.format(dateTime, pattern);
        assertThat(formatted).isEqualTo("2023/12/25 15:30:00");
        
        LocalDateTime parsed = DateTimeUtils.parse(formatted, pattern);
        assertThat(parsed).isEqualTo(dateTime);
    }
}
