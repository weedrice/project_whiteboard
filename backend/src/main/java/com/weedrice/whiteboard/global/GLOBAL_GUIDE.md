# Global 패키지 가이드

`global` 패키지는 프로젝트 전반에서 공통적으로 사용되는 설정, 유틸리티, 예외 처리, 보안 등의 인프라스트럭처 코드를 포함합니다. 특정 비즈니스 도메인에 종속되지 않는 크로스 커팅 관심사(Cross-Cutting Concerns)를 담당합니다.

## 1. 패키지 구조

### 1.1 `common`
애플리케이션 전반에서 사용되는 공통 클래스와 전역 설정 기능을 포함합니다.
- **ApiResponse.java**: 모든 API 응답을 감싸는 표준 래퍼 클래스입니다. 성공/실패 여부, 데이터, 에러 메시지 등을 통일된 포맷으로 제공합니다.
- **entity/BaseTimeEntity.java**: JPA 엔티티들의 생성일시(`created_at`), 수정일시(`modified_at`)를 자동으로 관리하는 상위 엔티티입니다.
- **util/**: 유틸리티 클래스 모음 (예: `SecurityUtils` 등)
- **전역 설정 (Global Config)**: 시스템의 동적인 설정을 관리하는 `GlobalConfig` 도메인(Entity, Service, Controller)이 포함되어 있습니다.

### 1.2 `config`
Spring Framework 및 라이브러리 설정을 담당합니다.
- **SecurityConfig.java**: Spring Security 설정 (URL별 접근 권한, 필터 체인 등).
- **WebConfig.java**: CORS 설정, WebMvc 설정 등.
- **QuerydslConfig.java**: QueryDSL `JPAQueryFactory` 빈 등록.
- **OpenApiConfig.java**: Swagger/OpenAPI 문서 설정.

### 1.3 `exception`
전역 예외 처리 전략을 정의합니다.
- **ErrorCode.java**: 애플리케이션에서 발생하는 모든 에러 코드와 메시지를 정의한 Enum입니다.
- **BusinessException.java**: 비즈니스 로직에서 발생하는 예외의 기본 클래스입니다.
- **GlobalExceptionHandler.java**: `@ControllerAdvice`를 사용하여 애플리케이션 전역에서 발생하는 예외를 포착하고 표준 `ApiResponse` 포맷으로 변환합니다.

### 1.4 `security`
인증(Authentication) 및 인가(Authorization) 관련 구현체입니다.
- **JwtTokenProvider.java**: JWT 토큰 생성, 검증, 파싱을 담당합니다.
- **JwtAuthenticationFilter.java**: 요청 헤더에서 JWT를 추출하여 인증 정보를 SecurityContext에 저장하는 필터입니다.
- **CustomUserDetails.java**: Spring Security의 `UserDetails` 구현체로, 인증된 사용자의 정보를 담습니다.

### 1.5 `log`
시스템 로깅 및 감사 로그 처리를 담당합니다.
- AOP를 활용한 요청/응답 로깅 등을 수행할 수 있습니다.

## 2. 주요 개발 패턴

### API 응답 처리
모든 Controller는 반환 타입을 `ApiResponse<T>`로 감싸서 반환해야 합니다.
```java
return ApiResponse.success(data);
```

### 예외 처리
비즈니스 로직에서 예외 상황 발생 시 `BusinessException`을 발생시킵니다.
```java
throw new BusinessException(ErrorCode.USER_NOT_FOUND);
```
발생한 예외는 `GlobalExceptionHandler`에 의해 자동으로 4xx/5xx HTTP 상태 코드와 함께 적절한 에러 응답으로 변환됩니다.

### 메시지 처리 및 국제화 (i18n)
- **MessageSource**: Spring `MessageSource`를 사용하여 메시지를 중앙에서 관리합니다.
- **리소스 파일**: `src/main/resources/messages.properties` (기본/한국어) 및 `messages_en.properties` (영어)에 메시지를 정의합니다.
- **ErrorCode 사용**: `ErrorCode` Enum은 메시지 텍스트 대신 '메시지 키'를 가집니다 (예: `error.user.notFound`). 예외 핸들러가 이를 해석하여 클라이언트에게 현지화된 메시지를 반환합니다.
- **성공 메시지**: 컨트롤러에서 성공 메시지 반환 시 `MessageSource`를 주입받아 키를 통해 메시지를 조회해야 합니다.
- **Validation**: DTO의 Validation 어노테이션에는 `{key}` 형식을 사용하여 메시지 키를 지정합니다.

### 보안 및 인증
- **인증 필요 API**: Spring Security 설정에 따라 보호되며, 요청 헤더에 유효한 `Bearer` 토큰이 필요합니다.
- **현재 사용자 접근**: `SecurityContextHolder`를 직접 접근하거나, 컨트롤러 메서드 인자로 `@AuthenticationPrincipal CustomUserDetails userDetails`를 받아 현재 인증된 사용자의 ID(`userId`)를 획득할 수 있습니다.
- **슈퍼 관리자 권한**: `SecurityUtils.validateSuperAdminPermission()`을 호출하여 관리자 권한을 강제로 체크할 수 있습니다.
