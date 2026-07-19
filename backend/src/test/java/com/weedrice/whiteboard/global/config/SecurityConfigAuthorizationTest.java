package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint;
import com.weedrice.whiteboard.global.security.oauth.CustomOAuth2UserService;
import com.weedrice.whiteboard.global.security.oauth.OAuth2SuccessHandler;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        SecurityConfigAuthorizationTest.TestController.class,
        SecurityConfigAuthorizationTest.ActuatorTestController.class
},
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebConfig.class)
        })
@Import({
        SecurityConfig.class,
        JwtAuthenticationEntryPoint.class,
        SecurityConfigAuthorizationTest.TestController.class
})
class SecurityConfigAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    @MockitoBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockitoBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockitoBean
    private com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    @MockitoBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockitoBean
    private org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private org.springframework.security.oauth2.client.OAuth2AuthorizedClientService oAuth2AuthorizedClientService;

    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() throws Exception {
        userDetails = new CustomUserDetails(
                1L,
                "testuser",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        when(ipBlockInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(refererCheckInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("self API requires authentication")
    void selfApi_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me/settings"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me/posts"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me/comments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/mention-candidates").param("keyword", "ca"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/mention-candidates")
                        .param("keyword", "ca")
                        .with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("recent search API requires authentication")
    void recentSearchApi_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/search/recent"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/search/recent/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("personal emoticon list APIs require authentication")
    void personalEmoticonListApis_requireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/emoticons/my"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/emoticons/purchased"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("public search and user profile GET endpoints remain public")
    void publicGetEndpoints_remainPublic() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "test"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/search/posts").param("q", "test"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/search/semantic").param("q", "test"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/search/popular"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/1/posts"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/1/comments"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/1/badges"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/common-codes/POST_STATUS/details"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emoticons/1"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emoticons/1/purchased"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("public profile content remains accessible to authenticated users and administrators")
    void publicProfileContent_allowsAuthenticatedRoles() throws Exception {
        CustomUserDetails adminDetails = new CustomUserDetails(
                2L,
                "admin",
                "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        mockMvc.perform(get("/api/v1/users/1/posts").with(user(userDetails)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/1/comments").with(user(userDetails)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/1/posts").with(user(adminDetails)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/users/1/comments").with(user(adminDetails)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ad metric POST endpoints do not require authentication")
    void adMetricPostEndpoints_doNotRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/ads/1/impression"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/ads/1/click"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("agent register endpoint does not require authentication")
    void agentRegisterEndpoint_doesNotRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/agents/register"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("client error log POST endpoint does not require authentication")
    void clientErrorLogPostEndpoint_doesNotRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/logs/client"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("auth, code, health, and CSP report endpoints remain public")
    void configuredPublicEndpoints_remainPublic() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/codes/email"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .isNotIn(401, 403));

        mockMvc.perform(post("/api/v1/security/csp-report"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("unmatched API endpoints require authentication")
    void unmatchedApiEndpoint_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("authenticated self and recent search requests can reach controllers")
    void protectedEndpoints_allowAuthenticatedUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(user(userDetails)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/search/recent").with(user(userDetails)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emoticons/my").with(user(userDetails)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/emoticons/purchased").with(user(userDetails)))
                .andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/api/v1")
    static class TestController {

        @GetMapping("/users/me")
        String me() {
            return "me";
        }

        @GetMapping("/users/me/settings")
        String settings() {
            return "settings";
        }

        @GetMapping("/users/{userId}")
        String userProfile(@PathVariable Long userId) {
            return "user-" + userId;
        }

        @GetMapping("/users/mention-candidates")
        String mentionCandidates() {
            return "mention-candidates";
        }

        @GetMapping("/users/{userId}/posts")
        String userPosts(@PathVariable Long userId) {
            return "user-posts-" + userId;
        }

        @GetMapping("/users/{userId}/comments")
        String userComments(@PathVariable Long userId) {
            return "user-comments-" + userId;
        }

        @GetMapping("/users/{userId}/badges")
        String userBadges(@PathVariable Long userId) {
            return "user-badges-" + userId;
        }

        @GetMapping("/search")
        String search() {
            return "search";
        }

        @GetMapping("/search/posts")
        String searchPosts() {
            return "posts";
        }

        @GetMapping("/search/semantic")
        String semanticSearch() {
            return "semantic";
        }

        @GetMapping("/search/popular")
        String popular() {
            return "popular";
        }

        @GetMapping("/search/recent")
        String recent() {
            return "recent";
        }

        @GetMapping("/search/recent/{logId}")
        String recentItem(@PathVariable Long logId) {
            return "recent-" + logId;
        }

        @GetMapping("/common-codes/{typeCode}/details")
        String commonCodeDetails(@PathVariable String typeCode) {
            return "details-" + typeCode;
        }

        @GetMapping("/emoticons/my")
        String myEmoticons() {
            return "my-emoticons";
        }

        @GetMapping("/emoticons/purchased")
        String purchasedEmoticons() {
            return "purchased-emoticons";
        }

        @GetMapping("/emoticons/{emoticonId}")
        String emoticonDetail(@PathVariable Long emoticonId) {
            return "emoticon-" + emoticonId;
        }

        @GetMapping("/emoticons/{emoticonId}/purchased")
        String emoticonPurchaseStatus(@PathVariable Long emoticonId) {
            return "emoticon-purchased-" + emoticonId;
        }

        @PostMapping("/ads/{adId}/impression")
        String adImpression(@PathVariable Long adId) {
            return "ad-impression-" + adId;
        }

        @PostMapping("/ads/{adId}/click")
        String adClick(@PathVariable Long adId) {
            return "ad-click-" + adId;
        }

        @PostMapping("/agents/register")
        String agentRegister() {
            return "agent-register";
        }

        @PostMapping("/logs/client")
        String clientErrorLog() {
            return "client-error-log";
        }

        @PostMapping("/auth/login")
        String authLogin() {
            return "auth-login";
        }

        @PostMapping("/codes/email")
        String codeEmail() {
            return "code-email";
        }

        @PostMapping("/security/csp-report")
        String cspReport() {
            return "csp-report";
        }
    }

    @RestController
    static class ActuatorTestController {

        @GetMapping("/actuator/health")
        String health() {
            return "health";
        }
    }
}
