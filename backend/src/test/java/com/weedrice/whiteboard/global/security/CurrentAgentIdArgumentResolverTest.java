package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentAgentIdArgumentResolverTest {

    private final CurrentAgentIdArgumentResolver resolver = new CurrentAgentIdArgumentResolver();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("supportsParameter supports annotated Long parameters")
    void supportsParameter_annotatedLong_returnsTrue() throws Exception {
        assertThat(resolver.supportsParameter(parameter("requiredAgentId"))).isTrue();
    }

    @Test
    @DisplayName("supportsParameter rejects primitive long parameters")
    void supportsParameter_primitiveLong_returnsFalse() throws Exception {
        assertThat(resolver.supportsParameter(parameter("primitiveAgentId"))).isFalse();
    }

    @Test
    @DisplayName("required current agent id returns authenticated agent id")
    void resolveArgument_requiredAuthenticated_returnsAgentId() throws Exception {
        authenticate(agentPrincipal(7L));

        Object result = resolver.resolveArgument(parameter("requiredAgentId"), null, null, null);

        assertThat(result).isEqualTo(7L);
    }

    @Test
    @DisplayName("optional current agent id returns null for anonymous user")
    void resolveArgument_optionalAnonymous_returnsNull() throws Exception {
        Object result = resolver.resolveArgument(parameter("optionalAgentId"), null, null, null);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("required current agent id rejects anonymous user")
    void resolveArgument_requiredAnonymous_throwsUnauthorized() throws Exception {
        assertThatThrownBy(() -> resolver.resolveArgument(parameter("requiredAgentId"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("required current agent id rejects non AgentPrincipal principal")
    void resolveArgument_nonAgentPrincipal_throwsUnauthorized() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("agent", "password", List.of()));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("requiredAgentId"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("required current agent id rejects principal without agent id")
    void resolveArgument_missingAgentId_throwsUnauthorized() throws Exception {
        authenticate(agentPrincipal(null));

        assertThatThrownBy(() -> resolver.resolveArgument(parameter("requiredAgentId"), null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
    }

    private void authenticate(AgentPrincipal agentPrincipal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(agentPrincipal, "password", List.of()));
    }

    private AgentPrincipal agentPrincipal(Long agentId) {
        return new AgentPrincipal(agentId, 1L, "Writer Agent", "ACTIVE");
    }

    private MethodParameter parameter(String methodName) throws Exception {
        Method method = TestController.class.getDeclaredMethod(methodName, parameterType(methodName));
        return new MethodParameter(method, 0);
    }

    private Class<?> parameterType(String methodName) {
        return methodName.equals("primitiveAgentId") ? long.class : Long.class;
    }

    @SuppressWarnings("unused")
    private static class TestController {
        void requiredAgentId(@CurrentAgentId Long agentId) {
        }

        void optionalAgentId(@CurrentAgentId(required = false) Long agentId) {
        }

        void primitiveAgentId(@CurrentAgentId long agentId) {
        }
    }
}
