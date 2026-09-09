# NoviIs Frontend

NoviIs 커뮤니티 플랫폼의 Vue 3 기반 프론트엔드 애플리케이션입니다. Vite, TypeScript, Pinia, TanStack Vue Query, Vue Router, Vue I18n을 사용하며 백엔드 API, 인증, SSE/Web Push 알림, 게시글 에디터, 노비콘, PWA 기능과 연동됩니다.

## 기술 스택

아래 버전은 2026-09-09 현재 `package.json`의 선언 범위이며, 설치 해석 버전은 `package-lock.json`을 기준으로 합니다.

- Framework: Vue `^3.5.41`, Composition API, `<script setup>`
- Build Tool: Vite `^8.2.1`
- Language: TypeScript `^5.9.3`
- State: Pinia `^4.0.3`, TanStack Vue Query `^5.101.4`
- Routing: Vue Router `^5.2.0`
- Styling: Tailwind CSS `^4.3.2`, PostCSS, `nv-*` design tokens
- HTTP: Axios `^1.19.0`
- Editor: TipTap `3.30.5`
- Icons: Lucide Vue Next
- I18n: Vue I18n `11.4.8`
- Unit/component tests: Vitest `^4.1.10`, Vue Test Utils, jsdom
- Browser tests: Playwright `^1.62.1`, axe-core accessibility checks

## 프로젝트 구조

```text
src/
|-- api/          Axios clients, request/response handling, API adapters
|-- assets/       CSS and static frontend assets
|-- components/   Reusable UI components
|-- composables/  Shared cross-feature and low-level composables
|-- extensions/   TipTap/editor extensions
|-- features/     Domain feature logic and feature-local composables
|   |-- admin/
|   |-- board/
|   |   |-- icons/
|   |   |-- posts/
|   |   |   |-- detail/
|   |   |   |-- draft/
|   |   |   |-- editor/
|   |   |   |-- form/
|   |   |   `-- queries/
|   |   `-- queries/
|   |-- comments/
|   |-- emoticon/
|   |   |-- detail/
|   |   |-- form/
|   |   |-- list/
|   |   `-- picker/
|   |-- feed/
|   |-- mentions/
|   |-- notifications/
|   |-- search/
|   |-- shop/
|   `-- user/
|-- locales/      Translation resources
|-- router/       Route definitions and guards
|-- stores/       Pinia stores
|-- styles/       Shared stylesheet modules
|-- test/         Shared test setup and helpers
|-- types/        Shared TypeScript types
|-- utils/        Utilities, constants, logger, sanitizers, storage helpers
`-- views/        Route-level pages
```

`main.ts`는 플러그인과 API/스토어 연결을 초기화하고, `App.vue`는 레이아웃과 공통 알림·모달을 조립합니다. `router/routes.ts`는 페이지를 지연 로딩하며 `router/guards.ts`가 인증·권한 접근을 검사합니다.

API 요청은 `api/index.ts`의 공유 Axios 인스턴스를 거칩니다. 서버 상태는 Vue Query, 인증·테마·공통 UI 상태는 Pinia가 담당합니다. 인증 세션 변경 시 `queryAuthScope.ts`와 `main.ts`의 세션 효과가 계정별 query와 알림 스트림을 정리합니다.

PWA는 `service-worker.ts`에서 API/OAuth 요청을 네트워크 전용으로 처리하고 해시 정적 자원을 캐시합니다. 네트워크 실패 시 `/`와 `/index.html`은 앱 셸, 다른 문서 경로는 `offline.html`로 fallback합니다. `pwa.ts`는 업데이트를 자동 적용하되 `pwaReloadGuard.ts`의 폼 보호가 활성화되어 있으면 적용을 미룹니다.

## 구조 기준

- 도메인 전용 query key, cache invalidation, form state, page resource, mutation orchestration은 `src/features/{domain}` 아래에 둡니다.
- `src/composables`는 여러 feature가 공유하는 helper와 낮은 수준의 재사용 로직에 사용합니다.
- 도메인 로직은 `src/features/...`에서 직접 import하며, `src/composables` 아래에 호환용 re-export shim을 다시 만들지 않습니다.
- `components`는 재사용 UI, `views`는 route-level orchestration을 담당합니다.
- 백엔드 응답 envelope와 DTO 변경은 `src/api`, `src/types`, 관련 feature composable, UI consumer를 함께 맞춥니다.

## 주요 기능

- 반응형 커뮤니티 UI와 다크모드
- 게시판, 게시글, 댓글, 검색, 신고 UI
- TipTap 기반 게시글 작성/수정 에디터
- 커스텀 노비콘 등록, 수정, 목록, picker
- 사용자 설정, 프로필, 쪽지, 알림, 구독 게시판
- 관리자 대시보드와 관리 화면
- OAuth callback, token refresh, route guard
- SSE 기반 실시간 알림과 브라우저 Web Push 구독·설정
- 설치형 PWA, 오프라인 fallback, 작성 중인 폼의 보호가 해제되면 새 버전 자동 적용
- SEO sitemap/prerender scripts

## 시작하기

### 요구 사항

- Node.js `>=24.11.0 <25`
- npm

### 설치

```bash
npm ci
```

lockfile 갱신이 필요한 명시적 의존성 변경 작업이 아니라면 `npm install`보다 `npm ci`를 사용합니다.

### 개발 서버

```bash
npm run dev
```

기본 주소:

```text
http://localhost:5173
```

## 환경 변수

로컬 환경 변수는 커밋하지 않는 Vite env 파일에 둡니다.

- `frontend/.env.local`
- `frontend/.env.development.local`

개발 서버·빌드 설정과 브라우저 코드가 사용하는 변수를 구분합니다.

| 변수 | 소비 위치와 역할 |
| --- | --- |
| `VITE_API_BASE_URL` | Vite 개발 서버의 `/api`, `/oauth2` proxy 대상. 기본 `http://localhost:8080` |
| `VITE_API_URL` | 브라우저 Axios API base URL. 기본 `/api/v1` |
| `VITE_INQUIRY_BOARD_URL` | 문의 작성 화면의 게시판 식별자. 기본 `inquiry` |
| `VITE_COMMIT_HASH` | 빌드 시 `__COMMIT_HASH__`로 주입. 없으면 Vite가 Git SHA 조회 |
| `VITE_WEB_VITALS_ENDPOINT` | production Web Vitals 전송 경로. 비어 있으면 전송하지 않음 |
| `VITE_ANALYZE` | Vite production build의 bundle 분석 활성화 |

`VITE_*` 값에는 브라우저에 공개하면 안 되는 비밀을 넣지 않습니다.

`VITE_WEB_VITALS_ENDPOINT`는 production Web Vitals 전송 경로를 지정합니다. `VITE_ANALYZE=true`는 production build에서 bundle 분석 보고서를 생성하며, `--mode analyze`도 같은 분석 기능을 활성화합니다.

## 검증 명령

```bash
npm run lint:ci
npm run type-check
npm run test:run
```

대상 테스트만 실행할 때:

```bash
npm run test:run -- PostForm.spec.ts
npm run test:run -- usePost.spec.ts
npm run test:run -- EmoticonRegister.spec.ts
```

커버리지가 필요한 경우:

```bash
npm run coverage
```

OpenAPI 계약과 브라우저 흐름 검증:

```powershell
npm.cmd run api:check
npm.cmd run test:e2e
```

`api:check`는 `../docs/api/openapi-frontend.json`에서 생성한 타입이 `src/types/generated/api.ts`와 일치하는지 확인합니다. 의도적으로 API snapshot을 갱신했을 때는 `npm.cmd run api:generate`로 재생성합니다.

`test:e2e`는 production build 후 기본 Playwright suite를 실행합니다. `test:e2e:run`은 기존 build를 사용하며, 다운로드·HTML sandbox·별도 통합 suite는 각각 `test:e2e:download`, `test:e2e:sandbox`, `test:e2e:full`의 별도 설정을 사용합니다. `test:e2e:full`은 실행 중인 대상 서버의 `E2E_BASE_URL` 설정이 필요합니다. `src/test/setup.ts`는 Vitest 공통 환경이며 browser tests는 `e2e/`에 있습니다.

## 빌드

일반 production build (`vite build`와 `pwa:verify`, 타입 검사는 별도 `type-check`):

```bash
npm run build
```

SEO sitemap/prerender 절차까지 포함한 build:

```bash
npm run build:seo
```

로컬 Docker runtime에 frontend 변경을 반영해야 하는 경우 repository root에서 실행합니다.

```bash
docker compose build frontend
```

`docker compose build frontend`는 `frontend/Dockerfile`을 사용해 `noviis-frontend:local` 이미지를 만들며, 이미지 build stage 안에서 `npm run build`를 실행합니다.

## 관련 문서

- [frontend/AGENTS.md](./AGENTS.md): AI agent 작업 규칙
- [frontend/docs/frontend-commonization-guidelines.md](./docs/frontend-commonization-guidelines.md): 공통화와 feature boundary 기준
- [docs/design-notes/frontend-color-token-guidelines-2026-05-29.md](../docs/design-notes/frontend-color-token-guidelines-2026-05-29.md): 색상 token 사용 기준
- [docs/qa/frontend-dark-mode-smoke-checklist-2026-05-29.md](../docs/qa/frontend-dark-mode-smoke-checklist-2026-05-29.md): 다크모드 smoke checklist
