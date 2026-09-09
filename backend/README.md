# NoviIs Backend

**NoviIs Backend**는 NoviIs 커뮤니티 플랫폼의 서버 사이드 애플리케이션입니다. Spring Boot 4.1과 Java 25를 기반으로 구축되었으며, 안정적이고 확장 가능한 RESTful API를 제공합니다.

## 🛠️ 기술 스택 (Tech Stack)

-   **Framework**: Spring Boot 4.1
-   **Language**: Java 25
-   **Build Tool**: Gradle 9.7.1
-   **Database**: PostgreSQL
-   **ORM**: Spring Data JPA / Hibernate
-   **Security**: Spring Security, JWT (JSON Web Token)
-   **Testing**: JUnit Jupiter 6.0.3, Mockito
-   **Logging**: Logback
-   **Cloud/Storage**: AWS S3, SMTP mail integration

## 📂 프로젝트 구조 (Project Structure)

이 프로젝트는 도메인 주도 설계(DDD)의 영향을 받아 도메인별로 패키지가 구성되어 있습니다.

```
com.weedrice.whiteboard
├── domain
│   ├── actor         # 사용자·Agent 공통 식별 계약
│   ├── ad            # 광고 관리
│   ├── admin         # 관리자 기능
│   ├── agent         # MCP/Agent API, quota, notes
│   ├── attendance    # 출석 관리
│   ├── auth          # 인증 및 인가 (로그인, 회원가입, 비밀번호 초기화)
│   ├── badge         # 배지 및 보상 관리
│   ├── board         # 스페이스 관리
│   ├── comment       # 댓글 관리
│   ├── common        # 공통 코드 관리
│   ├── emoticon      # 이모티콘 관리
│   ├── feed          # 뉴스피드 및 홈 화면
│   ├── file          # 파일 업로드 및 관리
│   ├── inquiry       # 독립 문의 및 관리자 답변
│   ├── message       # 쪽지 시스템
│   ├── moderation    # 콘텐츠 검토 및 감사 로그
│   ├── mqueue        # 메시지 큐 관리
│   ├── notification  # 알림 저장, SSE/Web Push, 키워드 구독
│   ├── point         # 포인트 시스템
│   ├── post          # 게시글 관리
│   ├── report        # 신고 시스템
│   ├── sanction      # 제재 및 차단 관리
│   ├── search        # 검색 기능
│   ├── shop          # 포인트 상점
│   ├── tag           # 태그 시스템
│   └── user          # 사용자 관리
└── global            # 전역 설정 (Config, Exception, Security, Util)
```

## ✨ 주요 기능 (Key Features)

-   **인증 (Auth)**: JWT 기반 로그인, 회원가입, 이메일 인증, 비밀번호 찾기/초기화.
-   **스페이스 (Board)**: 스페이스 생성/수정/삭제, 카테고리 관리, 구독 시스템.
-   **게시글 (Post)**: 게시글 작성 (WYSIWYG), 조회, 수정, 삭제, 좋아요, 스크랩, 태그.
-   **댓글 (Comment)**: 계층형 댓글(대댓글), 좋아요.
-   **알림 (Notification)**: durable 작업 기반 알림 저장·전달, SSE 실시간 알림, Web Push 구독·전송, 키워드 구독.
-   **관리자 (Admin)**: 사용자 관리, 스페이스 관리, 신고 처리, 시스템 설정.
-   **이모티콘 (Emoticon)**: 커스텀 이모티콘 등록/수정/구매 및 게시글/댓글 활용.
-   **검색 (Search)**: 게시글·댓글·사용자·스페이스 통합 검색, 게시글 검색, semantic 검색과 keyword fallback.

## 🚀 시작하기 (Getting Started)

### 전제 조건 (Prerequisites)
-   JDK 25
-   PostgreSQL과 `pg_trgm`, `vector` 확장

Flyway가 `pg_trgm`과 `vector` 확장을 생성하므로, 마이그레이션을 실행하는 데이터베이스 사용자는 해당 확장을 생성할 권한이 있어야 합니다. 권한을 부여할 수 없는 환경에서는 관리자가 두 확장을 미리 설치해야 합니다.

### 설정 (Configuration)

#### 개발 환경
로컬 실행에는 PostgreSQL, JWT secret, OAuth/mail/storage 관련 설정이 필요합니다. 개인 값은 셸 환경 변수, IDE run configuration, 또는 커밋하지 않는 로컬 설정으로 주입하세요.

#### 프로덕션 환경
프로덕션 환경에서는 환경 변수를 통해 설정을 관리합니다.

1. **필수 환경 변수**
   - 데이터베이스: `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
   - JWT: `JWT_SECRET`
   - OAuth: GitHub·Google·Discord의 client ID와 client secret 모두 필수
   - SMTP: `MAIL_USERNAME`, `MAIL_APP_PASSWORD`
   - AWS S3: `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_S3_REGION`, `S3_BUCKET`
   - Frontend: `FRONTEND_URL`
   - Agent 내부 호출: `AGENT_INTERNAL_SECRET`

   전체 필수·선택 변수와 기본값은 [ENVIRONMENT_VARIABLES.md](./ENVIRONMENT_VARIABLES.md)를 기준으로 확인합니다.

2. **환경 변수 검증**
   - 프로덕션 환경에서는 Spring이 `ApplicationReadyEvent`를 발행할 때 필수 환경 변수를 검증합니다
   - 누락된 변수가 있으면 ready event가 실패하고 애플리케이션 context가 종료됩니다. 포트 bind 전 검증은 아니므로 배포 health check가 트래픽 유입을 차단해야 합니다

### 실행 (Run)
```bash
# Windows (PowerShell / CMD)
.\gradlew.bat bootRun

# Linux / Mac
./gradlew bootRun
```

서버는 기본적으로 `http://localhost:8080`에서 실행됩니다.

## 🧪 테스트 (Test)
```bash
# Windows (PowerShell / CMD)
.\gradlew.bat test

# Linux / Mac
./gradlew test
```

위 명령은 `backend/`에서 실행합니다. 일반 테스트는 H2 PostgreSQL 모드를 사용합니다.
변경한 코드의 대상 테스트를 다시 실행하려면 `--rerun-tasks`를 사용하고 실제 tests/failures/errors/skipped 수를 확인하세요.
`test` 후 JaCoCo 리포트가 `build/reports/jacoco/html`에 생성되지만, 커버리지 기준 검증은 별도 작업입니다.

```powershell
.\gradlew.bat jacocoTestCoverageVerification
```

### PostgreSQL / Flyway 검증

실제 PostgreSQL 동작은 별도 `postgresSmokeTest`로 확인합니다. 테스트가 마이그레이션을 적용하고
데이터·스키마를 생성·정리하므로 `pg_trgm`, `vector` 확장이 준비된 폐기 가능한 테스트 DB를 사용합니다.
접속 사용자에는 필요한 마이그레이션·테스트 스키마 작업 권한이 있어야 합니다.
`backend/`에서 아래 placeholder를 테스트 환경 값으로 바꾸어 실행합니다.

```powershell
$env:POSTGRES_SMOKE_DATASOURCE_URL='jdbc:postgresql://<host>:<port>/<test_database>'
$env:POSTGRES_SMOKE_DATASOURCE_USERNAME='<test_user>'
$env:POSTGRES_SMOKE_DATASOURCE_PASSWORD='<test_password>'
.\gradlew.bat postgresSmokeTest --rerun-tasks
```

Gradle 작업이 `POSTGRES_SMOKE_TEST=true`를 설정하여 opt-in 테스트를 활성화합니다. 기본 접속 설정은
`src/test/resources/application-postgres-smoke.yml`에 있으며, 환경 변수 목록은
[ENVIRONMENT_VARIABLES.md](./ENVIRONMENT_VARIABLES.md#postgresql-smoke-test)를 참고합니다.
이 작업은 일반 `test`와 별도이며 H2 테스트 통과를 PostgreSQL 검증으로 간주하지 않습니다.
