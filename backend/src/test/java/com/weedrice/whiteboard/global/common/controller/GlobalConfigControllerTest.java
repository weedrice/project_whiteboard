package com.weedrice.whiteboard.global.common.controller;

import tools.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalConfigController.class, 
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@Import({
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class GlobalConfigControllerTest {

    private static final Long ACTOR_USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GlobalConfigService globalConfigService;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    @MockitoBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.oauth.CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.oauth.OAuth2SuccessHandler oAuth2SuccessHandler;

    private CustomUserDetails adminUser;

    @BeforeEach
    void setUp() throws Exception {
        adminUser = new CustomUserDetails(ACTOR_USER_ID, "admin", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

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
        when(globalConfigService.getConfigResponseOrThrow(ACTOR_USER_ID, "key"))
                .thenReturn(configResponse("key", "value", null));

        mockMvc.perform(get("/api/v1/configs/{key}", "key")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("key"))
                .andExpect(jsonPath("$.data.value").value("value"));
    }

    @Test
    @DisplayName("GET /configs/{key} response uses normalized key")
    void getConfig_normalizesResponseKey() throws Exception {
        when(globalConfigService.getConfigResponseOrThrow(ACTOR_USER_ID, " key "))
                .thenReturn(configResponse("key", "value", null));

        mockMvc.perform(get("/api/v1/configs/{key}", " key ")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("key"))
                .andExpect(jsonPath("$.data.value").value("value"));
    }

    @Test
    @DisplayName("단건 설정 조회는 없는 key에 404를 반환한다")
    void getConfig_missing_returnsNotFound() throws Exception {
        when(globalConfigService.getConfigResponseOrThrow(ACTOR_USER_ID, "missing"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/configs/{key}", "missing")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("공개 설정 조회는 DTO 목록을 반환한다")
    void getPublicConfigs_success() throws Exception {
        when(globalConfigService.getPublicConfigs()).thenReturn(List.of(configResponse("site.name", "Noviis", "desc")));

        mockMvc.perform(get("/api/v1/configs/public")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("site.name"))
                .andExpect(jsonPath("$.data[0].value").value("Noviis"));
    }

    @Test
    @DisplayName("전체 설정 조회")
    void getAllConfigs_success() throws Exception {
        when(globalConfigService.getAllConfigs(ACTOR_USER_ID)).thenReturn(List.of(configResponse("key", "val", "desc")));

        mockMvc.perform(get("/api/v1/admin/configs")
                .with(user(adminUser))
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].key").value("key"));
    }

    @Test
    @DisplayName("설정 생성")
    void createConfig_success() throws Exception {
        Map<String, String> request = Map.of("key", "newKey", "value", "val", "description", "desc");
        when(globalConfigService.createConfig(ACTOR_USER_ID, "newKey", "val", "desc"))
                .thenReturn(configResponse("newKey", "val", "desc"));

        mockMvc.perform(post("/api/v1/admin/configs")
                .with(user(adminUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.key").value("newKey"));
    }

    @Test
    @DisplayName("설정 생성 - 필수값 누락 시 400")
    void createConfig_validationFailure() throws Exception {
        Map<String, String> request = Map.of("value", "val");

        mockMvc.perform(post("/api/v1/admin/configs")
                .with(user(adminUser))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        verify(globalConfigService, never()).createConfig(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("설정 수정")
    void updateConfig_success() throws Exception {
        Map<String, String> request = Map.of("key", "key", "value", "newVal");

        when(globalConfigService.updateConfig(ACTOR_USER_ID, "key", "newVal", null))
                .thenReturn(configResponse("key", "newVal", "desc"));

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

        when(globalConfigService.updateConfig(ACTOR_USER_ID, "key", "newVal", null))
                .thenReturn(configResponse("key", "newVal", "desc"));

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
        doNothing().when(globalConfigService).deleteConfig(ACTOR_USER_ID, "key");

        mockMvc.perform(delete("/api/v1/admin/configs/{key}", "key")
                .with(user(adminUser))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("admin config endpoints require SUPER_ADMIN")
    void adminConfigEndpoints_requireSuperAdminPreAuthorize() throws Exception {
        assertRequiresSuperAdmin("getConfig", String.class, Long.class);
        assertRequiresSuperAdmin("getAllConfigs", Long.class);
        assertRequiresSuperAdmin("createConfig",
                com.weedrice.whiteboard.global.common.dto.GlobalConfigCreateRequest.class,
                Long.class);
        assertRequiresSuperAdmin("updateConfig",
                com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateRequest.class,
                Long.class);
        assertRequiresSuperAdmin("updateConfigByKey",
                String.class,
                com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateByKeyRequest.class,
                Long.class);
        assertRequiresSuperAdmin("deleteConfig", String.class, Long.class);
    }

    @Test
    @DisplayName("public config endpoint has no PreAuthorize")
    void publicConfigEndpoint_hasNoPreAuthorize() throws Exception {
        Method method = GlobalConfigController.class.getDeclaredMethod("getPublicConfigs");

        assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
    }

    private GlobalConfigResponse configResponse(String key, String value, String description) {
        return GlobalConfigResponse.builder()
                .key(key)
                .value(value)
                .description(description)
                .build();
    }

    private void assertRequiresSuperAdmin(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = GlobalConfigController.class.getDeclaredMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('" + Role.SUPER_ADMIN + "')");
    }
}
