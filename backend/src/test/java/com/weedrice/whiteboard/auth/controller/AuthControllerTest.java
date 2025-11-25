package com.weedrice.project_whiteboard.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.project_whiteboard.domain.auth.service.AuthService;
import com.weedrice.project_whiteboard.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider; // Required for SecurityConfig

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void signup_Success() throws Exception {
        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setLoginId("testuser");
        request.setPassword("password");
        request.setEmail("test@example.com");
        request.setDisplayName("Test User");

        given(authService.signup(anyString(), anyString(), anyString(), anyString()))
                .willReturn(1L);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.userId").value(1));
    }

    @Test
    @WithMockUser
    void login_Success() throws Exception {
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setLoginId("testuser");
        request.setPassword("password");

        given(authService.login(anyString(), anyString()))
                .willReturn("access-token");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }
}
