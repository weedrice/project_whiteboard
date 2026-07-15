package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadDiscardResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.service.FileDownloadService;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.file.service.FileUploadDiscardService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {FileController.class, LegacyFileController.class},
    excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.WebConfig.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.weedrice.whiteboard.global.config.SecurityConfig.class)
    })
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.context.annotation.Import({
        FileControllerTest.TestSecurityConfig.class,
        com.weedrice.whiteboard.global.exception.GlobalExceptionHandler.class,
        CurrentUserIdWebMvcConfig.class,
        CurrentUserIdArgumentResolver.class
})
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

    @MockitoBean
    private FileService fileService;

    @MockitoBean
    private FileDownloadService fileDownloadService;

    @MockitoBean
    private FileUploadDiscardService fileUploadDiscardService;

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

    @MockitoBean
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
    @DisplayName("임시 업로드 파일 폐기 성공")
    void discardTemporaryUploads_returnsDiscardedCount() throws Exception {
        when(fileUploadDiscardService.discardTemporaryUploads(1L, java.util.List.of(3L, 3L, 4L)))
                .thenReturn(new FileUploadDiscardResponse(2));

        mockMvc.perform(post("/api/v1/files/uploads/discard")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[3,3,4]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.discardedCount").value(2));
    }

    @Test
    @DisplayName("임시 업로드 파일 폐기는 빈 목록을 거부한다")
    void discardTemporaryUploads_rejectsEmptyFileIds() throws Exception {
        mockMvc.perform(post("/api/v1/files/uploads/discard")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileUploadDiscardService);
    }

    @Test
    @DisplayName("임시 업로드 파일 폐기는 101개를 초과한 목록을 거부한다")
    void discardTemporaryUploads_rejectsMoreThan101FileIds() throws Exception {
        String fileIds = java.util.stream.LongStream.rangeClosed(1, 102)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));

        mockMvc.perform(post("/api/v1/files/uploads/discard")
                        .with(user(customUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileIds\":[" + fileIds + "]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(fileUploadDiscardService);
    }

    @Test
    @DisplayName("파일 다운로드 성공")
    void downloadFile_returnsSuccess() throws Exception {
        Long fileId = 1L;
        when(fileDownloadService.downloadFile(eq(fileId), isNull())).thenReturn(downloadResponse(
                "test.txt",
                "text/plain"));

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
                "test.txt",
                "text/plain"));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId)
                        .with(user(customUserDetails)))
                .andExpect(status().isOk());
    }

    @Test
    void downloadVariantFile_passesVariantTypeAndViewerUserId() throws Exception {
        Long fileId = 3L;
        when(fileDownloadService.downloadVariantFile(eq(fileId), eq(FileVariantType.THUMBNAIL), eq(1L)))
                .thenReturn(downloadResponse("image.png", "image/png"));

        mockMvc.perform(get("/api/v1/files/{fileId}/variants/{variantType}", fileId, "thumbnail")
                        .with(user(customUserDetails)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")));
    }

    @Test
    @DisplayName("기존 /files 경로 다운로드를 허용한다")
    void downloadFile_legacyPathReturnsSuccess() throws Exception {
        Long fileId = 4L;
        when(fileDownloadService.downloadFile(eq(fileId), isNull())).thenReturn(downloadResponse(
                "emoticon.png",
                "image/png"));

        mockMvc.perform(get("/files/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void downloadFile_matchingEmoticonEntityTagReturns304WithoutOpeningStream() throws Exception {
        Long fileId = 5L;
        AtomicInteger streamOpenCount = new AtomicInteger();
        when(fileDownloadService.downloadFile(eq(fileId), isNull())).thenReturn(new FileDownloadResponse(
                () -> {
                    streamOpenCount.incrementAndGet();
                    return new ByteArrayInputStream("test content".getBytes());
                },
                "emoticon.png",
                "image/png",
                true,
                "etag-value"));

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId)
                        .header("If-None-Match", "\"etag-value\""))
                .andExpect(status().isNotModified())
                .andExpect(header().string("Cache-Control", "max-age=600, private"))
                .andExpect(header().string("ETag", "\"etag-value\""));

        assertThat(streamOpenCount).hasValue(0);
    }

    @Test
    @DisplayName("SVG 파일은 inline 미리보기 없이 attachment로 응답")
    void downloadFile_svgServedAsAttachment() throws Exception {
        Long fileId = 2L;
        when(fileDownloadService.downloadFile(eq(fileId), isNull())).thenReturn(downloadResponse(
                "vector.svg",
                "image/svg+xml"));

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

    private FileDownloadResponse downloadResponse(String originalName, String contentType) {
        return new FileDownloadResponse(
                new ByteArrayInputStream("test content".getBytes()),
                originalName,
                contentType);
    }
}
