package com.weedrice.whiteboard.global.config;

import com.weedrice.whiteboard.global.security.JwtAuthenticationFilter;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**", "/api/v1/codes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/boards", "/api/v1/boards/{boardId}", "/api/v1/boards/{boardId}/categories").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/posts/{postId}", "/api/v1/posts/{postId}/versions").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/boards/{boardId}/posts", "/api/v1/comments/{commentId}/replies").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/tags", "/api/v1/tags/{tagId}/posts").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/search", "/api/v1/search/popular").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shop/items", "/api/v1/shop/items/{itemId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ads").permitAll()
                        .requestMatchers("/api/v1/users/{userId}").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
