package com.weedrice.whiteboard.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI (Swagger) 설정
 * 
 * API 문서화를 위한 설정을 정의합니다.
 * - API 버전: v1
 * - 인증: JWT Bearer Token
 * - 공통 응답 스키마 정의
 */
@Configuration
public class OpenApiConfig {

    private static final String API_VERSION = "v1";
    private static final String API_TITLE = "NoviIs Whiteboard API";
    private static final String API_DESCRIPTION = """
            NoviIs 프로젝트의 백엔드 API 문서입니다.
            
            ## 인증
            대부분의 API는 JWT 토큰 인증이 필요합니다. `/api/v1/auth/login` 또는 `/api/v1/auth/signup`을 통해 토큰을 발급받을 수 있습니다.
            
            ## 응답 형식
            모든 API는 다음 형식으로 응답합니다:
            - 성공: `{ "success": true, "data": {...} }`
            - 실패: `{ "success": false, "error": { "code": "...", "message": "..." } }`
            
            ## 에러 코드
            각 에러는 고유한 코드를 가지며, 자세한 내용은 에러 코드 섹션을 참조하세요.
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(createInfo())
                .servers(createServers())
                .tags(createTags())
                .components(createComponents())
                // 기본 보안 요구사항은 제거 (각 엔드포인트에서 선택적으로 적용)
                ;
    }

    private Info createInfo() {
        return new Info()
                .title(API_TITLE)
                .version(API_VERSION)
                .description(API_DESCRIPTION)
                .contact(new Contact()
                        .name("NoviIs Team")
                        .email("support@noviis.kr"))
                .license(new License()
                        .name("Proprietary")
                        .url("https://noviis.kr"));
    }

    private List<Server> createServers() {
        List<Server> servers = new ArrayList<>();
        servers.add(new Server()
                .url("http://localhost:8080")
                .description("로컬 개발 서버"));
        servers.add(new Server()
                .url("https://api.noviis.kr")
                .description("프로덕션 서버"));
        return servers;
    }

    private List<Tag> createTags() {
        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag().name("인증").description("회원가입, 로그인, 토큰 갱신 등 인증 관련 API"));
        tags.add(new Tag().name("사용자").description("사용자 정보 조회 및 관리 API"));
        tags.add(new Tag().name("게시판").description("게시판 조회 및 관리 API"));
        tags.add(new Tag().name("게시글").description("게시글 작성, 수정, 삭제, 조회 API"));
        tags.add(new Tag().name("댓글").description("댓글 작성, 수정, 삭제, 조회 API"));
        tags.add(new Tag().name("태그").description("태그 관련 API"));
        tags.add(new Tag().name("검색").description("통합 검색 API"));
        tags.add(new Tag().name("신고").description("신고 관련 API"));
        tags.add(new Tag().name("관리자").description("관리자 전용 API"));
        tags.add(new Tag().name("공통").description("공통 코드, 설정 등 공통 API"));
        return tags;
    }

    private Components createComponents() {
        Components components = new Components();
        
        // JWT Security Scheme
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("JWT 토큰을 'Bearer {token}' 형식으로 전달하세요.");
        components.addSecuritySchemes("bearerAuth", securityScheme);

        // 공통 응답 스키마는 각 컨트롤러에서 @ApiResponse로 정의
        return components;
    }

    /**
     * 공통 에러 응답 생성 헬퍼 메서드
     * 컨트롤러에서 재사용 가능
     */
    public static ApiResponses createCommonErrorResponses() {
        ApiResponses responses = new ApiResponses();
        
        // 400 Bad Request
        responses.addApiResponse("400", new ApiResponse()
                .description("잘못된 요청 (Validation 오류 등)")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        
        // 401 Unauthorized
        responses.addApiResponse("401", new ApiResponse()
                .description("인증 실패 (토큰이 없거나 만료됨)")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        
        // 403 Forbidden
        responses.addApiResponse("403", new ApiResponse()
                .description("권한 없음")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        
        // 404 Not Found
        responses.addApiResponse("404", new ApiResponse()
                .description("리소스를 찾을 수 없음")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        
        // 500 Internal Server Error
        responses.addApiResponse("500", new ApiResponse()
                .description("서버 내부 오류")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
        
        return responses;
    }
}
