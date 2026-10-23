package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.service.PushSubscriptionService;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import com.weedrice.whiteboard.global.exception.GlobalExceptionHandler;
import com.weedrice.whiteboard.global.log.service.ErrorLogService;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PushSubscriptionControllerTest {
    private PushSubscriptionService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PushSubscriptionService.class);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        mvc = MockMvcBuilders.standaloneSetup(new PushSubscriptionController(service))
                .setCustomArgumentResolvers(new CurrentUserIdArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler(messages,
                        beans.getBeanProvider(ClientIpResolver.class), beans.getBeanProvider(ErrorLogService.class)))
                .build();
        CustomUserDetails principal = new CustomUserDetails(7L, "user@example.com", "test-password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void statusUsesTheAuthenticatedUserAndDoesNotAcceptAnOwnerFromTheBody() throws Exception {
        when(service.isSubscribed(7L, "https://push.example/device")).thenReturn(true);
        mvc.perform(post("/api/v1/users/me/push-subscriptions/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://push.example/device\",\"userId\":8}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
        verify(service).isSubscribed(7L, "https://push.example/device");
    }

    @Test
    void missingMembershipReturnsFalseWithoutOwnerInformation() throws Exception {
        mvc.perform(post("/api/v1/users/me/push-subscriptions/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://push.example/foreign\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(false));
    }

    @Test
    void statusRequiresAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/users/me/push-subscriptions/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"https://push.example/device\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void statusRejectsBlankAndOversizedEndpoints() throws Exception {
        for (String endpoint : List.of("", "x".repeat(501))) {
            mvc.perform(post("/api/v1/users/me/push-subscriptions/status")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
