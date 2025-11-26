package com.weedrice.whiteboard.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupResponse {
    private Long userId;
    private String loginId;
    private String email;
    private String displayName;
}
