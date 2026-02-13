package com.weedrice.whiteboard.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Builder
public class ReregisterCheckResponse {
    private boolean canReregister;
    private String maskedLoginId;

    public ReregisterCheckResponse(boolean canReregister, String maskedLoginId) {
        this.canReregister = canReregister;
        this.maskedLoginId = maskedLoginId;
    }
}
