# NoviIs Frontend

NoviIs 커뮤니티 플랫폼의 Vue 3 기반 프론트엔드 애플리케이션입니다. Vite, TypeScript, Pinia, TanStack Query를 사용하며 백엔드 API와 SSE 알림을 연동합니다.

## 기술 스택

- Framework: Vue 3, Composition API, Script Setup
- Build Tool: Vite
- Language: TypeScript / JavaScript
- State Management: Pinia, TanStack Query
- Routing: Vue Router
- Styling: TailwindCSS, PostCSS
- HTTP Client: Axios
- Icons: Lucide Vue Next
- I18n: Vue I18n
- Test: Vitest, Vue Test Utils

## 프로젝트 구조

```text
src
├── api             # 백엔드 API 통신 모듈
├── assets          # CSS, 이미지 등 정적 자원
├── components      # 재사용 UI 컴포넌트
│   ├── admin       # 관리자 컴포넌트
│   ├── board       # 노드 컴포넌트
│   ├── comment     # 댓글 컴포넌트
│   ├── common      # 공통 UI, 위젯
│   ├── home        # 홈 화면 컴포넌트
│   ├── layout      # Header, Footer 등 레이아웃
│   ├── notification# 알림 컴포넌트
│   ├── report      # 신고 관련 컴포넌트
│   ├── search      # 검색 컴포넌트
│   ├── tag         # 태그 컴포넌트
│   ├── user        # 사용자 컴포넌트
│   └── __tests__   # 컴포넌트 테스트
├── composables     # Vue composables
├── extensions      # TipTap 에디터 확장
├── locales         # 다국어 리소스
├── router          # 라우터 설정
├── stores          # Pinia store
├── types           # TypeScript 타입 정의
├── utils           # 유틸리티 함수
└── views           # 페이지 뷰
    ├── admin       # 관리자 페이지
    ├── auth        # 인증 페이지
    ├── board       # 노드 페이지
    ├── common      # 공통 페이지
    ├── emoticon    # 이모티콘 페이지
    ├── home        # 홈 화면
    ├── search      # 검색 페이지
    ├── user        # 사용자 페이지
    ├── PrivacyPolicy.vue
    └── TermsOfService.vue
```

## 주요 기능

- 반응형 커뮤니티 UI
- 다크 모드
- 커스텀 이모티콘 등록, 구매, 게시글/댓글 사용
- SSE 기반 실시간 알림
- 한국어/영어 다국어 리소스
- TipTap 기반 WYSIWYG 에디터
- 관리자 대시보드와 사용자/노드 관리 화면
- 검색, 태그, 신고, 댓글 UI

## 시작하기

### 전제 조건

- Node.js 20.19 이상 또는 22.12 이상
- npm

### 설치

```bash
npm ci
```

로컬에서 lockfile을 갱신해야 하는 의존성 변경 작업은 `npm install`을 사용한다.

### 개발 서버

```bash
npm run dev
```

기본 주소는 `http://localhost:5173`입니다.

### 빌드

```bash
npm run build
```

SEO 배포 빌드와 동일한 절차를 확인하려면 다음 명령을 사용합니다.

```bash
npm run build:seo
```

### 테스트와 검증

```bash
npm run test:run
npm run type-check
npm run lint:ci
```
