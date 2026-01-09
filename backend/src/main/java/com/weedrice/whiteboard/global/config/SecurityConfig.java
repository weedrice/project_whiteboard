package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint;

import com.weedrice.whiteboard.global.security.oauth.CustomOAuth2UserService;
import com.weedrice.whiteboard.global.security.oauth.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2SuccessHandler oAuth2SuccessHandler;

        @Value("${app.frontend-url}")
        private String frontendUrl;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(AbstractHttpConfigurer::disable)
                                .exceptionHandling(exceptionHandling -> exceptionHandling
                                                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/v1/users/me/**").authenticated()
                                                .requestMatchers("/api/v1/auth/**", "/api/v1/codes/**",
                                                                "/actuator/health")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/v1/files/**",
                                                                "/api/v1/boards/**", // /boards 로 시작하는 모든 GET 요청 허용
                                                                "/api/v1/posts/**", // /posts 로 시작하는 모든 GET 요청 허용 (상세 조회
                                                                                    // 등)
                                                                "/api/v1/comments/**", // /comments 로 시작하는 모든 GET 요청 허용
                                                                                       // (답글 조회 등)
                                                                "/api/v1/tags/**",
                                                                "/api/v1/search/**",
                                                                "/api/v1/shop/items/**",
                                                                "/api/v1/ads",
                                                                "/api/v1/users/**",
                                                                "/api/v1/configs/public") // Public Configs
                                                .permitAll()
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/v1/ads/{adId}/click", // 광고 클릭 기록 허용
                                                                "/api/v1/posts/*/view" // 게시글 조회수 증가 허용
                                                ).permitAll()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2SuccessHandler))
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                
                // 허용할 Origin 설정 (프론트엔드 URL)
                configuration.setAllowedOrigins(Collections.singletonList(frontendUrl));
                
                // 허용할 HTTP 메서드 (프론트엔드에서 사용하는 모든 메서드)
                configuration.setAllowedMethods(
                                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                
                // 허용할 헤더 (프론트엔드에서 실제로 사용하는 헤더만 명시)
                // - Authorization: JWT 토큰 인증
                // - Content-Type: JSON 및 multipart/form-data 요청
                // - Accept: 응답 형식 지정 (선택적)
                configuration.setAllowedHeaders(Arrays.asList(
                                "Authorization",
                                "Content-Type",
                                "Accept"
                ));
                
                // 클라이언트가 접근할 수 있는 응답 헤더
                // - 필요시 커스텀 헤더 추가 가능 (예: X-Total-Count 등)
                configuration.setExposedHeaders(Arrays.asList(
                                "Content-Type",
                                "Content-Length"
                ));
                
                // 인증 정보(쿠키, Authorization 헤더) 허용
                configuration.setAllowCredentials(true);
                
                // Preflight 요청 캐시 시간 (초 단위)
                // 브라우저가 OPTIONS 요청을 캐시하는 시간
                configuration.setMaxAge(3600L); // 1시간

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
