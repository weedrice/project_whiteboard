package com.weedrice.whiteboard.global.security.oauth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OAuthAttributesTest {

    @Test
    @DisplayName("Naver nested response is parsed without an unchecked map cast")
    void ofNaver_parsesStringKeyedNestedResponse() {
        OAuthAttributes attributes = OAuthAttributes.of(
                "naver",
                "unused",
                Map.of("response", Map.of(
                        "id", "naver-user-id",
                        "name", "OAuth User",
                        "email", "oauth@example.com",
                        "profile_image", "https://example.com/profile.png")));

        assertThat(attributes.getProviderId()).isEqualTo("naver-user-id");
        assertThat(attributes.getName()).isEqualTo("OAuth User");
        assertThat(attributes.getEmail()).isEqualTo("oauth@example.com");
    }

    @Test
    @DisplayName("Naver response with an invalid nested shape is rejected")
    void ofNaver_rejectsInvalidNestedResponse() {
        assertThatThrownBy(() -> OAuthAttributes.of("naver", "unused", Map.of("response", "invalid")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthenticationException) error).getError().getErrorCode())
                        .isEqualTo("invalid_user_info_response"));
    }

    @Test
    @DisplayName("unsupported OAuth2 providers are rejected instead of being parsed as Google")
    void of_rejectsUnsupportedProvider() {
        assertThatThrownBy(() -> OAuthAttributes.of("unknown", "id", Map.of("id", "provider-user-id")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthenticationException) error).getError().getErrorCode())
                        .isEqualTo("unsupported_provider"));
    }

    @Test
    @DisplayName("missing provider id is rejected before account resolution")
    void getProviderId_rejectsMissingProviderId() {
        OAuthAttributes attributes = OAuthAttributes.of(
                "google",
                "sub",
                Map.of("name", "OAuth User", "email", "oauth@example.com"));

        assertThatThrownBy(attributes::getProviderId)
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthenticationException) error).getError().getErrorCode())
                        .isEqualTo("invalid_user_info_response"));
    }
}
