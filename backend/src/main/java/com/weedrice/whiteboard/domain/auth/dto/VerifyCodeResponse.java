package com.weedrice.whiteboard.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Builder
public class VerifyCodeResponse {
    private boolean verified;
    private String loginId;    // 재가입 시 이메일 인증 완료 후 마스킹 해제용

    @JsonProperty("isReregister")
    private boolean isReregister;

    public VerifyCodeResponse(boolean verified, String loginId, boolean isReregister) {
        this.verified = verified;
        this.loginId = loginId;
        this.isReregister = isReregister;
    }
}
