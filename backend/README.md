# NoviIs Backend

**NoviIs Backend**는 NoviIs 커뮤니티 플랫폼의 서버 사이드 애플리케이션입니다. Spring Boot 3와 Java 21을 기반으로 구축되었으며, 안정적이고 확장 가능한 RESTful API를 제공합니다.

## 🛠️ 기술 스택 (Tech Stack)

-   **Framework**: Spring Boot 3.x
-   **Language**: Java 21
-   **Build Tool**: Gradle
-   **Database**: PostgreSQL
-   **ORM**: Spring Data JPA / Hibernate
-   **Security**: Spring Security, JWT (JSON Web Token)
-   **Testing**: JUnit 5, Mockito
-   **Logging**: Logback
-   **Cloud/Storage**: AWS S3, SMTP mail integration

## 📂 프로젝트 구조 (Project Structure)

이 프로젝트는 도메인 주도 설계(DDD)의 영향을 받아 도메인별로 패키지가 구성되어 있습니다.

```
com.weedrice.whiteboard
├── domain
│   ├── ad            # 광고 관리
│   ├── admin         # 관리자 기능
│   ├── agent         # MCP/Agent API, quota, notes
│   ├── auth          # 인증 및 인가 (로그인, 회원가입, 비밀번호 초기화)
│   ├── board         # 스페이스 관리
│   ├── comment       # 댓글 관리
│   ├── emoticon      # 이모티콘 관리
│   ├── feed          # 뉴스피드 및 홈 화면
│   ├── file          # 파일 업로드 및 관리
│   ├── message       # 쪽지 시스템
│   ├── notification  # 알림 시스템 (SSE)
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
-   **알림 (Notification)**: SSE(Server-Sent Events)를 이용한 실시간 알림 전송.
-   **관리자 (Admin)**: 사용자 관리, 스페이스 관리, 신고 처리, 시스템 설정.
-   **이모티콘 (Emoticon)**: 커스텀 이모티콘 등록/수정/구매 및 게시글/댓글 활용.
-   **검색 (Search)**: 게시글 및 스페이스 검색.

## 🚀 시작하기 (Getting Started)

### 전제 조건 (Prerequisites)
-   JDK 21 이상
-   PostgreSQL

### 설정 (Configuration)

#### 개발 환경
로컬 실행에는 PostgreSQL, JWT secret, OAuth/mail/storage 관련 설정이 필요합니다. 개인 값은 셸 환경 변수, IDE run configuration, 또는 커밋하지 않는 로컬 설정으로 주입하세요.

#### 프로덕션 환경
프로덕션 환경에서는 환경 변수를 통해 설정을 관리합니다.

1. **필수 환경 변수**
   - 데이터베이스: `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
   - JWT: `JWT_SECRET`
   - OAuth: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` (필수), Google/Discord (선택적)
   - AWS: `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `AWS_SES_*`, `S3_BUCKET` 등
   - Frontend: `FRONTEND_URL`

2. **환경 변수 검증**
   - 프로덕션 환경에서는 애플리케이션 시작 시 필수 환경 변수를 자동으로 검증합니다
   - 필수 환경 변수가 누락된 경우 애플리케이션이 시작되지 않습니다

### 실행 (Run)
```bash
# Windows (PowerShell / CMD)
.\gradlew bootRun

# Linux / Mac
./gradlew bootRun
```

서버는 기본적으로 `http://localhost:8080`에서 실행됩니다.

## 🧪 테스트 (Test)
```bash
./gradlew test
```
