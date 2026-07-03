package com.weedrice.whiteboard.domain.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OAuthSignupTicketResponse {
    private String email;
    private String name;
    private String provider;
}
