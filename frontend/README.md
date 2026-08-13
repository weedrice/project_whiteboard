# NoviIs Frontend

NoviIs 커뮤니티 플랫폼의 Vue 3 기반 프론트엔드 애플리케이션입니다. Vite, TypeScript, Pinia, TanStack Vue Query, Vue Router, Vue I18n을 사용하며 백엔드 API, 인증, SSE/Web Push 알림, 게시글 에디터, 노비콘, PWA 기능과 연동됩니다.

## 기술 스택

- Framework: Vue 3, Composition API, `<script setup>`
- Build Tool: Vite
- Language: TypeScript
- State: Pinia, TanStack Vue Query
- Routing: Vue Router
- Styling: Tailwind CSS, PostCSS, `nv-*` design tokens
- HTTP: Axios
- Editor: TipTap
- Icons: Lucide Vue Next
- I18n: Vue I18n
- Test: Vitest, Vue Test Utils, jsdom

## 프로젝트 구조

```text
src/
|-- api/          Axios clients, request/response handling, API adapters
|-- assets/       CSS and static frontend assets
|-- components/   Reusable UI components
|-- composables/  Shared composables and compatibility re-export shims
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

## 구조 기준

- 도메인 전용 query key, cache invalidation, form state, page resource, mutation orchestration은 `src/features/{domain}` 아래에 둡니다.
- `src/composables`는 여러 feature가 공유하는 helper, 낮은 수준의 재사용 로직, 기존 import 경로 유지를 위한 re-export shim에 사용합니다.
- 새 코드에서는 이동된 도메인 로직을 `src/features/...`에서 직접 import하는 것을 우선합니다.
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
- 설치형 PWA, 오프라인 fallback과 새 버전 안내
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

현재 클라이언트에서 참조하는 주요 변수:

- `VITE_API_BASE_URL`
- `VITE_API_URL`
- `VITE_INQUIRY_BOARD_URL`
- `VITE_COMMIT_HASH`
- `VITE_WEB_VITALS_ENDPOINT`
- `VITE_ANALYZE`

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

## 빌드

일반 production build:

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
