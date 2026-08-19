package com.weedrice.whiteboard.domain.shop.controller;

import com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor;
import com.weedrice.whiteboard.domain.shop.dto.AdminShopItemResponse;
import com.weedrice.whiteboard.domain.shop.service.AdminShopService;
import com.weedrice.whiteboard.global.config.CurrentUserIdWebMvcConfig;
import com.weedrice.whiteboard.global.config.SecurityConfig;
import com.weedrice.whiteboard.global.config.WebConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.CurrentUserIdArgumentResolver;
import com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint;
import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.RefererCheckInterceptor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShopAdminController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class)
        })
@Import({
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
class ShopAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminShopService adminShopService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private RateLimitInterceptor rateLimitInterceptor;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private CustomUserDetails superAdmin;
    private CustomUserDetails regularUser;

    @BeforeEach
    void setUp() throws Exception {
        superAdmin = userDetails(1L, "ROLE_SUPER_ADMIN");
        regularUser = userDetails(2L, "ROLE_USER");

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
    void getItems_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/shop/items"))
                .andExpect(status().isUnauthorized());

        verify(adminShopService, never()).getItems(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getItems_passesActorAndFiltersToService() throws Exception {
        PageRequest pageRequest = PageRequest.of(1, 10);
        AdminShopItemResponse item = response(false);
        when(adminShopService.getItems(
                eq(1L), eq("premium"), eq("EMOTICON"), eq(true), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(item), pageRequest, 11));

        mockMvc.perform(get("/api/v1/admin/shop/items")
                        .param("q", "premium")
                        .param("itemType", "EMOTICON")
                        .param("isActive", "true")
                        .param("isSaleEnabled", "false")
                        .param("page", "1")
                        .param("size", "10")
                        .with(user(superAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].itemId").value(2))
                .andExpect(jsonPath("$.data.content[0].isSaleEnabled").value(false));

        verify(adminShopService).getItems(
                eq(1L), eq("premium"), eq("EMOTICON"), eq(true), eq(false), any());
    }

    @Test
    void updateSaleStatus_mapsForbiddenForRegularUser() throws Exception {
        when(adminShopService.updateSaleStatus(2L, 2L, false, "review"))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(put("/api/v1/admin/shop/items/{itemId}/sale-status", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleEnabled\":false,\"reason\":\"review\"}")
                        .with(user(regularUser))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.FORBIDDEN.getCode()));
    }

    @Test
    void updateSaleStatus_returnsUpdatedItemForSuperAdmin() throws Exception {
        when(adminShopService.updateSaleStatus(1L, 2L, false, "temporary review"))
                .thenReturn(response(false));

        mockMvc.perform(put("/api/v1/admin/shop/items/{itemId}/sale-status", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleEnabled\":false,\"reason\":\"temporary review\"}")
                        .with(user(superAdmin))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.itemId").value(2))
                .andExpect(jsonPath("$.data.isSaleEnabled").value(false))
                .andExpect(jsonPath("$.data.purchasable").value(false));
    }

    @Test
    void updateSaleStatus_rejectsBlankReason() throws Exception {
        mockMvc.perform(put("/api/v1/admin/shop/items/{itemId}/sale-status", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleEnabled\":false,\"reason\":\"   \"}")
                        .with(user(superAdmin))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(adminShopService, never()).updateSaleStatus(any(), any(), any(Boolean.class), any());
    }

    @Test
    void updateSaleStatus_rejectsMissingSaleEnabled() throws Exception {
        mockMvc.perform(put("/api/v1/admin/shop/items/{itemId}/sale-status", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"review\"}")
                        .with(user(superAdmin))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(adminShopService, never()).updateSaleStatus(any(), any(), any(Boolean.class), any());
    }

    @Test
    void updateSaleStatus_rejectsReasonLongerThanLimit() throws Exception {
        String reason = "a".repeat(501);

        mockMvc.perform(put("/api/v1/admin/shop/items/{itemId}/sale-status", 2L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleEnabled\":false,\"reason\":\"" + reason + "\"}")
                        .with(user(superAdmin))
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        verify(adminShopService, never()).updateSaleStatus(any(), any(), any(Boolean.class), any());
    }

    @Test
    void updateSaleStatus_mapsMissingItemToNotFound() throws Exception {
        when(adminShopService.updateSaleStatus(1L, 404L, false, "review"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(put("/api/v1/admin/shop/items/{itemId}/sale-status", 404L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleEnabled\":false,\"reason\":\"review\"}")
                        .with(user(superAdmin))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.NOT_FOUND.getCode()));
    }

    private CustomUserDetails userDetails(Long userId, String authority) {
        return new CustomUserDetails(
                userId,
                "user" + userId + "@example.com",
                "password",
                List.of(new SimpleGrantedAuthority(authority)));
    }

    private AdminShopItemResponse response(boolean saleEnabled) {
        return AdminShopItemResponse.builder()
                .itemId(2L)
                .itemName("Premium emoticon")
                .price(100)
                .itemType("EMOTICON")
                .targetId(10L)
                .isActive(true)
                .isSaleEnabled(saleEnabled)
                .purchasable(saleEnabled)
                .build();
    }
}
