package com.weedrice.whiteboard.global.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest(controllers = GlobalConfigController.class, 
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
class GlobalConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GlobalConfigService globalConfigService;

    @MockBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockBean
    private com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private com.weedrice.whiteboard.domain.user.repository.UserRepository userRepository;

    @MockBean
    private com.weedrice.whiteboard.domain.admin.repository.AdminRepository adminRepository;

    @MockBean
    private com.weedrice.whiteboard.global.security.oauth.CustomOAuth2UserService customOAuth2UserService;

    @MockBean
    private com.weedrice.whiteboard.global.security.oauth.OAuth2SuccessHandler oAuth2SuccessHandler;

    private CustomUserDetails adminUser;

    @BeforeEach
    void setUp() throws Exception {
        adminUser = new CustomUserDetails(1L, "admin", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        // UserRepository mock 설정 (SecurityUtils에서 사용)
        com.weedrice.whiteboard.domain.user.entity.User user = com.weedrice.whiteboard.domain.user.entity.User.builder()
                .loginId("admin")
                .displayName("Admin")
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "userId", 1L);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "isSuperAdmin", true);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));

        // SecurityUtils 초기화 (static 필드 설정)
        com.weedrice.whiteboard.global.common.util.SecurityUtils securityUtils = 
            new com.weedrice.whiteboard.global.common.util.SecurityUtils(userRepository, adminRepository);
        securityUtils.init();

        when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(refererCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("설정 단건 조회")
    void getConfig_success() throws Exception {
        when(globalConfigService.getConfig("key")).thenReturn("value");

        mockMvc.perform(get("/api/v1/configs/{key}", "key")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("value"));
    }

    @Test
    @DisplayName("전체 설정 조회")
    void getAllConfigs_success() throws Exception {
        GlobalConfig config = new GlobalConfig("key", "val", "desc");
        when(globalConfigService.getAllConfigs()).thenReturn(List.of(config));

        mockMvc.perform(get("/api/v1/admin/configs")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].configKey").value("key"));
    }

    @Test
    @DisplayName("설정 생성")
    void createConfig_success() throws Exception {
        Map<String, String> request = Map.of("key", "newKey", "value", "val", "description", "desc");
        GlobalConfig created = new GlobalConfig("newKey", "val", "desc");

        when(globalConfigService.createConfig("newKey", "val", "desc")).thenReturn(created);

        mockMvc.perform(post("/api/v1/admin/configs")
                .with(user(adminUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configKey").value("newKey"));
    }

    @Test
    @DisplayName("설정 수정")
    void updateConfig_success() throws Exception {
        Map<String, String> request = Map.of("key", "key", "value", "newVal");

        when(globalConfigService.updateConfig("key", "newVal", null))
                .thenReturn(new GlobalConfig("key", "newVal", "desc"));

        mockMvc.perform(put("/api/v1/admin/configs")
                .with(user(adminUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("키로 설정 수정")
    void updateConfigByKey_success() throws Exception {
        Map<String, String> request = Map.of("value", "newVal");

        when(globalConfigService.updateConfig("key", "newVal", null))
                .thenReturn(new GlobalConfig("key", "newVal", "desc"));

        mockMvc.perform(put("/api/v1/admin/configs/{key}", "key")
                .with(user(adminUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("설정 삭제")
    void deleteConfig_success() throws Exception {
        doNothing().when(globalConfigService).deleteConfig("key");

        mockMvc.perform(delete("/api/v1/admin/configs/{key}", "key")
                .with(user(adminUser))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
