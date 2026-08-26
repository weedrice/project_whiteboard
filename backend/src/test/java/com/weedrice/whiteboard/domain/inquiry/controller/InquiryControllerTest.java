package com.weedrice.whiteboard.domain.inquiry.controller;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryCommandService;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryReadService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.config.SecurityConfig;
import com.weedrice.whiteboard.global.config.WebConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint;
import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.RefererCheckInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {InquiryController.class, AdminSupportInquiryController.class},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        })
@AutoConfigureMockMvc
@Import({
        InquiryControllerTest.TestSecurityConfig.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class InquiryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InquiryCommandService commandService;
    @MockitoBean
    private InquiryReadService readService;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    @MockitoBean
    private IpBlockInterceptor ipBlockInterceptor;
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;
    @MockitoBean
    private RefererCheckInterceptor refererCheckInterceptor;
    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;

    private CustomUserDetails userDetails;
    private CustomUserDetails superAdminDetails;

    @BeforeEach
    void setUp() throws Exception {
        userDetails = details(10L, "ROLE_USER");
        superAdminDetails = details(99L, "ROLE_SUPER_ADMIN");
        org.mockito.Mockito.when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.when(refererCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        org.mockito.Mockito.when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void adminApiRejectsRegularUser() throws Exception {
        mockMvc.perform(get("/api/v1/admin/support/inquiries/{inquiryId}", 1L)
                        .with(user(userDetails)))
                .andExpect(status().isForbidden());

        verify(readService, never()).getAdminDetail(anyLong());
    }

    @Test
    void adminApiAllowsSuperAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/support/inquiries/{inquiryId}", 1L)
                        .with(user(superAdminDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(readService).getAdminDetail(1L);
    }

    @Test
    void userDetailHidesAnotherUsersInquiryAsNotFound() throws Exception {
        doThrow(new BusinessException(ErrorCode.INQUIRY_NOT_FOUND))
                .when(readService).getMineDetail(10L, 1L);

        mockMvc.perform(get("/api/v1/inquiries/{inquiryId}", 1L)
                        .with(user(userDetails)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void createRejectsMoreThanFiveImageIdsBeforeServiceCall() throws Exception {
        String body = """
                {
                  "category": "TECHNICAL",
                  "title": "title",
                  "content": "content",
                  "fileIds": [1, 2, 3, 4, 5, 6]
                }
                """;

        mockMvc.perform(post("/api/v1/inquiries")
                        .with(user(userDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(commandService, never()).create(anyLong(), any());
    }

    private CustomUserDetails details(Long userId, String authority) {
        return new CustomUserDetails(
                userId,
                "user-" + userId + "@example.com",
                "password",
                List.of(new SimpleGrantedAuthority(authority)));
    }

    @TestConfiguration
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }
}
