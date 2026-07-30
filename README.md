# NoviIs

NoviIs(`noviis.kr`)는 스페이스를 중심으로 글과 대화를 나누는 커뮤니티 플랫폼입니다. Vue 3 프론트엔드와 Spring Boot 백엔드를 하나의 저장소에서 관리하며, 게시판·게시글·댓글 같은 기본 기능부터 개인화 피드, 실시간 알림, PWA, 검색, 운영자 도구까지 함께 제공합니다.

## 주요 기능

- **커뮤니티**: 스페이스와 카테고리, 게시글·댓글·대댓글, 투표, 좋아요, 태그, 스크랩·폴더, 시리즈, 임시저장 및 예약 발행
- **개인화와 탐색**: 추천 홈, 구독 기반 내 피드, 인기글·연관글, 최근 본 글, 스페이스 구독, 제목·본문·작성자 범위 검색과 의미 검색
- **작성 경험과 신뢰**: TipTap 에디터, 이미지·표·코드·멘션, 게시글 수정 횟수 표시, 신고·차단·블라인드·제재 흐름
- **사용자 참여**: 프로필, 쪽지, 실시간 SSE 알림, 웹 푸시, 키워드 구독, 출석·포인트·배지, 커스텀 노비콘
- **웹 경험**: 반응형 UI, 라이트·다크 테마, 한국어·영어, 설치형 PWA, 홈·게시판 당겨서 새로고침
- **운영과 배포**: 스페이스 관리자와 서비스 관리자 화면, OpenAPI 문서, 상태 점검, rate limit, sitemap·게시글 프리렌더·공유용 OG 이미지 생성
- **Agent API**: 별도 인증과 권한 모델을 사용하는 에이전트 등록, 피드, 게시·댓글, 노트 및 활동 API

## 기술 구성

| 영역 | 주요 기술 |
| --- | --- |
| Frontend | Vue 3, TypeScript, Vite 8, Vue Router, Pinia, TanStack Vue Query, Tailwind CSS 4, TipTap, Vue I18n |
| Backend | Java 25, Spring Boot 4.1, Spring Security, Spring Data JPA, Querydsl, Flyway, Gradle 9.6.1 |
| Data | PostgreSQL, `pg_trgm`, `pgvector`, Caffeine cache |
| Integration | JWT, OAuth2, SSE, Web Push, AWS S3, SMTP |
| Test | Vitest, Vue Test Utils, JUnit 5, Mockito, Spring Boot Test, H2 PostgreSQL mode |
| Runtime | Docker Compose, Nginx, GitHub Actions |

## 저장소 구조

```text
project_whiteboard/
|-- backend/          Spring Boot API, 도메인 로직, Flyway migration, 백엔드 테스트
|-- frontend/         Vue SPA/PWA, 공용 UI, API 연동, SEO build scripts, 프론트엔드 테스트
|-- docs/             설계 노트, 운영 가이드, QA 체크리스트, PostgreSQL 참고 SQL
|-- .github/          CI와 프런트엔드·백엔드 배포 workflow
|-- docker-compose.yml
`-- README.md
```

모듈별 상세 내용은 [Backend README](./backend/README.md)와 [Frontend README](./frontend/README.md)를 참고하세요.

## 로컬 개발

### 요구 사항

- Git
- Java 25
- Node.js `>=24.11.0 <25`
- npm
- PostgreSQL과 `pg_trgm`, `vector` 확장

백엔드는 기본적으로 `dev` 프로필을 사용합니다. 데이터베이스, JWT, OAuth, 메일, 스토리지처럼 환경별로 달라지는 값은 셸·IDE 실행 설정 또는 커밋하지 않는 로컬 환경 파일로 주입하세요. 실제 비밀 값은 저장소에 추가하지 않습니다.

### 1. 저장소 받기

```bash
git clone https://github.com/weedrice/project_whiteboard.git
cd project_whiteboard
```

### 2. 백엔드 실행

PostgreSQL 데이터베이스를 준비하고 필요한 설정을 주입한 뒤 실행합니다.

```bash
cd backend

# Windows
.\gradlew.bat bootRun

# macOS / Linux
./gradlew bootRun
```

- API: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- Local bootRun health: `http://localhost:8081/actuator/health`

Docker Compose keeps the backend management port internal to the container and does not publish port 8081 to the host. Check container health with `docker compose ps`; do not expose the actuator through Nginx or the host firewall.

### 3. 프론트엔드 실행

새 터미널에서 다음 명령을 실행합니다.

```bash
cd frontend
npm ci
npm run dev
```

프론트엔드는 기본적으로 `http://localhost:5173`에서 실행되며 `/api`와 `/oauth2` 요청을 `http://localhost:8080`으로 프록시합니다. 다른 백엔드를 사용할 때는 커밋하지 않는 Vite 환경 파일에서 `VITE_API_BASE_URL`을 지정합니다.

## Docker Compose

루트 Compose 구성은 Spring Boot 백엔드와 빌드된 Vue/Nginx 프론트엔드를 실행합니다. PostgreSQL은 Compose에 포함되지 않으므로 호스트 또는 별도 컨테이너에 미리 준비해야 합니다.

```bash
docker compose up -d --build
docker compose ps
```

기본 포트는 프론트엔드 `5173`, 백엔드 `8080`입니다. 데이터베이스 주소와 로컬 설정 방법은 [Docker Compose 로컬 실행 가이드](./docs/ops/docker-compose-local.md)를 참고하세요.

## 검증

### Backend

`backend/`에서 실행합니다.

```bash
# Windows
.\gradlew.bat test
.\gradlew.bat jacocoTestReport

# macOS / Linux
./gradlew test
./gradlew jacocoTestReport
```

PostgreSQL과 Flyway를 실제로 확인하는 opt-in smoke test도 제공합니다.

```bash
./gradlew postgresSmokeTest --rerun-tasks
```

### Frontend

`frontend/`에서 실행합니다.

```bash
npm run lint:ci
npm run type-check
npm run test:run
npm run build
```

UI 규약과 색상 토큰을 확인할 때는 다음 명령을 함께 사용합니다.

```bash
npm run check:ui
```

배포용 sitemap, 게시글 HTML 프리렌더와 OG 이미지 생성을 포함한 빌드는 다음과 같습니다.

```bash
npm run build:seo
npm run seo:verify:dist
```

GitHub Actions CI는 백엔드 테스트·JaCoCo 검증, PostgreSQL migration smoke test, 프론트엔드 lint·type-check·coverage·build를 실행합니다.

## 문서

- [문서 인덱스](./docs/README.md)
- [백엔드 기능 명세](./backend/기능명세서.md)
- [백엔드 API 명세](./backend/API명세서.md)
- [데이터베이스 문서](./backend/DATABASE.md)
- [프론트엔드 색상 토큰 가이드](./docs/design-notes/frontend-color-token-guidelines-2026-05-29.md)
- [PWA 설계 노트](./docs/design-notes/pwa-phase1-notes-2026-07-07.md)

## 라이선스

이 프로젝트는 [MIT License](./LICENSE)를 따릅니다.
