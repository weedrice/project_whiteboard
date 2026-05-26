package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatedUserResolverTest {

    @Test
    @DisplayName("optional user id returns null for anonymous principal")
    void optionalUserId_anonymous_returnsNull() {
        assertThat(AuthenticatedUserResolver.optionalUserId(null)).isNull();
    }

    @Test
    @DisplayName("optional user id returns authenticated user id")
    void optionalUserId_authenticated_returnsUserId() {
        CustomUserDetails userDetails = userDetails(1L);

        assertThat(AuthenticatedUserResolver.optionalUserId(userDetails)).isEqualTo(1L);
    }

    @Test
    @DisplayName("required user id rejects anonymous principal")
    void requiredUserId_anonymous_throwsUnauthorized() {
        assertThatThrownBy(() -> AuthenticatedUserResolver.requiredUserId(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("required user id rejects principal without user id")
    void requiredUserId_missingUserId_throwsUnauthorized() {
        assertThatThrownBy(() -> AuthenticatedUserResolver.requiredUserId(userDetails(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("required principal returns authenticated principal")
    void requiredPrincipal_authenticated_returnsPrincipal() {
        CustomUserDetails userDetails = userDetails(1L);

        assertThat(AuthenticatedUserResolver.requiredPrincipal(userDetails)).isSameAs(userDetails);
    }

    private CustomUserDetails userDetails(Long userId) {
        return new CustomUserDetails(
                userId,
                "test@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
