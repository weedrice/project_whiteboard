package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * `LocalDateTime`을 offset이 붙은 문자열로 주고받는다.
 *
 * <p>기존에는 offset 없는 벽시계 문자열(`2026-07-25T10:00:00`)을 내보냈다. 저장값은 KST
 * 기준이지만 그 사실이 값에 담기지 않아, 브라우저가 KST인 사용자에게만 우연히 맞고
 * 그 밖의 사용자에게는 항상 어긋났다. ECMAScript 규격상 offset 없는 date-time 문자열은
 * 브라우저 로컬 시각으로 해석되기 때문이다.
 *
 * <p>저장 계층은 그대로 두고 직렬화에서만 KST offset을 붙인다. DB 마이그레이션 없이
 * 클라이언트가 "언제인지"를 알 수 있게 되며, 이후 사용자 지역 기준 표시의 선결 조건이 된다.
 *
 * <p>역직렬화는 두 형식을 모두 받는다. 클라이언트가 응답으로 받은 값을 그대로 되돌려
 * 보내는 경로(임시저장 `updatedAt`, 예약 발행 `scheduledAt` 등)가 있어, offset이 붙은
 * 값을 거부하면 그 흐름이 깨진다.
 */
public class LocalDateTimeWireModule extends SimpleModule {

    public LocalDateTimeWireModule() {
        addSerializer(LocalDateTime.class, new KstOffsetSerializer());
        addDeserializer(LocalDateTime.class, new OffsetTolerantDeserializer());
    }

    private static final class KstOffsetSerializer extends ValueSerializer<LocalDateTime> {

        @Override
        public void serialize(LocalDateTime value, JsonGenerator generator, SerializationContext context) {
            generator.writeString(value.atZone(DateTimeUtils.KST_ZONE_ID)
                    .toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
    }

    private static final class OffsetTolerantDeserializer extends ValueDeserializer<LocalDateTime> {

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) {
            String raw = parser.getString();
            if (raw == null || raw.isBlank()) {
                return null;
            }

            String value = raw.trim();
            try {
                // offset이 붙어 있으면 KST 벽시계로 되돌린다. 저장 기준이 KST이기 때문이다.
                return OffsetDateTime.parse(value)
                        .atZoneSameInstant(DateTimeUtils.KST_ZONE_ID)
                        .toLocalDateTime();
            } catch (DateTimeParseException notAnOffset) {
                return LocalDateTime.parse(value);
            }
        }
    }
}
