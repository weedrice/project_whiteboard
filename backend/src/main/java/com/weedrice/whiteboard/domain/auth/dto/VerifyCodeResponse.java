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
    private String verificationTicket;
    private String loginId;

    @JsonProperty("isReregister")
    private boolean isReregister;

    public VerifyCodeResponse(boolean verified, String verificationTicket, String loginId, boolean isReregister) {
        this.verified = verified;
        this.verificationTicket = verificationTicket;
        this.loginId = loginId;
        this.isReregister = isReregister;
    }
}
