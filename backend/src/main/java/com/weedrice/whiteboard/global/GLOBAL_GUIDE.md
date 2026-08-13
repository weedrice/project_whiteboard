# Global 패키지 가이드

`global` 패키지는 프로젝트 전반에서 공통적으로 사용되는 설정, 유틸리티, 예외 처리, 보안 등의 인프라스트럭처 코드를 포함합니다. 특정 비즈니스 도메인에 종속되지 않는 크로스 커팅 관심사(Cross-Cutting Concerns)를 담당합니다.

## 1. 패키지 구조

### 1.1 `common`
애플리케이션 전반에서 사용되는 공통 클래스와 전역 설정 기능을 포함합니다.
- **ApiResponse.java**: JSON 업무 API 응답을 감싸는 표준 래퍼 클래스입니다. 성공/실패 여부, 데이터, 에러 메시지 등을 통일된 포맷으로 제공합니다. 파일 스트리밍과 `204 No Content` 응답은 이 래퍼를 사용하지 않습니다.
- **entity/BaseTimeEntity.java**: JPA 엔티티들의 생성일시(`created_at`), 수정일시(`modified_at`)를 자동으로 관리하는 상위 엔티티입니다.
- **util/**: 페이지 요청, 클라이언트 정보 등 공통 유틸리티 클래스 모음
- **전역 설정 (Global Config)**: 시스템의 동적인 설정을 관리하는 `GlobalConfig` 도메인(Entity, Service, Controller)이 포함되어 있습니다.

### 1.2 `config`
Spring Framework 및 라이브러리 설정을 담당합니다.
- **SecurityConfig.java**: URL별 접근 권한, 보안 필터 체인과 CORS 허용 origin·method·header를 설정합니다.
- **WebConfig.java**: IP 차단, rate limit, 인증 쿠키 origin, referer 검사를 위한 Spring MVC interceptor를 등록합니다.
- **QuerydslConfig.java**: QueryDSL `JPAQueryFactory` 빈 등록.
- **OpenApiConfig.java**: Swagger/OpenAPI 문서 설정.

### 1.3 `email`
이메일 발송을 담당합니다. Google Workspace SMTP (앱 비밀번호 방식)를 사용합니다.
- **EmailService.java**: 이메일 발송 인터페이스.
- **SmtpEmailService.java**: `JavaMailSender`를 사용한 동기 SMTP 구현체입니다. `spring.mail.*` 설정을 사용하며 발송 실패를 서버 로그에 기록한 뒤 `EMAIL_SEND_FAILED` 예외로 변환합니다. 비동기가 필요한 호출자는 별도 `@Async` 경계에서 이 서비스를 호출해야 합니다.
- **사용처**: 인증 코드 발송 (`VerificationCodeDeliveryService`), 비밀번호 재설정 링크 발송 (`PasswordResetTokenOrchestrationService`), 메시지 큐 발송 (`MqueueService`). 메시지 큐만 `durableTaskExecutor`에서 비동기로 실행되며 인증 메일 API는 SMTP 결과를 확인한 뒤 응답합니다.

### 1.4 `exception`
전역 예외 처리 전략을 정의합니다.
- **ErrorCode.java**: 일반 사용자·관리·전역 흐름의 `BusinessException` 에러 코드와 메시지 키를 정의한 Enum입니다. Agent 쓰기 API의 기계 판독용 코드는 도메인 소유 `AgentWriteErrorCode`가 별도로 정의합니다.
- **BusinessException.java**: 비즈니스 로직에서 발생하는 예외의 기본 클래스입니다.
- **GlobalExceptionHandler.java**: `@ControllerAdvice`를 사용하여 애플리케이션 전역에서 발생하는 예외를 포착하고 표준 `ApiResponse` 포맷으로 변환합니다.

### 1.5 `security`
인증(Authentication) 및 인가(Authorization) 관련 구현체입니다.
- **JwtTokenProvider.java**: JWT 토큰 생성, 검증, 파싱을 담당합니다.
- **JwtAuthenticationFilter.java**: 요청의 `Authorization: Bearer` 헤더에서 JWT를 추출하여 인증 정보를 SecurityContext에 저장하는 필터입니다. query parameter 토큰은 지원하지 않으며, SSE 알림 스트림도 `fetch` 기반 클라이언트가 동일한 Authorization 헤더를 전송합니다.
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
`GlobalExceptionHandler`는 운영 조사가 필요한 비즈니스 오류, 권한 오류, 지원하지 않는 method, 데이터 무결성 오류, 인증 실패와 미처리 예외를 `ErrorLogService`로 비동기 저장합니다. 요청 validation·본문 파싱 오류는 DB에 적재하지 않고 경고 로그만 남기며, 일부 반복 가능한 비즈니스 오류도 정책에 따라 DB 적재를 억제합니다. 미처리 5xx는 스택 트레이스를 포함하고, 에러 로그 저장 실패가 원래 에러 응답에 영향을 주지 않도록 보호합니다.

## 2. API 엔드포인트

| Method | URI | 권한 | 설명 |
|---|---|---|---|
| `GET` | `/api/v1/configs/{key}` | `SUPER_ADMIN` | 키로 전역 설정을 조회합니다. |
| `GET` | `/api/v1/admin/configs` | `SUPER_ADMIN` | 전역 설정 전체를 조회합니다. |
| `GET` | `/api/v1/configs/public` | 공개 | 공개 전역 설정을 조회합니다. |
| `POST` | `/api/v1/admin/configs` | `SUPER_ADMIN` | 전역 설정을 생성합니다. |
| `PUT` | `/api/v1/admin/configs` | `SUPER_ADMIN` | 요청 본문의 키를 기준으로 전역 설정을 수정합니다. |
| `PUT` | `/api/v1/admin/configs/{key}` | `SUPER_ADMIN` | 경로의 키를 기준으로 전역 설정을 수정합니다. |
| `DELETE` | `/api/v1/admin/configs/{key}` | `SUPER_ADMIN` | 전역 설정을 삭제합니다. |
| `POST` | `/api/v1/logs/client` | 공개 | 프론트엔드에서 수집한 오류 로그를 저장합니다. |
| `GET` | `/api/v1/admin/error-logs` | `SUPER_ADMIN` | 에러 로그 목록을 검색·조회합니다. |
| `GET` | `/api/v1/admin/error-logs/{errorLogId}` | `SUPER_ADMIN` | 에러 로그 상세를 조회합니다. |
| `PUT` | `/api/v1/admin/error-logs/{errorLogId}/resolve` | `SUPER_ADMIN` | 에러 로그를 확인 처리합니다. |
| `GET` | `/api/v1/admin/error-logs/stats` | `SUPER_ADMIN` | 에러 로그 통계를 조회합니다. |
| `GET` | `/api/v1/admin/logs` | `SUPER_ADMIN` | DB에 저장된 사용자 행위 감사 로그를 페이지 조회합니다. |
| `POST` | `/api/v1/security/csp-report` | 공개 | 브라우저의 CSP 위반 보고를 수집합니다. |

## 3. 주요 개발 패턴

### API 응답 처리
일반 JSON 업무 API는 반환 타입을 `ApiResponse<T>`로 감쌉니다.
```java
return ApiResponse.success(data);
```
파일 다운로드처럼 `Resource`를 스트리밍하거나 CSP report처럼 `204 No Content`를 반환하는 엔드포인트는 예외입니다.

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
- JPA optimistic lock 충돌은 중복 리소스 오류와 구분해 HTTP 409의 `CONCURRENT_MODIFICATION`(`C012`)으로 응답합니다. 클라이언트는 최신 데이터를 다시 조회한 뒤 사용자가 재시도할 수 있게 안내해야 합니다.
- **성공 메시지**: 컨트롤러에서 성공 메시지 반환 시 `MessageSource`를 주입받아 키를 통해 메시지를 조회해야 합니다.
- **Validation**: DTO의 Validation 어노테이션에는 `{key}` 형식을 사용하여 메시지 키를 지정합니다.

### 보안 및 인증
- **인증 필요 API**: Spring Security 설정에 따라 보호되며, 요청 헤더에 유효한 `Bearer` 토큰이 필요합니다.
- **현재 사용자 접근**: 컨트롤러 메서드 인자로 `@CurrentUserId Long userId`를 받아 현재 인증된 사용자의 ID를 획득합니다. 공개 API에서 선택적 인증이 필요하면 `@CurrentUserId(required = false) Long userId`를 사용합니다.
- **슈퍼 관리자 권한**: 컨트롤러의 `@PreAuthorize`와 서비스의 `SuperAdminPolicy`로 권한을 검증합니다.
