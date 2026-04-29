package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.service.FileDownloadService;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FileController.class,
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.context.annotation.Import({FileControllerTest.TestSecurityConfig.class, com.weedrice.whiteboard.global.exception.GlobalExceptionHandler.class})
class FileControllerTest {

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

    @MockBean
    private FileService fileService;

    @MockBean
    private FileDownloadService fileDownloadService;

    @MockBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private com.weedrice.whiteboard.global.security.JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @MockBean
    private com.weedrice.whiteboard.domain.admin.interceptor.IpBlockInterceptor ipBlockInterceptor;

    @MockBean
    private org.springframework.data.jpa.mapping.JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @MockBean
    private com.weedrice.whiteboard.global.security.RefererCheckInterceptor refererCheckInterceptor;

    @MockBean
    private com.weedrice.whiteboard.global.ratelimit.RateLimitInterceptor rateLimitInterceptor;

    @MockBean
    private org.springframework.context.MessageSource messageSource;

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
    @DisplayName("파일 업로드 성공")
    void uploadFile_returnsSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        FileUploadResponse response = FileUploadResponse.builder().build();
        when(fileService.uploadFile(any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .with(user(customUserDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("간단 파일 업로드 성공")
    void uploadSimple_returnsSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", "test content".getBytes());
        FileSimpleResponse response = FileSimpleResponse.builder().build();
        when(fileService.uploadSimpleFile(any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(file)
                        .with(user(customUserDetails)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("파일 다운로드 성공")
    void downloadFile_returnsSuccess() throws Exception {
        Long fileId = 1L;
        when(fileDownloadService.downloadFile(eq(fileId), isNull())).thenReturn(downloadResponse(
                "text/plain",
                ContentDisposition.attachment().filename("test.txt", StandardCharsets.UTF_8).build()));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("인증 다운로드는 조회자 ID를 서비스에 전달한다")
    void downloadFile_authenticatedPassesViewerUserId() throws Exception {
        Long fileId = 3L;
        when(fileDownloadService.downloadFile(eq(fileId), eq(1L))).thenReturn(downloadResponse(
                "text/plain",
                ContentDisposition.attachment().filename("test.txt", StandardCharsets.UTF_8).build()));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId)
                        .with(user(customUserDetails)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("SVG 파일은 inline 미리보기 없이 attachment로 응답")
    void downloadFile_svgServedAsAttachment() throws Exception {
        Long fileId = 2L;
        when(fileDownloadService.downloadFile(eq(fileId), isNull())).thenReturn(downloadResponse(
                "image/svg+xml",
                ContentDisposition.attachment().filename("vector.svg", StandardCharsets.UTF_8).build()));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("파일 다운로드 실패 - 파일 없음")
    void downloadFile_notFound() throws Exception {
        Long fileId = 99L;
        when(fileDownloadService.downloadFile(fileId, null)).thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("파일 업로드 실패 - 파일 비어있음")
    void uploadFile_empty() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", "text/plain", new byte[0]);
        when(fileService.uploadFile(any(), any())).thenThrow(new BusinessException(ErrorCode.FILE_EMPTY));

        mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .with(user(customUserDetails)))
                .andExpect(status().isBadRequest());
    }

    private FileDownloadResponse downloadResponse(String contentType, ContentDisposition contentDisposition) {
        return new FileDownloadResponse(
                new ByteArrayResource("test content".getBytes()),
                MediaType.parseMediaType(contentType),
                contentDisposition);
    }
}
