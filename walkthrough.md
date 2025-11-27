### 3. 회원 모듈 & 보안
- `SecurityConfig`: Spring Security 설정 (CSRF 비활성화, 세션 비활성화, 엔드포인트 권한 설정)
- `JwtTokenProvider`: JWT 토큰 생성 및 검증 로직
- `AuthService`: 회원가입, 로그인 비즈니스 로직
- `AuthController`: 회원가입(`POST /signup`), 로그인(`POST /login`) API 구현
- **조치 필요**: 로컬 환경에 Java(JDK 21)가 설치되어 있고 `PATH` 환경변수에 등록되어 있는지 확인 후, `./gradlew test`를 실행하여 검증할 수 있습니다.

## 다음 단계
- `NEXT_TASKS.md`의 4번(게시판 모듈) 구현을 진행할 차례입니다.
