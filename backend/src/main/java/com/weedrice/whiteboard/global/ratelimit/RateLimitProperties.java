package com.weedrice.whiteboard.global.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml의 rate-limit 설정을 바인딩합니다.
 *
 * <pre>
 * rate-limit:
 *   enabled: true
 *   default-limit: 100   # IP 기반 기본 (requests per minute)
 *   auth-limit: 5        # 인증 엔드포인트 (무차별 대입 방지)
 *   api-limit: 200      # 일반 API
 *   user-limit: 500      # 인증된 사용자
 * </pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int defaultLimit = 100;
    private int authLimit = 5;
    private int apiLimit = 200;
    private int userLimit = 500;
}
