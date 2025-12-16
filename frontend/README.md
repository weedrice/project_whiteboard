# NoviIs Frontend

**NoviIs Frontend**는 NoviIs 커뮤니티 플랫폼의 클라이언트 사이드 애플리케이션입니다. Vue 3와 Vite를 기반으로 구축되었으며, 빠르고 반응성 높은 사용자 경험을 제공합니다.

## 🛠️ 기술 스택 (Tech Stack)

-   **Framework**: Vue 3 (Composition API, Script Setup)
-   **Build Tool**: Vite
-   **Language**: TypeScript / JavaScript
-   **State Management**: Pinia (UI State), TanStack Query (Server State)
-   **Routing**: Vue Router
-   **Styling**: TailwindCSS, PostCSS
-   **HTTP Client**: Axios
-   **Icons**: Lucide Vue Next
-   **I18n**: Vue I18n

## 📂 프로젝트 구조 (Project Structure)

```
src
├── api             # 백엔드 API 통신 모듈
├── assets          # 정적 자원 (CSS, Images)
├── components      # 재사용 가능한 UI 컴포넌트
│   ├── admin       # 관리자용 컴포넌트
│   ├── board       # 게시판 관련 컴포넌트
│   ├── common      # 공통 컴포넌트 (UI, Widgets)
│   ├── layout      # 레이아웃 컴포넌트 (Header, Footer)
│   ├── notification# 알림 컴포넌트
│   └── user        # 사용자 관련 컴포넌트
├── composables     # Vue Composables (Hooks)
├── locales         # 다국어 리소스 (i18n)
├── router          # 라우터 설정
├── stores          # Pinia 스토어
├── types           # TypeScript 타입 정의
├── utils           # 유틸리티 함수
└── views           # 페이지 뷰 컴포넌트
    ├── admin       # 관리자 페이지
    ├── auth        # 인증 페이지
    ├── board       # 게시판 페이지
    ├── common      # 공통 페이지 (에러 등)
    ├── home        # 홈 화면
    ├── search      # 검색 페이지
    └── user        # 사용자 페이지
```

## ✨ 주요 기능 (Key Features)

-   **반응형 디자인**: 데스크탑 및 모바일 환경 지원.
-   **다크 모드**: 시스템 설정 또는 사용자 선택에 따른 테마 전환.
-   **실시간 알림**: SSE를 통한 실시간 알림 수신 및 UI 업데이트.
-   **다국어 지원**: 한국어 및 영어 지원 (확장 가능).
-   **에디터**: 게시글 작성을 위한 WYSIWYG 에디터 통합.
-   **관리자 대시보드**: 차트 및 테이블을 활용한 데이터 시각화 및 관리.

## 🚀 시작하기 (Getting Started)

### 전제 조건 (Prerequisites)
-   Node.js 18 이상
-   npm 또는 yarn

### 설치 (Installation)
```bash
npm install
```

### 개발 서버 실행 (Development)
```bash
npm run dev
```
앱은 `http://localhost:5173`에서 실행됩니다.

### 빌드 (Build)
```bash
npm run build
```

### 타입 체크 (Type Check)
```bash
npm run type-check
```
