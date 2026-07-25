package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.common.util.DateTimeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /**
     * 서비스 계층이 주입받는 시계. JPA auditing용 provider는 슬라이스 컨텍스트에서도
     * 로드되어야 하므로 {@code WhiteboardApplication}에 따로 두었으며, 양쪽 모두
     * {@link DateTimeUtils#KST_ZONE_ID}에서 파생된다.
     *
     * <p>아직 주입 Clock을 쓰지 않는 지점이 남아 있어(예: {@code NotificationCommandService},
     * {@code Notification} 엔티티 기본값) {@code WhiteboardApplication.init()}의
     * {@code TimeZone.setDefault} 호출은 제거할 수 없다. 남은 지점을 정리한 뒤 재검토한다.
     */
    @Bean
    public Clock clock() {
        return Clock.system(DateTimeUtils.KST_ZONE_ID);
    }
}
