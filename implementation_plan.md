# 구현 계획 - 환경 설정, DB 및 회원 모듈

## 목표
프로젝트 환경을 초기화하고, JPA를 사용하여 PostgreSQL을 구성하며, 명세서에 기반한 회원 모듈(인증, 회원 관리)을 구현합니다.

## 사용자 검토 필요 사항
> [!IMPORTANT]
> **데이터베이스 스키마**: 테이블은 JPA(`ddl-auto: update`)를 통해 생성됩니다. PostgreSQL이 실행 중이어야 하며 `whiteboard`(또는 설정된 이름) 데이터베이스가 존재해야 합니다.
> **보안**: JWT 기반 인증이 구현될 예정입니다.

## 변경 제안

### 백엔드 설정

#### [MODIFY] [build.gradle](file:///c:/Users/user/IdeaProjects/project_whiteboard/backend/build.gradle)
- 의존성 추가:
    - `spring-boot-starter-data-jpa`

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/domain/user/entity/User.java`
- `users` 테이블 매핑
- 필드: `userId`, `loginId`, `password`, `email`, `displayName`, `status`, `isEmailVerified` 등

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/domain/user/entity/UserSettings.java`
- `user_settings` 테이블 매핑

### 회원 모듈 구현

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/domain/user/repository/UserRepository.java`
- User용 JPA Repository

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/domain/auth/controller/AuthController.java`
- 엔드포인트: `/auth/signup`, `/auth/login`, `/auth/refresh`

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/domain/auth/service/AuthService.java`
- 회원가입 및 로그인 비즈니스 로직

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/global/config/SecurityConfig.java`
- Spring Security 설정 (FilterChain, PasswordEncoder)

#### [NEW] `backend/src/main/java/com/weedrice/project_whiteboard/global/security/JwtTokenProvider.java`
- JWT 생성 및 검증

## 검증 계획

### 자동화 테스트
- `./gradlew test`를 실행하여 컨텍스트 로드 확인
- `AuthControllerTest`를 생성하여 `MockMvc`를 사용해 회원가입 및 로그인 엔드포인트 검증

### 수동 검증
- 애플리케이션 실행: `./gradlew bootRun`
- Postman 또는 curl 사용:
    1. `POST /api/v1/auth/signup` 호출 -> 201 Created 예상
    2. `POST /api/v1/auth/login` 호출 -> Access/Refresh Token과 함께 200 OK 예상
    3. 데이터베이스 확인: `users` 테이블 생성 및 데이터 삽입 확인
