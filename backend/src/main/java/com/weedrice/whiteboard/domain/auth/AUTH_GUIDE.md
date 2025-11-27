# Auth 도메인 가이드

`auth` 도메인은 사용자의 인증과 관련된 모든 기능을 담당합니다. (회원가입, 로그인, 로그아웃, 토큰 재발급)

## 1. 주요 기능 및 로직

`AuthService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **회원가입 (`signup`):**
  - 로그인 ID 및 이메일 중복 여부를 확인합니다.
  - 비밀번호를 암호화하여 `users` 테이블에 새로운 사용자를 저장합니다.

- **로그인 (`login`):**
  - `AuthenticationManager`를 통해 사용자 자격 증명을 인증합니다.
  - 인증 성공 시, Access Token과 Refresh Token을 발급합니다.
  - Refresh Token은 보안을 위해 해시 처리 후 `refresh_tokens` 테이블에 저장합니다.
  - `login_histories` 테이블에 로그인 성공 기록을 남깁니다.
  - 사용자의 `last_login_at` 필드를 현재 시간으로 업데이트합니다.

- **로그아웃 (`logout`):**
  - 전달받은 Refresh Token을 `refresh_tokens` 테이블에서 찾아 '폐기(revoked)' 상태로 변경합니다.

- **토큰 재발급 (`refresh`):**
  - 전달받은 Refresh Token의 유효성을 검증합니다.
  - 유효한 토큰일 경우, 새로운 Access Token을 발급합니다.

## 2. API Endpoints

`AuthController`에서 다음 API를 제공합니다.

| Method | URI                  | 설명         |
| :----- | :------------------- | :----------- |
| `POST` | `/api/v1/auth/signup`  | 회원가입     |
| `POST` | `/api/v1/auth/login`   | 로그인      |
| `POST` | `/api/v1/auth/logout`  | 로그아웃     |
| `POST` | `/api/v1/auth/refresh` | 토큰 재발급  |

## 3. 관련 DB 테이블

`auth` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명            | 엔티티                | 설명                               |
| :------------------ | :-------------------- | :--------------------------------- |
| `users`             | `User`                | 회원 정보 (회원가입 시 생성)       |
| `refresh_tokens`    | `RefreshToken`        | JWT Refresh Token 정보 저장        |
| `login_histories`   | `LoginHistory`        | 로그인 성공/실패 이력 기록         |
| `password_histories`| `PasswordHistory`     | 비밀번호 변경 이력 (재사용 방지용) |

이 문서는 `auth` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
