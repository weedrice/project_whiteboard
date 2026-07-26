package com.weedrice.whiteboard;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import com.weedrice.whiteboard.global.config.LocalDateTimeWireModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@SpringBootApplication
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@EnableAsync
public class WhiteboardApplication {

    @jakarta.annotation.PostConstruct
    public void init() {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(DateTimeUtils.KST_ZONE_ID));
    }

    /**
     * JPA auditing이 JVM 기본 timezone 대신 {@link DateTimeUtils#KST_ZONE_ID}를 따르도록 한다.
     * 이 provider가 없으면 Spring Data는 기본 CurrentDateTimeProvider를 사용하며,
     * createdAt/modifiedAt이 서비스 계층의 주입 Clock과 다른 기준으로 기록될 수 있다.
     *
     * <p>{@code @EnableJpaAuditing}과 같은 클래스에 두어야 {@code @DataJpaTest} 슬라이스
     * 컨텍스트에서도 함께 로드된다. 별도 {@code @Configuration}에 두면 슬라이스가 이를
     * 제외해 provider 조회가 실패한다.
     *
     * <p><b>주의</b>: 이 provider는 {@code TimeConfig}의 {@code Clock} 빈을 주입받지 않는다.
     * 따라서 테스트에서 {@code Clock} 빈을 {@code Clock.fixed(...)}로 교체해도 audit 필드
     * (createdAt/modifiedAt)에는 반영되지 않는다. audit 시각을 고정해야 하는 테스트는
     * 이 provider 자체를 교체해야 한다.
     */
    @Bean
    public DateTimeProvider auditingDateTimeProvider() {
        Clock clock = Clock.system(DateTimeUtils.KST_ZONE_ID);
        return () -> Optional.of(LocalDateTime.now(clock));
    }

    /**
     * `LocalDateTime` 필드를 offset이 붙은 형식으로 주고받게 한다.
     *
     * <p>{@code auditingDateTimeProvider}와 같은 이유로 여기에 둔다. 별도 {@code @Configuration}에
     * 두면 {@code @WebMvcTest} 같은 슬라이스가 이를 제외해, 슬라이스 테스트는 offset 없는
     * 예전 형식을 보게 되고 실제 응답과 어긋난다.
     */
    @Bean
    public LocalDateTimeWireModule localDateTimeWireModule() {
        return new LocalDateTimeWireModule();
    }

    public static void main(String[] args) {
        SpringApplication.run(WhiteboardApplication.class, args);
    }
}
