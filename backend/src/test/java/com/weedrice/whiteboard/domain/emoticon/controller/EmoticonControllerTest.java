package com.weedrice.whiteboard.domain.emoticon.controller;

import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonMasterDto;
import com.weedrice.whiteboard.domain.emoticon.dto.EmoticonPurchaseStatusResponse;
import com.weedrice.whiteboard.domain.emoticon.service.EmoticonService;
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
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmoticonController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import({
        EmoticonControllerTest.TestSecurityConfig.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
@DisplayName("EmoticonController 테스트")
class EmoticonControllerTest {

    @org.springframework.boot.test.context.TestConfiguration
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class TestSecurityConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.web.SecurityFilterChain filterChain(org.springframework.security.config.annotation.web.builders.HttpSecurity http) throws Exception {
            http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmoticonService emoticonService;

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
        clearInvocations(emoticonService);
    }

    @Nested
    @DisplayName("이모티콘 목록/상세 조회 API")
    class GetEmoticons {

        @Test
        @DisplayName("이모티콘 목록 조회 성공 - latest 정렬")
        void getEmoticons_latest() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.getActiveEmoticons(any(), eq("latest"))).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons")
                            .param("sortBy", "latest")
                            .param("page", "0")
                            .param("size", "20")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content[0].emoticonId").value(1))
                    .andExpect(jsonPath("$.data.page").value(0));

            verify(emoticonService).getActiveEmoticons(any(), eq("latest"));
        }

        @Test
        @DisplayName("이모티콘 목록 조회 성공 - popular 정렬")
        void getEmoticons_popular() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.getActiveEmoticons(any(), eq("popular"))).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons")
                            .param("sortBy", "popular")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());

            verify(emoticonService).getActiveEmoticons(any(), eq("popular"));
        }

        @Test
        @DisplayName("이모티콘 목록 조회 성공 - oldest 정렬")
        void getEmoticons_oldest() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.getActiveEmoticons(any(), eq("oldest"))).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons")
                            .param("sortBy", "oldest")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());

            verify(emoticonService).getActiveEmoticons(any(), eq("oldest"));
        }

        @Test
        @DisplayName("이모티콘 목록 page/size를 정규화하고 sort 파라미터를 무시한다")
        void getEmoticons_normalizesPageableAndIgnoresClientSort() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(
                    List.of(EmoticonMasterDto.builder().emoticonId(1L).build()),
                    PageRequest.of(2, 100),
                    1);
            when(emoticonService.getActiveEmoticons(any(), eq("latest"))).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons")
                            .param("sortBy", "createdAt")
                            .param("page", "2")
                            .param("size", "500")
                            .param("sort", "purchaseCount,desc")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(emoticonService).getActiveEmoticons(pageableCaptor.capture(), eq("latest"));
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(2);
            assertThat(pageable.getPageSize()).isEqualTo(100);
            assertThat(pageable.getSort().isUnsorted()).isTrue();
        }

        @Test
        @DisplayName("이모티콘 목록 page 요청이 유효하지 않으면 거절한다")
        void getEmoticons_invalidPageRequest() throws Exception {
            mockMvc.perform(get("/api/v1/emoticons")
                            .param("page", "-1")
                            .param("size", "20")
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("인기 이모티콘 조회 성공")
        void getPopularEmoticons_success() throws Exception {
            List<EmoticonMasterDto> list = List.of(EmoticonMasterDto.builder().emoticonId(1L).build());
            when(emoticonService.getPopularEmoticons("daily")).thenReturn(list);

            mockMvc.perform(get("/api/v1/emoticons/popular")
                            .param("period", "daily")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data[0].emoticonId").value(1));

            verify(emoticonService).getPopularEmoticons("daily");
        }

        @Test
        @DisplayName("이모티콘 상세 조회 성공")
        void getEmoticonDetail_success() throws Exception {
            EmoticonMasterDto dto = EmoticonMasterDto.builder().emoticonId(1L).name("테스트 이모티콘").build();
            when(emoticonService.getEmoticonDetail(1L, null)).thenReturn(dto);

            mockMvc.perform(get("/api/v1/emoticons/{emoticonId}", 1L)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.emoticonId").value(1))
                    .andExpect(jsonPath("$.data.name").value("테스트 이모티콘"));

            verify(emoticonService).getEmoticonDetail(1L, null);
        }

        @Test
        @DisplayName("이모티콘 상세 조회 - 존재하지 않으면 404")
        void getEmoticonDetail_notFound() throws Exception {
            when(emoticonService.getEmoticonDetail(999L, null))
                    .thenThrow(new BusinessException(ErrorCode.EMOTICON_NOT_FOUND));

            mockMvc.perform(get("/api/v1/emoticons/{emoticonId}", 999L)
                            .with(csrf()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    @DisplayName("이모티콘 검색 API")
    class SearchEmoticons {

        @Test
        @DisplayName("통합 검색 성공")
        void searchAll_success() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.searchAll(anyString(), anyString(), any(), anyString())).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/search/all")
                            .param("keyword", "테스트")
                            .param("searchType", "ALL")
                            .param("sortBy", "latest")
                            .param("page", "0")
                            .param("size", "20")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());

            verify(emoticonService).searchAll(eq("테스트"), eq("ALL"), any(), eq("latest"));
        }

        @Test
        @DisplayName("통합 검색 page/size와 popular 정렬을 정규화한다")
        void searchAll_normalizesPageableAndPopularSortBy() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(
                    List.of(EmoticonMasterDto.builder().emoticonId(1L).build()),
                    PageRequest.of(1, 50),
                    1);
            when(emoticonService.searchAll(anyString(), anyString(), any(), eq("popular"))).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/search/all")
                            .param("keyword", "test")
                            .param("searchType", "NAME")
                            .param("sortBy", "POPULAR")
                            .param("page", "1")
                            .param("size", "50")
                            .param("sort", "name,asc")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(emoticonService).searchAll(eq("test"), eq("NAME"), pageableCaptor.capture(), eq("popular"));
            Pageable pageable = pageableCaptor.getValue();
            assertThat(pageable.getPageNumber()).isEqualTo(1);
            assertThat(pageable.getPageSize()).isEqualTo(50);
            assertThat(pageable.getSort().isUnsorted()).isTrue();
        }

        @Test
        @DisplayName("통합 검색 oldest 정렬을 정규화한다")
        void searchAll_normalizesOldestSortBy() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(
                    List.of(EmoticonMasterDto.builder().emoticonId(1L).build()),
                    PageRequest.of(0, 20),
                    1);
            when(emoticonService.searchAll(anyString(), anyString(), any(), eq("oldest"))).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/search/all")
                            .param("keyword", "test")
                            .param("searchType", "NAME")
                            .param("sortBy", "OLDEST")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(emoticonService).searchAll(eq("test"), eq("NAME"), any(), eq("oldest"));
        }

        @Test
        @DisplayName("태그로 검색 성공")
        void searchByTag_success() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.searchByTag(eq("웃음"), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/search/tag")
                            .param("tag", "웃음")
                            .param("page", "0")
                            .param("size", "20")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());

            verify(emoticonService).searchByTag(eq("웃음"), any());
        }

        @Test
        @DisplayName("키워드로 검색 성공")
        void searchByKeyword_success() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.searchByKeyword(eq("이모티콘"), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/search")
                            .param("keyword", "이모티콘")
                            .param("page", "0")
                            .param("size", "20")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());

            verify(emoticonService).searchByKeyword(eq("이모티콘"), any());
        }
    }

    @Nested
    @DisplayName("내 이모티콘 / 구매 API")
    class MyAndPurchase {

        @Test
        @DisplayName("내 이모티콘 목록 조회 성공")
        void getMyEmoticons_success() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.getMyEmoticons(eq(1L), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/my")
                            .param("page", "0")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());

            verify(emoticonService).getMyEmoticons(eq(1L), any());
        }

        @Test
        @DisplayName("인증 없이 내 이모티콘 목록 조회 시 401")
        void getMyEmoticons_unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/emoticons/my")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

            verify(emoticonService, never()).getMyEmoticons(anyLong(), any());
        }

        @Test
        @DisplayName("이모티콘 생성 성공")
        void createEmoticon_success() throws Exception {
            EmoticonMasterDto dto = EmoticonMasterDto.builder().emoticonId(1L).name("새 이모티콘").build();
            when(emoticonService.createEmoticon(eq(1L), any())).thenReturn(dto);

            mockMvc.perform(post("/api/v1/emoticons")
                            .contentType("application/json")
                            .content("{\"name\":\"새 이모티콘\",\"thumbnailFileId\":10,\"tags\":[\"테스트\"]}")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("새 이모티콘"));

            verify(emoticonService).createEmoticon(eq(1L), any());
        }

        @Test
        @DisplayName("이모티콘 수정 성공")
        void updateEmoticon_success() throws Exception {
            EmoticonMasterDto dto = EmoticonMasterDto.builder().emoticonId(1L).name("수정된 이름").build();
            when(emoticonService.updateEmoticon(eq(1L), eq(1L), any())).thenReturn(dto);

            mockMvc.perform(put("/api/v1/emoticons/{emoticonId}", 1L)
                            .contentType("application/json")
                            .content("{\"name\":\"수정된 이름\"}")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("수정된 이름"));

            verify(emoticonService).updateEmoticon(eq(1L), eq(1L), any());
        }

        @Test
        @DisplayName("이모티콘 삭제 성공")
        void deleteEmoticon_success() throws Exception {
            mockMvc.perform(delete("/api/v1/emoticons/{emoticonId}", 1L)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isNoContent())
                    .andExpect(jsonPath("$.success").value(true));

            verify(emoticonService).deleteEmoticon(eq(1L), eq(1L));
        }

        @Test
        @DisplayName("이미지 추가 성공")
        void addImage_success() throws Exception {
            EmoticonMasterDto dto = EmoticonMasterDto.builder().emoticonId(1L).build();
            when(emoticonService.addImage(eq(1L), eq(1L), eq(99L))).thenReturn(dto);

            mockMvc.perform(post("/api/v1/emoticons/{emoticonId}/images", 1L)
                            .contentType("application/json")
                            .content("{\"fileId\":99}")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(emoticonService).addImage(eq(1L), eq(1L), eq(99L));
        }

        @Test
        @DisplayName("이미지 추가 - fileId가 없으면 400")
        void addImage_fileIdRequired() throws Exception {
            mockMvc.perform(post("/api/v1/emoticons/{emoticonId}/images", 1L)
                            .contentType("application/json")
                            .content("{}")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("이미지 삭제 성공")
        void deleteImage_success() throws Exception {
            mockMvc.perform(delete("/api/v1/emoticons/images/{imageId}", 10L)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isNoContent())
                    .andExpect(jsonPath("$.success").value(true));

            verify(emoticonService).deleteImage(eq(1L), eq(10L));
        }

        @Test
        @DisplayName("이모티콘 구매 성공")
        void purchaseEmoticon_success() throws Exception {
            EmoticonMasterDto dto = EmoticonMasterDto.builder().emoticonId(1L).build();
            when(emoticonService.purchaseEmoticon(eq(1L), eq(1L))).thenReturn(dto);

            mockMvc.perform(post("/api/v1/emoticons/{emoticonId}/purchase", 1L)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(emoticonService).purchaseEmoticon(eq(1L), eq(1L));
        }

        @Test
        @DisplayName("구매한 이모티콘 목록 조회 성공")
        void getPurchasedEmoticons_success() throws Exception {
            Page<EmoticonMasterDto> page = new PageImpl<>(List.of(EmoticonMasterDto.builder().emoticonId(1L).build()), PageRequest.of(0, 20), 1);
            when(emoticonService.getPurchasedEmoticons(eq(1L), any())).thenReturn(page);

            mockMvc.perform(get("/api/v1/emoticons/purchased")
                            .param("page", "0")
                            .param("size", "20")
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(emoticonService).getPurchasedEmoticons(eq(1L), any());
        }

        @Test
        @DisplayName("인증 없이 구매 이모티콘 목록 조회 시 401")
        void getPurchasedEmoticons_unauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/emoticons/purchased")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false));

            verify(emoticonService, never()).getPurchasedEmoticons(anyLong(), any());
        }

        @Test
        @DisplayName("구매 여부 확인 - 인증 시 purchased true")
        void hasPurchased_authenticatedTrue() throws Exception {
            when(emoticonService.getPurchaseStatus(1L, 1L)).thenReturn(EmoticonPurchaseStatusResponse.builder()
                    .purchased(true)
                    .price(100)
                    .build());

            mockMvc.perform(get("/api/v1/emoticons/{emoticonId}/purchased", 1L)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.purchased").value(true))
                    .andExpect(jsonPath("$.data.price").value(100));

            verify(emoticonService).getPurchaseStatus(1L, 1L);
        }

        @Test
        @DisplayName("구매 여부 확인 - 인증 시 hasPurchased false이면 purchased false")
        void hasPurchased_authenticatedFalse() throws Exception {
            when(emoticonService.getPurchaseStatus(1L, 1L)).thenReturn(EmoticonPurchaseStatusResponse.builder()
                    .purchased(false)
                    .price(100)
                    .build());

            mockMvc.perform(get("/api/v1/emoticons/{emoticonId}/purchased", 1L)
                            .with(user(customUserDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.purchased").value(false))
                    .andExpect(jsonPath("$.data.price").value(100));

            verify(emoticonService).getPurchaseStatus(1L, 1L);
        }

        @Test
        @DisplayName("구매 여부 확인 - 비인증 시 purchased false, price 반환")
        void hasPurchased_anonymous() throws Exception {
            when(emoticonService.getPurchaseStatus(null, 1L)).thenReturn(EmoticonPurchaseStatusResponse.builder()
                    .purchased(false)
                    .price(100)
                    .build());

            mockMvc.perform(get("/api/v1/emoticons/{emoticonId}/purchased", 1L)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.purchased").value(false))
                    .andExpect(jsonPath("$.data.price").value(100));

            verify(emoticonService).getPurchaseStatus(null, 1L);
        }
    }
}
