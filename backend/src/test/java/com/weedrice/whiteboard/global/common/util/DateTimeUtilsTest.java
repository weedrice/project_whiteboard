package com.weedrice.whiteboard.global.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeUtilsTest {

    @Test
    @DisplayName("KST 현재 시간 조회")
    void nowKST() {
        LocalDateTime now = DateTimeUtils.nowKST();
        assertThat(now).isNotNull();
    }

    @Test
    @DisplayName("LocalDateTime -> KST ZonedDateTime 변환")
    void toKSTZonedDateTime() {
        LocalDateTime now = LocalDateTime.now();
        ZonedDateTime zdt = DateTimeUtils.toKSTZonedDateTime(now);
        assertThat(zdt.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    @DisplayName("ZonedDateTime -> LocalDateTime 변환")
    void toLocalDateTime() {
        ZonedDateTime zdt = ZonedDateTime.now();
        LocalDateTime ldt = DateTimeUtils.toLocalDateTime(zdt);
        assertThat(ldt).isNotNull();
    }

    @Test
    @DisplayName("날짜 포맷팅")
    void format() {
        LocalDateTime dt = LocalDateTime.of(2023, 1, 1, 12, 0, 0);
        String formatted = DateTimeUtils.format(dt);
        assertThat(formatted).isEqualTo("2023-01-01 12:00:00");
    }

    @Test
    @DisplayName("날짜 파싱")
    void parse() {
        String str = "2023-01-01 12:00:00";
        LocalDateTime dt = DateTimeUtils.parse(str);
        assertThat(dt).isEqualTo(LocalDateTime.of(2023, 1, 1, 12, 0, 0));
    }
}