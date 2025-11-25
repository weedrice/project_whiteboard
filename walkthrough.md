# Walkthrough - Environment, DB, and User Module

## 작업 요약
`NEXT_TASKS.md`의 1, 2, 3번 작업을 완료했습니다.
- **환경 설정**: `build.gradle` 의존성 추가, `application.yml` 설정 (PostgreSQL, JPA, JWT)
- **DB 구축**: JPA 엔티티(`User`, `UserSettings`, `BaseTimeEntity`) 생성
- **회원 모듈**: `AuthController`, `AuthService`, `UserRepository`, `SecurityConfig`, `JwtTokenProvider` 구현

## 변경 사항

### 1. 환경 설정
- `backend/build.gradle`: JPA, Security, JWT, PostgreSQL 의존성 추가
- `backend/src/main/resources/application.yml`: DB 연결 및 JWT 설정 추가
- `backend/src/main/resources/application.properties`: 삭제

### 2. 도메인 엔티티
- `BaseTimeEntity`: 생성일/수정일 자동 관리를 위한 공통 클래스
- `User`: 회원 정보 엔티티
- `UserSettings`: 사용자 설정 엔티티

### 3. 회원 모듈 & 보안
- `SecurityConfig`: Spring Security 설정 (CSRF 비활성화, 세션 비활성화, 엔드포인트 권한 설정)
- `JwtTokenProvider`: JWT 토큰 생성 및 검증 로직
- `AuthService`: 회원가입, 로그인 비즈니스 로직
- `AuthController`: 회원가입(`POST /signup`), 로그인(`POST /login`) API 구현

## 검증 결과
- `AuthControllerTest`를 작성하여 단위 테스트를 준비했습니다.
- **결과**: 현재 실행 환경에서 `JAVA_HOME` 또는 `java` 명령어를 찾을 수 없어 테스트 실행을 건너뛰었습니다.
- **조치 필요**: 로컬 환경에 Java(JDK 21)가 설치되어 있고 `PATH` 환경변수에 등록되어 있는지 확인 후, `./gradlew test`를 실행하여 검증할 수 있습니다.

## 다음 단계
- `NEXT_TASKS.md`의 4번(게시판 모듈) 구현을 진행할 차례입니다.
