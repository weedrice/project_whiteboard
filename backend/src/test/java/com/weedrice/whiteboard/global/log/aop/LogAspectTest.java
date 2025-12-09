package com.weedrice.whiteboard.global.log.aop;

import com.weedrice.whiteboard.global.log.service.LogService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogAspectTest {

    @Mock
    private LogService logService;

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private LogAspect logAspect;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        ServletRequestAttributes attributes = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("로그 저장 AOP 동작 확인 - 로그인 사용자")
    void logBefore_loggedInUser() {
        // given
        Method mockMethod = mock(Method.class);
        when(mockMethod.getName()).thenReturn("testControllerMethod");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(mockMethod);
        
        String expectedActionType = "testControllerMethod";

        request.setRemoteAddr("127.0.0.1");

        CustomUserDetails userDetails = new CustomUserDetails(1L, "user", "password", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);

        // when
        logAspect.logBefore(joinPoint);

        // then
        verify(logService).saveLog(eq(1L), eq(expectedActionType), eq("127.0.0.1"), anyString());
    }

    @Test
    @DisplayName("로그 저장 AOP 동작 확인 - 익명 사용자")
    void logBefore_anonymousUser() {
        // given
        Method mockMethod = mock(Method.class);
        when(mockMethod.getName()).thenReturn("testControllerMethod");

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(mockMethod);
        
        String expectedActionType = "testControllerMethod";

        request.setRemoteAddr("127.0.0.1");

        SecurityContextHolder.clearContext();

        // when
        logAspect.logBefore(joinPoint);

        // then
        verify(logService).saveLog(eq(null), eq(expectedActionType), eq("127.0.0.1"), anyString());
    }

    @Test
    @DisplayName("로그 저장 AOP 동작 중 에러 발생 시 처리 확인")
    void logBefore_errorHandling() {
        // given
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        Method mockMethod = mock(Method.class);
        when(mockMethod.getName()).thenReturn("testControllerMethod");
        when(methodSignature.getMethod()).thenReturn(mockMethod);

        request.setRemoteAddr("127.0.0.1");

        // Mock a scenario where logService.saveLog throws an exception
        doThrow(new RuntimeException("Test LogService Error")).when(logService).saveLog(nullable(Long.class), any(String.class), any(String.class), any(String.class));

        // when
        logAspect.logBefore(joinPoint);

        // then
        // Verify that logService.saveLog was attempted
        verify(logService).saveLog(nullable(Long.class), eq("testControllerMethod"), eq("127.0.0.1"), anyString());
        // In a real scenario, we might also verify a log.error was called, but that requires mocking SLF4J/Logger
        // For now, simply ensuring no unhandled exception propagates is sufficient.
    }
}
