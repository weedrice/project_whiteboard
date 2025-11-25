# 💻 프론트엔드 개발 작업 계획서 (TASKS_FE.md)

> 📅 작성일: 2025-11-25
> 🎯 목표: Nuxt 3/Vue 3 기반 사용자 친화적인 커뮤니티 UI 구축
> ⚙️ 기술 스택: **Nuxt 3, Vue 3, Pinia, TanStack Query (Vue Query), Tailwind CSS**

---

## 📋 Phase 0: 프로젝트 초기 설정 (Nuxt & Environment)

### 0.1 개발 환경 구성
- [ ] **Nuxt 3 프로젝트 초기화** (`nuxi init`)
- [ ] **필수 모듈/라이브러리 설치**
    - [ ] Pinia (상태 관리)
    - [ ] Tailwind CSS / PostCSS (스타일링)
    - [ ] ESLint, Prettier (코드 품질 관리)
    - [ ] HTML Editor 라이브러리 (예: **Tiptap for Vue**)

- [ ] **API 통신 설정**
    - [ ] Nuxt `$fetch` 또는 Axios 인스턴스 설정
    - [ ] 환경 변수 (`.env`) 기반 백엔드 API Base URL 설정

- [ ] **프로젝트 구조 설정** (Nuxt 3 표준)
```
src/
├── pages/             # 라우팅 페이지 (URL 경로에 따라 뷰 렌더링)
├── components/        # 재사용 가능한 UI 컴포넌트
├── composables/       # 재사용 가능한 Vue 로직 (API 호출/유틸리티)
├── stores/            # Pinia 상태 관리 (전역 데이터)
├── layouts/           # 페이지 레이아웃 (default, admin 등 뼈대 구조 정의)
└── middleware/        # 라우트 가드 (인증/권한 확인 등)
```

### 0.2 공통 기반 컴포넌트 및 상태 관리
- [ ] **Base Layout**
- [ ] `DefaultLayout.vue` (헤더, 푸터, 알림 아이콘, 메인 콘텐츠 영역)
- [ ] `AdminLayout.vue` (관리자 전용 네비게이션)

- [ ] **공통 UI 컴포넌트**
- [ ] `BaseButton`, `BaseInput`, `BaseModal`
- [ ] `Pagination` 컴포넌트 (Backend 0.2 `PageResponse<T>` 연동)

- [ ] **Pinia Store 초기화**
- [ ] `useAuthStore` (JWT 토큰 및 사용자 정보 저장)
- [ ] `useConfigStore` (전역 설정 캐시 및 관리)

- [ ] **공통 라우트 가드**
- [ ] `auth.ts` (로그인 사용자만 접근 가능)
- [ ] `admin.ts` (관리자 권한만 접근 가능 - Backend 11.1 연동)

---

## 📋 Phase 1: 시스템/공통 도메인 구현

### 1.1 공통 코드 연동 및 유틸리티
- [ ] **`CodeService` Composable**: 백엔드 API (`GET /api/codes/{typeCode}`) 호출 및 코드 값 조회/캐싱 (`TanStack Query` 적용)
- [ ] **`CodeNameDisplay` 컴포넌트**: 코드 값(예: 'POST')을 코드명(예: '게시글')으로 변환하여 표시

### 1.2 전역 설정 연동
- [ ] **`useConfigStore` / `useConfigQuery`**: 전역 설정 값 (`/api/configs/{key}`) 조회 및 상태 관리

### 1.3 활동 기록 (로그인/로그아웃)
- [ ] **로그아웃 버튼 구현**: Pinia 상태 초기화 및 백엔드 로그아웃 API 호출

---

## 📋 Phase 2: 회원 도메인 구현

### 2.1 회원 인증/프로필 (users)
- [ ] **`pages/signup.vue`**: 회원 가입 폼, 이메일 중복 검사 로직 연동
- [ ] **`pages/login.vue`**: 로그인 폼 및 JWT 획득/저장 로직 (Pinia)
- [ ] **`pages/profile/index.vue`**: 내 프로필 조회 (`GET /api/users/me`)
- [ ] **`ProfileEditor` 컴포넌트**: 프로필 정보 수정 폼 (`PUT /api/users/me`)

### 2.2 인증/JWT 처리
- [ ] **API 클라이언트 설정**: 모든 인증이 필요한 API 호출 시 JWT를 HTTP Header에 자동 포함

### 2.3 회원 차단 UI (user_blocks)
- [ ] **`pages/profile/blocks.vue`**: 차단 목록 조회
- [ ] **`BlockButton` 컴포넌트**: 사용자 프로필에서 차단/해제 토글 버튼 구현

### 2.4 제재 확인 (sanctions)
- [ ] **로그인 후 로직**: 로그인 성공 시 사용자 제재 여부 확인 및 제재 중이면 서비스 접근 제한 (Backend 2.4 연동)

---

## 📋 Phase 3: 게시판 도메인 구현

### 3.1 게시판 목록 (boards)
- [ ] **`BoardSideBar` 컴포넌트**: 활성화된 게시판 목록 조회 및 렌더링

### 3.2 게시판 카테고리
- [ ] **`CategoryTabs` 컴포넌트**: 게시판 내 카테고리 목록을 탭 형태로 표시

### 3.3 즐겨찾는 게시판
- [ ] **`FavoriteButton`**: 게시판 목록/상세에서 즐겨찾기 추가/삭제 토글 구현

---

## 📋 Phase 4: 게시글 도메인 구현

### 4.1 게시글 CRUD 기본
- [ ] **`pages/posts/[id].vue`**: 게시글 상세 페이지 (Backend 4.1 `GET /api/posts/{postId}`)
- [ ] **`pages/posts/write.vue`**: 게시글 작성/수정 페이지
- [ ] **HTML 에디터 (Tiptap 등) 초기화 및 데이터 바인딩**
- [ ] **게시글 목록 필터링/페이징 UI**

### 4.2 게시글 조회수 증가
- [ ] **`PostDetail` 페이지 로드 시**: `GET /api/posts/{postId}` 호출 후 **응답에 포함된 조회수** 렌더링 (Backend 4.2 원자적 연산)

### 4.3 게시글 좋아요/스크랩
- [ ] **`LikeButton` / `ScrapButton`**: 좋아요/스크랩 상태 표시 및 토글 API 호출

### 4.4 임시 저장
- [ ] **`PostEditor` 내 임시 저장 버튼**: `POST /api/drafts` 호출 및 저장 상태 피드백

### 4.5 게시글 버전 관리
- [ ] **`AdminPostVersionPage` (관리자)**: 수정 이력 목록 조회 및 원본 내용 확인 UI

---

## 📋 Phase 5: 댓글 도메인 구현

### 5.1 댓글 CRUD
- [ ] **`CommentList` 컴포넌트**: 댓글 목록 API 호출 및 **대댓글을 포함한 계층 구조**로 재귀적 렌더링 로직 구현
- [ ] **`CommentInput` 컴포넌트**: 댓글/대댓글 작성 폼 구현

### 5.2 댓글 좋아요
- [ ] **`CommentLikeButton`**: 좋아요 상태 표시 및 토글

### 5.3 읽은 댓글 추적
- [ ] **`NewCommentBadge` 컴포넌트**: `ReadPost` 정보를 활용하여 현재 게시글의 **새로운 댓글 수**를 계산하여 Badge로 표시

---

## 📋 Phase 6: 태그 도메인 구현

### 6.1 태그 UI
- [ ] **`PostTags` 컴포넌트**: 게시글 작성 시 태그 입력 UI 및 표시
- [ ] **`TagCloud` 컴포넌트**: 인기 태그 목록 조회 (`GET /api/tags`) 및 시각화

### 6.2 태그 기반 검색
- [ ] **`TagCloud` 클릭 이벤트**: 해당 태그로 검색 페이지 라우팅

---

## 📋 Phase 7: 검색/통계 도메인 구현

### 7.1 검색 기능
- [ ] **`GlobalSearchInput`**: 전역 검색 입력 컴포넌트
- [ ] **`pages/search.vue`**: 검색 결과 탭 구성 (게시글, 댓글, 사용자 등)
- [ ] **`PopularKeywordList`**: 검색 페이지에 인기 검색어 목록 표시

### 7.2 인기글 시스템
- [ ] **`PopularPosts` 컴포넌트**: 랭킹 타입(`DAILY`/`WEEKLY`)에 따라 인기글 목록 조회 및 캐시 데이터 렌더링

---

## 📋 Phase 8: 알림 도메인 구현

### 8.1 알림 관리
- [ ] **`NotificationIcon`**: 읽지 않은 알림 개수 표시 (Pinia 상태 연동)
- [ ] **`NotificationModal`**: 알림 목록 조회 및 클릭 시 `PUT /api/notifications/{id}/read` 호출

### 8.2 알림 설정
- [ ] **`pages/settings/notifications.vue`**: `notification_type`별 수신 설정 체크박스 UI 구현

---

## 📋 Phase 9: 포인트/상점 도메인 구현

### 9.1 포인트 시스템
- [ ] **`UserPointDisplay`**: 헤더 등에서 현재 포인트 조회 및 표시
- [ ] **`pages/mypage/point-history.vue`**: 포인트 이력 목록 조회

### 9.2 상점
- [ ] **`pages/shop.vue`**: 상점 아이템 목록 렌더링 (아이템 타입별 분류)
- [ ] **`PurchaseModal`**: 아이템 구매 확인 모달 및 `POST /api/shop/items/{itemId}/purchase` API 트랜잭션 호출

---

## 📋 Phase 10: 파일 관리 도메인 구현

### 10.1 파일 업로드 컴포넌트
- [ ] **`ImageUploader` 컴포넌트**: 파일 선택, 미리보기 기능, `POST /api/files/upload` 호출
- [ ] **에디터-파일 연동**: HTML 에디터 내 이미지 삽입 기능을 `ImageUploader`와 통합하여 임시 파일 업로드 및 URL 삽입 처리

---

## 📋 Phase 11: 운영/관리자 도메인 구현

### 11.1 관리자 페이지 초기 설정
- [ ] **Nuxt Middleware**: `admin.ts`를 통해 `AdminLayout` 접근 권한 검증 (Backend 11.1 `role` 확인)

### 11.2 신고/제재 처리
- [ ] **`pages/admin/reports.vue`**: 신고 목록 조회, 필터링, 상세 보기 UI
- [ ] **`SanctionModal`**: 신고 처리 후 사용자 제재 등록 폼 연동

### 11.3 IP 차단
- [ ] **`pages/admin/ip-blocks.vue`**: IP 차단 목록 조회 및 `POST/DELETE /api/admin/ip-blocks` API 연동 UI

---

## 📋 Phase 12: 고급 기능 구현

### 12.1 광고 시스템
- [ ] **`AdBanner` 컴포넌트**: `placement` prop을 받아 광고 조회 API 호출 및 렌더링
- [ ] **클릭 추적**: 광고 클릭 시 `POST /api/ads/{adId}/click` 비동기 호출

### 12.2 사용자 피드
- [ ] **`pages/feeds/index.vue`**: 개인화된 피드 목록 조회 및 렌더링

### 12.3 메시지 큐
- [ ] (프론트엔드 미구현, 백엔드 비동기 처리)

---

## 📋 Phase 13: 테스트 및 배포 준비

### 13.1 단위 테스트 작성
- [ ] Pinia Store (actions, getters) 단위 테스트
- [ ] Composables (API 호출 로직) 단위 테스트

### 13.2 통합/E2E 테스트 작성
- [ ] Cypress 또는 Playwright 설정
- [ ] 핵심 사용자 플로우 (회원가입 -> 로그인 -> 게시글 작성 -> 로그아웃) E2E 테스트

### 13.3 빌드 및 배포 준비
- [ ] Nuxt 3 빌드 설정 (`nuxt build`)
- [ ] **SSR/SSG 전략 수립**: SEO가 중요한 목록/상세 페이지는 SSR/SSG 적용

---

## 📊 진행 상황 요약 (FE)

| Phase | 항목 | 상태 |
|:---:|:---|:---:|
| 0 | 프로젝트 초기 설정 | ⬜ 미착수 |
| 1 | 시스템/공통 도메인 | ⬜ 미착수 |
| 2 | 회원 도메인 | ⬜ 미착수 |
| 3 | 게시판 도메인 | ⬜ 미착수 |
| 4 | 게시글 도메인 | ⬜ 미착수 |
| 5 | 댓글 도메인 | ⬜ 미착수 |
| 6 | 태그 도메인 | ⬜ 미착수 |
| 7 | 검색/통계 도메인 | ⬜ 미착수 |
| 8 | 알림 도메인 | ⬜ 미착수 |
| 9 | 포인트/상점 도메인 | ⬜ 미착수 |
| 10 | 파일 관리 도메인 | ⬜ 미착수 |
| 11 | 운영/관리자 도메인 | ⬜ 미착수 |
| 12 | 고급 기능 | ⬜ 미착수 |
| 13 | 테스트 및 배포 | ⬜ 미착수 |

---

## 🎯 작업 우선순위 (FE)

### 🔴 High Priority (MVP 필수, Backend Phase 0-5 연동)
1. Phase 0: 프로젝트 초기 설정 (Nuxt, Pinia, Tailwind)
2. Phase 2: 회원 도메인 (로그인, 가입, 인증 가드)
3. Phase 3: 게시판 도메인 (목록 및 네비게이션)
4. Phase 4: 게시글 도메인 (목록, 상세, **에디터 포함 작성**)
5. Phase 5: 댓글 도메인 (CRUD)

### 🟡 Medium Priority (핵심 기능, Backend Phase 6-8, 11 연동)
6. Phase 6: 태그 도메인
7. Phase 7: 검색/통계 도메인
8. Phase 8: 알림 도메인
9. Phase 11: 운영/관리자 도메인

### 🟢 Low Priority (부가 기능, Backend Phase 9-10, 12 연동)
10. Phase 1: 시스템/공통 도메인 (UI 통합)
11. Phase 9: 포인트/상점 도메인
12. Phase 10: 파일 관리 도메인 (에디터 연동 마무리)
13. Phase 12: 고급 기능

---

## ⏭️ 다음 작업

**현재 작업**: Phase 0 - 프로젝트 초기 설정

**진행 순서**:
1. ✅ 백엔드 작업 계획서 및 기능 명세서 분석 완료
2. ⏭️ **다음**: `nuxi init`으로 Nuxt 3 프로젝트 생성
3. ⏭️ `package.json`에 Pinia, Tailwind CSS 등 의존성 추가 및 설정
4. ⏭️ `DefaultLayout.vue` 및 `AdminLayout.vue` 뼈대 작성
5. ⏭️ Pinia Store 구조 및 인증 미들웨어 초기 설정