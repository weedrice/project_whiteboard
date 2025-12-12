# Auth 도메인 가이드

`auth` 도메인은 인증/인가 전반(회원가입, 로그인, 토큰, 비밀번호 재설정, 이메일 인증)을 담당합니다.

## 1. 주요 기능 및 로직
- 회원가입: 이메일/로그인ID 중복 검증 및 이메일 인증 여부 체크, 비밀번호 암호화 후 저장, 기본 `UserSettings`·`UserPoint` 생성.
- 로그인: Spring Security 인증, Access/Refresh Token 발급, Refresh Token SHA-256 해시 저장(ip/디바이스/만료), 로그인 이력 기록 및 `last_login_at` 갱신.
- 로그아웃: 전달받은 Refresh Token 해시를 찾아 폐기 상태로 변경.
- 토큰 재발급: 저장된 Refresh Token 유효성 검증 후 폐기, 동일 기기 정보로 새 토큰 발급.
- 이메일 인증: 인증 코드 발송·검증, 비밀번호 재설정 후 인증 상태 초기화.
- 계정 찾기/재설정: 인증된 이메일로 로그인 ID 찾기, 재설정 링크/코드 발송 및 토큰 유효성 검증 뒤 비밀번호 변경.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :------------------------------- | :----------------------------- |
| `POST` | `/api/v1/auth/signup` | 회원가입 |
| `POST` | `/api/v1/auth/login` | 로그인 |
| `POST` | `/api/v1/auth/logout` | Refresh Token 폐기 |
| `POST` | `/api/v1/auth/refresh` | Access/Refresh 재발급 |
| `POST` | `/api/v1/auth/email/send-verification` | 이메일 인증 코드 발송 |
| `POST` | `/api/v1/auth/email/verify` | 이메일 인증 코드 검증 |
| `POST` | `/api/v1/auth/find-id` | 인증된 이메일로 로그인 ID 찾기 |
| `POST` | `/api/v1/auth/password/send-reset-link` | 비밀번호 재설정 링크 발송 |
| `POST` | `/api/v1/auth/password/reset` | 재설정 링크(토큰)로 비밀번호 변경 |
| `POST` | `/api/v1/auth/password/reset-by-code` | 인증 코드로 비밀번호 변경 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `users` | `User` | 회원 기본 정보/상태 |
| `refresh_tokens` | `RefreshToken` | 해시된 Refresh Token, 기기/IP, 만료 |
| `login_histories` | `LoginHistory` | 로그인 성공 이력 |
| `verification_codes` | `VerificationCode` | 이메일 인증 코드 및 만료/검증 여부 |
| `password_reset_tokens` | `PasswordResetToken` | 비밀번호 재설정용 해시 토큰 |
| `user_settings` | `UserSettings` | 기본 알림/테마 설정 (회원가입 시 생성) |
| `user_points` | `UserPoint` | 포인트 지갑 (회원가입 시 생성) |
