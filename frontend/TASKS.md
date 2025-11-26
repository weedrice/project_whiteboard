# 💻 프론트엔드 개발 작업 계획서 (TASKS_FE.md)

> 📅 작성일: 2025-11-25 (Updated: 2025-11-26)
> 🎯 목표: Vite + Vue 3 기반 사용자 친화적인 커뮤니티 UI 구축
> ⚙️ 기술 스택: **Vite, Vue 3, Pinia, TanStack Query (Vue Query), Tailwind CSS**

---

## 📋 Phase 0: 프로젝트 초기 설정 (Vite & Environment)

### 0.1 개발 환경 구성
- [x] **Vite 프로젝트 초기화** (`npm create vite@latest`)
- [x] **필수 모듈/라이브러리 설치**
    - [x] Pinia (상태 관리)
    - [x] Tailwind CSS / PostCSS (스타일링)
    - [x] ESLint, Prettier (코드 품질 관리)
    - [x] HTML Editor 라이브러리 (예: **Tiptap for Vue**)

- [x] **API 통신 설정**
    - [x] Axios 인스턴스 설정 (`src/api/index.js`)
    - [x] 환경 변수 (`.env`) 기반 백엔드 API Base URL 설정

- [x] **프로젝트 구조 설정** (Vite 표준)
```
src/
├── views/             # 라우팅 페이지 (URL 경로에 따라 뷰 렌더링)
├── components/        # 재사용 가능한 UI 컴포넌트
├── api/               # API 호출 모듈
├── stores/            # Pinia 상태 관리 (전역 데이터)
├── router/            # Vue Router 설정
└── assets/            # 정적 파일 (CSS, Images)
### 13.1 단위 테스트 작성
- [ ] Pinia Store 단위 테스트
- [ ] Composables 단위 테스트

### 13.2 통합/E2E 테스트 작성
- [ ] Cypress 또는 Playwright 설정
- [ ] 핵심 사용자 플로우 E2E 테스트

### 13.3 빌드 및 배포 준비
- [ ] Vite 빌드 설정 (`npm run build`)
- [ ] **SSR/SSG 전략 수립**: 필요 시 적용

---

## 📊 진행 상황 요약 (FE)

| Phase | 항목 | 상태 |
|:---:|:---|:---:|
| 0 | 프로젝트 초기 설정 | ✅ 완료 |
| 1 | 시스템/공통 도메인 | ✅ 완료 |
| 2 | 회원 도메인 | ✅ 완료 |
| 3 | 게시판 도메인 | ✅ 완료 |
| 4 | 게시글 도메인 | ✅ 완료 |
| 5 | 댓글 도메인 | ✅ 완료 |
| 6 | 태그 도메인 | ✅ 완료 |
| 7 | 검색/통계 도메인 | ✅ 완료 |
| 8 | 알림 도메인 | ✅ 완료 |
| 9 | 포인트/상점 도메인 | ⬜ 미착수 |
| 10 | 파일 관리 도메인 | ⬜ 미착수 |
| 11 | 운영/관리자 도메인 | ✅ 완료 |
| 12 | 고급 기능 | ⬜ 미착수 |
| 13 | 테스트 및 배포 | ⬜ 미착수 |

---

## 🎯 작업 우선순위 (FE)

### 🔴 High Priority (MVP 필수)
1. Phase 2: 회원 도메인 (프로필 수정, 차단 등 나머지 기능)
2. Phase 1: 시스템/공통 도메인 (공통 코드, 설정)
3. Phase 6: 태그 도메인

### 🟡 Medium Priority
4. Phase 7: 검색/통계 도메인
5. Phase 11: 운영/관리자 도메인

### 🟢 Low Priority
6. Phase 9: 포인트/상점 도메인
7. Phase 10: 파일 관리 도메인
8. Phase 12: 고급 기능