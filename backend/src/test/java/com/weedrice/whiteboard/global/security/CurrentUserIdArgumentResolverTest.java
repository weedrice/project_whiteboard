package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserIdArgumentResolverTest {

    private final CurrentUserIdArgumentResolver resolver = new CurrentUserIdArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("supportsParameter supports annotated Long parameters")
    void supportsParameter_annotatedLong_returnsTrue() throws Exception {
        assertThat(resolver.supportsParameter(parameter("requiredUserId"))).isTrue();
    }

    @Test
    @DisplayName("supportsParameter rejects primitive long parameters")
    void supportsParameter_primitiveLong_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(parameter("primitiveUserId"))).isFalse();
    }

    @Test
    @DisplayName("required current user id returns authenticated user id")
    void resolveArgument_requiredAuthenticated_returnsUserId() throws Exception {
        authenticate(userDetails(1L));

        Object result = resolver.resolveArgument(parameter("requiredUserId"), null, null, null);

        assertThat(result).isEqualTo(1L);
    }

    @Test
    @DisplayName("optional current user id returns null for anonymous user")
    void resolveArgument_optionalAnonymous_returnsNull() throws Exception {
        Object result = resolver.resolveArgument(parameter("optionalUserId"), null, null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("required current user id rejects anonymous user")
    void resolveArgument_requiredAnonymous_throwsUnauthorized() throws Exception {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter("requiredUserId"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("required current user id rejects non CustomUserDetails principal")
    void resolveArgument_nonUserPrincipal_throwsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "password", List.of()));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("requiredUserId"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    private void authenticate(CustomUserDetails userDetails) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, "password", userDetails.getAuthorities()));
    }

    private CustomUserDetails userDetails(Long userId) {
        return new CustomUserDetails(
                userId,
                "test@example.com",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private MethodParameter parameter(String methodName) throws Exception {
        Method method = TestController.class.getDeclaredMethod(methodName, parameterType(methodName));
        return new MethodParameter(method, 0);
    }

    private Class<?> parameterType(String methodName) {
        return methodName.equals("primitiveUserId") ? long.class : Long.class;
    }

    @SuppressWarnings("unused")
    private static class TestController {
        void requiredUserId(@CurrentUserId Long userId) {
        }

        void optionalUserId(@CurrentUserId(required = false) Long userId) {
        }

        void primitiveUserId(@CurrentUserId long userId) {
        }
    }
}
