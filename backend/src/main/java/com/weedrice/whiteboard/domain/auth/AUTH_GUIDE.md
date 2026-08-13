# Auth 도메인 가이드

`auth` 도메인은 인증/인가 전반(회원가입, 로그인, 토큰, 비밀번호 재설정, 이메일 인증)을 담당합니다.

## 1. 주요 기능 및 로직
- 회원가입: 이메일/로그인ID 중복 검증 및 이메일 인증 여부 체크, 비밀번호 암호화 후 저장, 기본 `UserSettings`·`UserPoint` 생성.
- 로그인: Spring Security 인증, Access/Refresh Token 발급, Refresh Token SHA-256 해시 저장(ip/디바이스/만료), 로그인 이력 기록 및 `last_login_at` 갱신. Access Token에는 저장된 `sessionFamilyId`가 반드시 포함됩니다.
- 로그아웃: 전달받은 Refresh Token의 session family 전체를 멱등 폐기합니다. 이후 같은 family의 Access Token도 다음 요청부터 즉시 거부됩니다.
- 토큰 재발급: 저장된 Refresh Token 유효성 검증 후 폐기하고 같은 session family를 계승해 새 토큰을 발급합니다. family claim이 없거나 활성 Refresh Token family와 연결되지 않은 legacy Access Token은 `401`로 거부합니다.
- 이메일 인증: 인증 코드 발송·검증. 발송은 pending 인증 정보 생성 → 동기 SMTP 발송 → 활성 상태 승격 순서로 처리해 전송 결과를 확인한 뒤 응답합니다. 검증 성공 시 해당 이메일의 유저가 존재하면 `isEmailVerified`를 true로 업데이트하고, 비밀번호 재설정 후 인증 상태를 초기화합니다.
- 계정 찾기/재설정: 인증된 이메일로 로그인 ID 찾기, fragment에 토큰을 담은 재설정 링크/코드 발송 및 토큰 유효성 검증 뒤 비밀번호 변경. 토큰 승격과 사용은 User → VerificationCode → PasswordResetToken 잠금 순서를 지킵니다.
- 인증 retention: 만료 비밀번호 재설정 토큰을 bounded batch로 먼저 정리한 뒤 만료 인증 코드를 정리하며, 인증 코드 삭제 시 연결 토큰의 참조는 null로 전환됩니다.
- 재가입 지원: 사전 확인 경로는 계정 열거를 막기 위해 고정된 비식별 응답만 반환하며, 실제 재가입 정보는 이메일 인증 성공 응답에서 제공합니다.
- OAuth 가입 ticket: 미가입 OAuth 사용자의 가입 정보는 10분 수명 HttpOnly 쿠키로 전달하며, 조회 실패·가입 성공·명시적 취소 시 쿠키와 서버 ticket을 정리합니다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :------------------------------- | :----------------------------- |
| `POST` | `/api/v1/auth/signup` | 회원가입 |
| `GET` | `/api/v1/auth/oauth/signup-ticket` | HttpOnly OAuth 가입 쿠키의 표시용 정보 조회 |
| `DELETE` | `/api/v1/auth/oauth/signup-ticket` | OAuth 가입 흐름 취소 및 가입 쿠키 만료 |
| `POST` | `/api/v1/auth/login` | 로그인 |
| `POST` | `/api/v1/auth/logout` | Refresh Token 폐기 |
| `POST` | `/api/v1/auth/refresh` | Access/Refresh 재발급 |
| `POST` | `/api/v1/auth/email/send-verification` | 이메일 인증 코드 발송 |
| `POST` | `/api/v1/auth/email/verify` | 이메일 인증 코드 검증 |
| `POST` | `/api/v1/auth/find-id` | 인증된 이메일로 로그인 ID 찾기 |
| `GET` | `/api/v1/auth/reregister/check-email` | 호환용 재가입 사전 확인(항상 비식별 고정 응답) |
| `POST` | `/api/v1/auth/password/send-reset-link` | 비밀번호 재설정 링크 발송 |
| `POST` | `/api/v1/auth/password/send-reset-link-by-email` | 이메일 기반 비밀번호 재설정 링크 발송 |
| `POST` | `/api/v1/auth/password/reset` | 재설정 링크(토큰)로 비밀번호 변경 |
| `POST` | `/api/v1/auth/password/reset-by-code` | 인증 코드로 비밀번호 변경 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `users` | `User` | 회원 기본 정보/상태 |
| `refresh_tokens` | `RefreshToken` | 해시된 Refresh Token, session family, 기기/IP, 만료·폐기 상태 |
| `login_histories` | `LoginHistory` | 로그인 성공 이력 |
| `verification_codes` | `VerificationCode` | 이메일 인증 코드 및 만료/검증 여부 |
| `password_reset_tokens` | `PasswordResetToken` | 비밀번호 재설정용 해시 토큰 |
| `user_settings` | `UserSettings` | 기본 알림/테마 설정 (회원가입 시 생성) |
| `user_points` | `UserPoint` | 포인트 지갑 (회원가입 시 생성) |
