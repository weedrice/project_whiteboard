package com.weedrice.whiteboard.domain.shop.controller;

import com.weedrice.whiteboard.domain.shop.dto.PurchaseHistoryResponse;
import com.weedrice.whiteboard.domain.shop.dto.ShopItemResponse;
import com.weedrice.whiteboard.domain.shop.entity.ShopItem;
import com.weedrice.whiteboard.domain.shop.service.ShopService;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShopController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import({
        ShopControllerTest.TestSecurityConfig.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
@DisplayName("ShopController 테스트")
class ShopControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.web.SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        (request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/shop/items/**").permitAll()
                        .anyRequest().authenticated());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopService shopService;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockitoBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    private CustomUserDetails customUserDetails;

    @BeforeEach
    void setUp() throws Exception {
        customUserDetails = new CustomUserDetails(1L, "test@example.com", "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        
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
    @DisplayName("상점 아이템 조회 성공")
    void getShopItems_returnsSuccess() throws Exception {
        // given
        ShopItemResponse response = ShopItemResponse.builder().build();
        when(shopService.getShopItems(any(), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/shop/items")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("아이템 구매 성공")
    void purchaseItem_returnsSuccess() throws Exception {
        // given
        when(shopService.purchaseItem(any(), eq(1L))).thenReturn(1L);

        // when & then
        mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 1L)
                        .with(user(customUserDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1L));
    }

    @Test
    @DisplayName("내 구매 내역 조회 성공")
    void getMyPurchaseHistories_returnsSuccess() throws Exception {
        // given
        PurchaseHistoryResponse response = PurchaseHistoryResponse.builder().build();
        when(shopService.getPurchaseHistories(any(), any())).thenReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/shop/me/purchases")
                        .param("page", "0")
                        .param("size", "20")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Nested
    @DisplayName("EMOTICON 아이템 조회 API")
    class GetEmoticonItems {

        @Test
        @DisplayName("EMOTICON 타입 필터로 아이템 조회 성공")
        void getShopItems_withEmoticonType() throws Exception {
            // given
            ShopItemResponse response = ShopItemResponse.builder().build();
            when(shopService.getShopItems(eq("EMOTICON"), any())).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shop/items")
                            .param("itemType", "EMOTICON")
                            .param("page", "0")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(shopService, atLeastOnce()).getShopItems(eq("EMOTICON"), any());
        }

        @Test
        @DisplayName("itemType 파라미터 없이 전체 아이템 조회")
        void getShopItems_withoutItemType() throws Exception {
            // given
            ShopItemResponse response = ShopItemResponse.builder().build();
            when(shopService.getShopItems(isNull(), any())).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shop/items")
                            .param("page", "0")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(shopService, atLeastOnce()).getShopItems(isNull(), any());
        }

        @Test
        @DisplayName("DECORATION 타입 필터로 아이템 조회")
        void getShopItems_withDecorationType() throws Exception {
            // given
            ShopItemResponse response = ShopItemResponse.builder().build();
            when(shopService.getShopItems(eq("DECORATION"), any())).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shop/items")
                            .param("itemType", "DECORATION")
                            .param("page", "0")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(shopService).getShopItems(eq("DECORATION"), any());
        }

        @Test
        @DisplayName("지원하지 않는 itemType은 잘못된 요청으로 응답")
        void getShopItems_unsupportedType_returnsBadRequest() throws Exception {
            when(shopService.getShopItems(eq("BADGE"), any()))
                    .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT_VALUE));

            mockMvc.perform(get("/api/v1/shop/items")
                            .param("itemType", "BADGE")
                            .param("page", "0")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
        }

        @Test
        @DisplayName("기본 페이징 값으로 아이템 조회")
        void getShopItems_withDefaultPaging() throws Exception {
            // given
            ShopItemResponse response = ShopItemResponse.builder().build();
            when(shopService.getShopItems(any(), any())).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shop/items")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("EMOTICON 아이템 구매 API")
    class PurchaseEmoticonItem {

        @Test
        @DisplayName("EMOTICON 아이템 구매 성공")
        void purchaseEmoticonItem_success() throws Exception {
            // given
            Long emoticonItemId = 5L;
            when(shopService.purchaseItem(eq(1L), eq(emoticonItemId))).thenReturn(100L);

            // when & then
            mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", emoticonItemId)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data").value(100L));

            verify(shopService).purchaseItem(1L, emoticonItemId);
        }

        @Test
        @DisplayName("인증 없이 EMOTICON 구매 시 401 에러")
        void purchaseEmoticonItem_unauthorized() throws Exception {
            // when & then
            mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", 1L)
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("존재하지 않는 EMOTICON 아이템 구매 시 에러")
        void purchaseEmoticonItem_notFound() throws Exception {
            // given
            Long nonExistentItemId = 999L;
            when(shopService.purchaseItem(eq(1L), eq(nonExistentItemId)))
                    .thenThrow(new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE));

            // when & then
            mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", nonExistentItemId)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("포인트 부족으로 EMOTICON 구매 실패")
        void purchaseEmoticonItem_insufficientPoints() throws Exception {
            // given
            Long itemId = 5L;
            when(shopService.purchaseItem(eq(1L), eq(itemId)))
                    .thenThrow(new BusinessException(ErrorCode.INSUFFICIENT_POINTS));

            // when & then
            mockMvc.perform(post("/api/v1/shop/items/{itemId}/purchase", itemId)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("EMOTICON 구매 이력 조회 API")
    class GetEmoticonPurchaseHistory {

        @Test
        @DisplayName("EMOTICON 포함 구매 이력 조회 성공")
        void getMyPurchaseHistories_withEmoticons() throws Exception {
            // given
            PurchaseHistoryResponse response = PurchaseHistoryResponse.builder()
                    .content(List.of(PurchaseHistoryResponse.PurchaseSummary.builder()
                            .purchaseId(1L)
                            .item(PurchaseHistoryResponse.ItemInfo.builder()
                                    .itemId(5L)
                                    .itemName("Premium emoticon")
                                    .imageUrl("https://example.com/emoticon.png")
                                    .build())
                            .price(100)
                            .build()))
                    .page(0)
                    .size(10)
                    .build();
            when(shopService.getPurchaseHistories(eq(1L), any())).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shop/me/purchases")
                            .param("page", "0")
                            .param("size", "10")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].item.imageUrl")
                            .value("https://example.com/emoticon.png"));

            verify(shopService, atLeastOnce()).getPurchaseHistories(eq(1L), any());
        }

        @Test
        @DisplayName("인증 없이 구매 이력 조회 시 401 에러")
        void getMyPurchaseHistories_unauthorized() throws Exception {
            // when & then
            mockMvc.perform(get("/api/v1/shop/me/purchases")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("사용자를 찾을 수 없을 때 에러")
        void getMyPurchaseHistories_userNotFound() throws Exception {
            // given
            when(shopService.getPurchaseHistories(eq(1L), any()))
                    .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/v1/shop/me/purchases")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("페이징 파라미터 테스트")
    class PagingParameterTest {

        @Test
        @DisplayName("커스텀 페이지 사이즈로 EMOTICON 조회")
        void getEmoticonItems_customPageSize() throws Exception {
            // given
            ShopItemResponse response = ShopItemResponse.builder().build();
            when(shopService.getShopItems(eq("EMOTICON"), any())).thenReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/shop/items")
                            .param("itemType", "EMOTICON")
                            .param("page", "2")
                            .param("size", "5")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("대용량 페이지 사이즈로 조회")
        void getEmoticonItems_largePageSize() throws Exception {
            // given
            ShopItemResponse response = ShopItemResponse.builder().build();
            when(shopService.getShopItems(eq("EMOTICON"), any())).thenReturn(response);
            org.mockito.Mockito.clearInvocations(shopService);

            // when & then
            mockMvc.perform(get("/api/v1/shop/items")
                            .param("itemType", "EMOTICON")
                            .param("page", "0")
                            .param("size", "100")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            org.mockito.ArgumentCaptor<Pageable> captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
            verify(shopService).getShopItems(eq("EMOTICON"), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("잘못된 페이지 파라미터는 400으로 처리")
        void getShopItems_invalidPage_returnsBadRequest() throws Exception {
            org.mockito.Mockito.clearInvocations(shopService);

            mockMvc.perform(get("/api/v1/shop/items")
                            .param("page", "-1")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(shopService, never()).getShopItems(any(), any());
        }

        @Test
        @DisplayName("구매 이력 조회의 잘못된 페이지 크기는 400으로 처리")
        void getMyPurchaseHistories_invalidSize_returnsBadRequest() throws Exception {
            org.mockito.Mockito.clearInvocations(shopService);

            mockMvc.perform(get("/api/v1/shop/me/purchases")
                            .param("page", "0")
                            .param("size", "0")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());

            verify(shopService, never()).getPurchaseHistories(any(), any());
        }
    }
}
