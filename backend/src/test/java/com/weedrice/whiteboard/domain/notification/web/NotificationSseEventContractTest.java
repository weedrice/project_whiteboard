package com.weedrice.whiteboard.domain.notification.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSE 이벤트 이름은 프론트엔드 분기와 맺은 계약이지만 REST envelope와 달리 직렬화
 * 검증 장치가 없다. 이름이 문자열 리터럴로 흩어지면 한쪽만 바뀌어도 아무 신호가 없으므로,
 * 모든 발신이 {@link NotificationSseEvents} 상수를 거치는지 원본에서 확인한다.
 */
class NotificationSseEventContractTest {

    private static final Path REGISTRY_SOURCE = Path.of(
            "src/main/java/com/weedrice/whiteboard/domain/notification/web/NotificationSseEmitterRegistry.java");

    /** {@code .name(...)} 인자가 문자열 리터럴인 경우를 찾는다. */
    private static final Pattern LITERAL_EVENT_NAME = Pattern.compile("\\.name\\(\\s*\"([^\"]*)\"");

    private static final Pattern CONSTANT_EVENT_NAME =
            Pattern.compile("NotificationSseEvents\\.([A-Z_]+)");

    @Test
    @DisplayName("SSE 이벤트 이름을 문자열 리터럴로 직접 넘기지 않는다")
    void emitsEventNamesOnlyThroughConstants() throws IOException {
        String source = Files.readString(REGISTRY_SOURCE);

        Set<String> literals = new LinkedHashSet<>();
        Matcher matcher = LITERAL_EVENT_NAME.matcher(source);
        while (matcher.find()) {
            literals.add(matcher.group(1));
        }

        assertThat(literals)
                .as("NotificationSseEvents 상수를 거치지 않은 이벤트 이름이 있다. "
                        + "상수를 추가하고 프론트엔드 union 타입도 함께 고칠 것")
                .isEmpty();
    }

    @Test
    @DisplayName("실제로 참조하는 상수는 모두 ALL 목록에 등재되어 있다")
    void referencedConstantsAreRegistered() throws IOException {
        String source = Files.readString(REGISTRY_SOURCE);

        Set<String> referenced = new LinkedHashSet<>();
        Matcher matcher = CONSTANT_EVENT_NAME.matcher(source);
        while (matcher.find()) {
            referenced.add(matcher.group(1));
        }

        assertThat(referenced).isNotEmpty();
        for (String name : referenced) {
            assertThat(resolve(name))
                    .as("%s 상수가 NotificationSseEvents.ALL에 없다", name)
                    .isIn(NotificationSseEvents.ALL);
        }
    }

    @Test
    @DisplayName("ALL은 선언된 이벤트 상수를 빠짐없이 담는다")
    void allContainsEveryDeclaredEvent() {
        Set<String> declared = Stream.of(NotificationSseEvents.class.getDeclaredFields())
                .filter(field -> field.getType() == String.class)
                .map(field -> {
                    try {
                        return (String) field.get(null);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(NotificationSseEvents.ALL).containsExactlyInAnyOrderElementsOf(declared);
    }

    private static String resolve(String constantName) {
        try {
            return (String) NotificationSseEvents.class.getDeclaredField(constantName).get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("알 수 없는 상수: " + constantName, e);
        }
    }
}
