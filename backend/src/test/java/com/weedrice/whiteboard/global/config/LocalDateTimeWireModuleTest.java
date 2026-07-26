package com.weedrice.whiteboard.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 저장 기준이 KST임을 값 자체에 담아 보내는지, 그리고 클라이언트가 되돌려 보낸 값을
 * 다시 받아들이는지 확인한다. 되돌려 보내는 경로(임시저장 updatedAt, 예약 발행 scheduledAt)가
 * 실제로 있어 한쪽만 바꾸면 그 흐름이 깨진다.
 */
class LocalDateTimeWireModuleTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new LocalDateTimeWireModule())
            .build();

    @Test
    @DisplayName("직렬화 시 KST offset을 붙인다")
    void serializesWithKstOffset() {
        String json = mapper.writeValueAsString(LocalDateTime.of(2026, 7, 25, 10, 0, 0));

        assertThat(json).isEqualTo("\"2026-07-25T10:00:00+09:00\"");
    }

    @Test
    @DisplayName("offset이 붙은 값을 KST 벽시계로 되돌린다")
    void deserializesOffsetValueBackToKstWallClock() {
        LocalDateTime parsed = mapper.readValue("\"2026-07-25T10:00:00+09:00\"", LocalDateTime.class);

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 25, 10, 0, 0));
    }

    @Test
    @DisplayName("다른 지역 offset은 같은 순간의 KST 벽시계로 변환한다")
    void convertsForeignOffsetToSameInstantInKst() {
        LocalDateTime parsed = mapper.readValue("\"2026-07-25T01:00:00Z\"", LocalDateTime.class);

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 25, 10, 0, 0));
    }

    @Test
    @DisplayName("offset 없는 기존 형식도 그대로 받는다")
    void stillAcceptsOffsetLessValues() {
        LocalDateTime parsed = mapper.readValue("\"2026-07-25T10:00:00\"", LocalDateTime.class);

        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 25, 10, 0, 0));
    }

    @Test
    @DisplayName("왕복해도 값이 보존된다")
    void roundTripsWithoutDrift() {
        LocalDateTime original = LocalDateTime.of(2026, 12, 31, 23, 59, 59);

        assertThat(mapper.readValue(mapper.writeValueAsString(original), LocalDateTime.class))
                .isEqualTo(original);
    }
}
