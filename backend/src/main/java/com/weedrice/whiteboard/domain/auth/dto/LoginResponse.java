package com.weedrice.whiteboard.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.user.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponse {
    private String accessToken;
    private long expiresIn;
    private UserInfo user;

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String loginId;
        private String displayName;
        private String profileImageUrl;
        // 명시적 getter에만 붙이면 속성이 읽기 전용이 된다. 양쪽에 같은 이름을 준다.
        @JsonProperty("isEmailVerified")
        private boolean isEmailVerified;
        private String role;
        private String theme;
        private Integer points;

        @JsonProperty("isEmailVerified")
        public boolean isEmailVerified() {
            return isEmailVerified;
        }
    }
}
