package com.weedrice.whiteboard.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResult {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private LoginResponse.UserInfo user;
}
