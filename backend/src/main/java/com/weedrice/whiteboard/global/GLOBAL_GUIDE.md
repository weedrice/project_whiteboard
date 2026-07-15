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

### 1.3 `email`
이메일 발송을 담당합니다. Google Workspace SMTP (앱 비밀번호 방식)를 사용합니다.
- **EmailService.java**: 이메일 발송 인터페이스.
- **SmtpEmailService.java**: `JavaMailSender`를 사용한 SMTP 이메일 서비스 구현체. `spring.mail.*` 설정을 사용합니다. `@Async`로 비동기 처리되어 API 응답을 블로킹하지 않습니다. 발송 실패 시 서버 로그에 기록됩니다.
- **사용처**: 회원가입 이메일 인증 코드 발송 (`VerificationCodeService`), 비밀번호 재설정 링크 발송 (`AuthService`).

### 1.4 `exception`
전역 예외 처리 전략을 정의합니다.
- **ErrorCode.java**: 애플리케이션에서 발생하는 모든 에러 코드와 메시지를 정의한 Enum입니다.
- **BusinessException.java**: 비즈니스 로직에서 발생하는 예외의 기본 클래스입니다.
- **GlobalExceptionHandler.java**: `@ControllerAdvice`를 사용하여 애플리케이션 전역에서 발생하는 예외를 포착하고 표준 `ApiResponse` 포맷으로 변환합니다.

### 1.5 `security`
인증(Authentication) 및 인가(Authorization) 관련 구현체입니다.
- **JwtTokenProvider.java**: JWT 토큰 생성, 검증, 파싱을 담당합니다.
- **JwtAuthenticationFilter.java**: 요청 헤더에서 JWT를 추출하여 인증 정보를 SecurityContext에 저장하는 필터입니다. 토큰은 `Authorization: Bearer` 헤더에서 추출하며, SSE 스트림 엔드포인트(`/stream`)에서만 query parameter(`?token=`)로도 허용합니다(EventSource API는 커스텀 헤더 설정 불가).
- **CustomUserDetails.java**: Spring Security의 `UserDetails` 구현체로, 인증된 사용자의 정보를 담습니다.

### 1.6 `log`
시스템 로깅, 감사 로그, 에러 로그 처리를 담당합니다.
- AOP를 활용한 요청/응답 로깅 등을 수행할 수 있습니다.

#### 에러 로그 시스템
에러 로그를 DB에 비동기적으로 저장하고 관리자 페이지에서 조회/확인 처리할 수 있는 시스템입니다.

- **entity/ErrorLog.java**: 에러 로그 엔티티. `BaseTimeEntity`를 상속하여 `created_at`, `modified_at`을 자동 관리합니다. Builder 패턴을 사용합니다.
- **repository/ErrorLogRepository.java**: `JpaRepository` + `ErrorLogRepositoryCustom`을 상속하여 기본 CRUD와 QueryDSL 기반 동적 검색을 지원합니다.
- **repository/ErrorLogRepositoryCustom.java**: QueryDSL 기반 에러 로그 동적 검색 인터페이스.
- **repository/ErrorLogRepositoryCustomImpl.java**: QueryDSL 기반 검색 구현. 에러 타입, 코드, HTTP 상태, 확인 여부, 날짜 범위, URI 등 다양한 필터를 지원합니다.
- **service/ErrorLogService.java**: 에러 로그 비즈니스 로직. 저장은 `@Async("observabilityTaskExecutor")` + `@Transactional(propagation = REQUIRES_NEW)` 비동기 방식. 메시지/URI/UA 길이 초과 시 자동 잘라냅니다.
- **controller/ErrorLogController.java**: 관리자용 에러 로그 API (`/api/v1/admin/error-logs`). `@PreAuthorize("hasRole('SUPER_ADMIN')")` 적용.
  - `GET /`: 에러 로그 목록 조회 (검색/필터/페이징)
  - `GET /{errorLogId}`: 에러 로그 상세 조회
  - `PUT /{errorLogId}/resolve`: 확인 처리 (메모 선택)
  - `GET /stats`: 에러 로그 통계
- **dto/**: `ErrorLogSearchRequest`, `ErrorLogResponse`, `ErrorLogResolveRequest`, `ErrorLogStatsResponse`

#### GlobalExceptionHandler 연동
`GlobalExceptionHandler`에서 `BusinessException`, `MethodArgumentNotValidException`, `AccessDeniedException`, `Exception` 등 모든 예외 처리 시 `ErrorLogService.saveErrorLog()`를 호출하여 에러를 DB에 비동기 저장합니다. 5xx 에러는 스택 트레이스도 함께 저장됩니다. 에러 로그 저장 실패 시 원래 에러 응답에 영향을 주지 않도록 try-catch로 보호됩니다.

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
- **현재 사용자 접근**: 컨트롤러 메서드 인자로 `@CurrentUserId Long userId`를 받아 현재 인증된 사용자의 ID를 획득합니다. 공개 API에서 선택적 인증이 필요하면 `@CurrentUserId(required = false) Long userId`를 사용합니다.
- **슈퍼 관리자 권한**: `SecurityUtils.validateSuperAdminPermission()`을 호출하여 관리자 권한을 강제로 체크할 수 있습니다.
