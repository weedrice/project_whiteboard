# 백엔드 개발 작업 계획서 (TASKS.md)

> 📅 작성일: 2025-11-25
> 🎯 목표: DATABASE.md와 FSD.md 기반 커뮤니티 서비스 백엔드 구축

---

## 📋 Phase 0: 프로젝트 초기 설정

### 0.1 개발 환경 구성
- [x] **데이터베이스 설정**
  - [x] PostgreSQL 연결 설정 (`application.yml`)
  - [x] JPA/Hibernate 설정
  - [x] DDL Auto 전략 설정 (개발: `update`, 운영: `validate`)

- [x] **필수 의존성 추가** (`build.gradle`)
  - [x] Spring Data JPA
  - [x] PostgreSQL Connector
  - [x] Lombok
  - [x] Validation
  - [x] Spring Security (JWT)
  - [ ] ModelMapper / MapStruct (DTO 변환)
  - [x] Querydsl 의존성 추가
  - [x] Spring Boot Starter Cache 및 Caffeine 의존성 추가

- [x] **프로젝트 구조 설정**
  ```
  src/main/java/com/weedrice/whiteboard/
  ├── domain/          # 도메인별 패키지
  │   ├── user/
  │   ├── board/
  │   ├── post/
  │   └── ...
  ├── global/          # 공통 기능
  │   ├── config/
  │   ├── exception/
  │   ├── common/
  │   └── security/
  └── WhiteboardApplication.java
  ```

### 0.2 공통 기반 코드 작성
- [x] **BaseEntity 추상 클래스**
  - [x] `created_at`, `modified_at` 공통 컬럼 정의
  - [x] `@EntityListeners(AuditingEntityListener.class)` 설정
  - [x] `global/common` 패키지로 이동 및 관련 엔티티 import 경로 수정

- [x] **공통 응답 DTO**
  - [x] `ApiResponse<T>` (성공/실패 응답 포맷)
  - [ ] `PageResponse<T>` (페이징 응답)

- [x] **공통 예외 처리**
  - [x] `GlobalExceptionHandler` (@RestControllerAdvice)
  - [x] Custom Exception 정의 (BusinessException, ErrorCode 등)

- [x] **공통 유틸리티**
  - [x] PasswordEncoder 설정
  - [x] JWT 토큰 유틸리티
  - [x] 날짜/시간 유틸리티

---

## 📋 Phase 1: 시스템/공통 도메인 구현

### 1.1 공통코드 관리 (common_codes, common_code_details)
- [x] **Entity 작성**
  - [x] `CommonCode` 엔티티 (PK: type_code)
  - [x] `CommonCodeDetail` 엔티티 (PK: id, FK: type_code)
  - [x] 양방향 연관관계 설정 (`@OneToMany`, `@ManyToOne`)

- [x] **Repository**
  - [x] `CommonCodeRepository`
  - [x] `CommonCodeDetailRepository`
  - [x] Custom Query 메서드 (활성화된 코드만 조회 등)

- [x] **Service**
  - [ ] 코드 유형 CRUD
  - [ ] 상세 코드 CRUD
  - [x] 코드 조회 (type_code별)

- [x] **Controller (관리자 전용)**
  - [ ] `POST /api/admin/codes` - 코드 유형 생성
  - [x] `GET /api/codes/{typeCode}` - 코드 상세 목록 조회
  - [ ] `PUT /api/admin/codes/{id}` - 코드 수정
  - [ ] `DELETE /api/admin/codes/{id}` - 코드 삭제

### 1.2 전역 설정 (global_configs)
- [x] **Entity**
  - [x] `GlobalConfig` (PK: config_key)

- [x] **Repository & Service**
  - [x] 설정 조회/수정 기능
  - [x] 캐싱 적용 (`@Cacheable`)

- [x] **Controller**
  - [x] `GET /api/configs/{key}` - 설정 조회
  - [x] `PUT /api/admin/configs/{key}` - 설정 수정

### 1.3 활동 기록 (logs)
- [x] **Entity**
  - [x] `Log` 엔티티

- [x] **Service**
  - [ ] AOP를 통한 자동 로깅 (@Aspect)
  - [x] 로그인/로그아웃 로그 기록 (수동 호출)
  - [ ] IP 주소 추출 유틸리티

- [ ] **Controller (관리자 전용)**
  - [ ] `GET /api/admin/logs` - 활동 로그 조회

---

## 📋 Phase 2: 회원 도메인 구현

### 2.1 회원 관리 (users)
- [x] **Entity**
  - [x] `User` 엔티티 (PK: user_id)
  - [x] 비밀번호 암호화 (서비스 레이어)

- [x] **Repository**
  - [x] `UserRepository`
  - [x] `findByLoginId()`, `existsByEmail()` 쿼리 메서드

- [x] **DTO**
  - [x] `UserSignupRequest`
  - [x] `UserLoginRequest`
  - [x] `UserResponse`
  - [x] `UserUpdateRequest`

- [x] **Service**
  - [x] 회원 가입 (이메일 중복 체크)
  - [x] 로그인 (JWT 토큰 발급)
  - [x] 프로필 조회/수정
  - [x] 회원 탈퇴 (Soft Delete 고려)

- [x] **Controller**
  - [x] `POST /api/auth/signup` - 회원 가입
  - [x] `POST /api/auth/login` - 로그인
  - [x] `GET /api/users/me` - 내 프로필 조회
  - [x] `PUT /api/users/me` - 프로필 수정

### 2.2 Spring Security & JWT 설정
- [x] **Security Configuration**
  - [x] `SecurityConfig` 작성
  - [x] JWT 필터 구현 (`JwtAuthenticationFilter`)
  - [x] 인증 EntryPoint 설정
  - [x] `@EnableMethodSecurity` 활성화

- [x] **JWT Provider**
  - [x] JWT 생성/검증 로직
  - [x] Refresh Token 구현
  - [x] `userId` 클레임 추가 및 `CustomUserDetails` 사용

### 2.3 회원 차단 (user_blocks)
- [x] **Entity**
  - [x] `UserBlock` 엔티티 (복합 PK: user_id, target_id)
  - [x] `@IdClass` 또는 `@EmbeddedId` 사용
- [x] **Service**
  - [x] 사용자 차단/해제
  - [x] 차단 목록 조회
- [x] **Controller**
  - [x] `POST /api/users/{targetId}/block` - 차단
  - [x] `DELETE /api/users/{targetId}/block` - 차단 해제
  - [x] `GET /api/users/me/blocks` - 차단 목록

### 2.4 제재 관리 (sanctions)
- [ ] **Entity**
  - [ ] `Sanction` 엔티티
  - [ ] `Admin` 엔티티 (FK 연결)

- [ ] **Service**
  - [ ] 제재 등록/해제
  - [ ] 로그인 시 제재 확인 로직

- [ ] **Controller (관리자 전용)**
  - [ ] `POST /api/admin/sanctions` - 제재 등록
  - [ ] `GET /api/admin/sanctions` - 제재 목록

---

## 📋 Phase 3: 게시판 도메인 구현

### 3.1 게시판 (boards)
- [x] **Entity**
  - [x] `Board` 엔티티 (PK: board_id)
  - [x] `active_yn` 컬럼 (CHAR(1))

- [x] **Repository**
  - [x] `BoardRepository`
  - [x] 활성화된 게시판만 조회 쿼리

- [x] **Service**
  - [ ] 게시판 생성/수정/삭제 (관리자 권한)
  - [x] 게시판 목록 조회

- [x] **Controller**
  - [x] `GET /api/boards` - 게시판 목록
  - [ ] `POST /api/admin/boards` - 게시판 생성
  - [ ] `PUT /api/admin/boards/{id}` - 게시판 수정
  - [ ] `DELETE /api/admin/boards/{id}` - 게시판 삭제

### 3.2 게시판 카테고리 (board_categories)
- [x] **Entity**
  - [x] `BoardCategory` 엔티티
  - [x] `Board`와 다대일 관계 설정

- [x] **Service**
  - [x] 카테고리 목록 조회
  - [ ] 카테고리 CRUD (관리자 권한)

- [x] **Controller**
  - [x] `GET /api/boards/{boardId}/categories` - 카테고리 목록
  - [ ] `POST /api/admin/boards/{boardId}/categories` - 카테고리 생성

### 3.3 게시판 구독 (board_subscriptions)
- [x] **Entity**
  - [x] `BoardSubscription` 엔티티 (복합 PK)
  - [x] `BoardSubscriptionId` 복합키 클래스

- [x] **Service**
  - [x] 구독/구독 취소

- [x] **Controller**
  - [x] `POST /api/boards/{boardId}/subscribe` - 구독
  - [x] `DELETE /api/boards/{boardId}/subscribe` - 구독 취소
  - [ ] `GET /api/users/me/subscriptions` - 내 구독 목록

---

## 📋 Phase 4: 게시글 도메인 구현

### 4.1 게시글 기본 CRUD (posts)
- [x] **Entity**
  - [x] `Post` 엔티티 (PK: post_id)
  - [x] `User`, `Board` FK 연관관계
  - [x] `delete_yn`, `view_count`, `like_count` 기본값 설정

- [x] **Repository**
  - [x] `PostRepository`
  - [x] Custom Query (페이징, 검색, 삭제되지 않은 게시글만 조회)
  - [x] Querydsl 설정 및 `PostRepositoryCustomImpl` 구현

- [x] **DTO**
  - [x] `PostCreateRequest`
  - [x] `PostUpdateRequest`
  - [x] `PostResponse`
  - [x] `PostListResponse`

- [x] **Service**
  - [x] 게시글 작성
  - [x] 게시글 수정 (버전 이력 저장)
  - [x] 게시글 삭제 (Soft Delete)
  - [x] 게시글 조회 (조회수 증가)
  - [x] 게시글 목록 조회 (페이징)

- [x] **Controller**
  - [x] `POST /api/boards/{boardId}/posts` - 게시글 작성
  - [x] `GET /api/posts/{postId}` - 게시글 상세 조회
  - [x] `PUT /api/posts/{postId}` - 게시글 수정
  - [x] `DELETE /api/posts/{postId}` - 게시글 삭제
  - [x] `GET /api/boards/{boardId}/posts` - 게시글 목록

### 4.2 게시글 조회수 증가 (view_count)
- [x] **Service**
  - [x] 조회수 증가 로직 (원자적 연산)
  - [x] `@Transactional` 처리

- [ ] **열람 기록 (view_histories)**
  - [ ] `ViewHistory` 엔티티
  - [ ] 체류 시간 기록 로직

### 4.3 게시글 좋아요 (post_likes)
- [x] **Entity**
  - [x] `PostLike` 엔티티 (복합 PK)

- [x] **Service**
  - [x] 좋아요 토글 (추가/삭제)
  - [x] `Post.like_count` 동기화

- [x] **Controller**
  - [x] `POST /api/posts/{postId}/like` - 좋아요 토글

### 4.4 게시글 스크랩 (scraps)
- [x] **Entity**
  - [x] `Scrap` 엔티티

- [x] **Service**
  - [x] 스크랩 추가/삭제
  - [ ] 내 스크랩 목록 조회

- [x] **Controller**
  - [x] `POST /api/posts/{postId}/scrap` - 스크랩
  - [ ] `GET /api/users/me/scraps` - 내 스크랩 목록

### 4.5 임시 저장 (draft_posts)
- [x] **Entity**
  - [x] `DraftPost` 엔티티

- [x] **Service**
  - [x] 임시 저장 CRUD

- [x] **Controller**
  - [x] `POST /api/drafts` - 임시 저장
  - [x] `GET /api/users/me/drafts` - 임시 저장 목록
  - [x] `GET /api/drafts/{draftId}` - 임시 저장 상세 조회
  - [x] `DELETE /api/drafts/{draftId}` - 임시 저장 삭제

### 4.6 게시글 버전 관리 (post_versions)
- [x] **Entity**
  - [x] `PostVersion` 엔티티

- [x] **Service**
  - [x] 게시글 수정 시 이전 버전 저장

- [x] **Controller (관리자 전용)**
  - [x] `GET /api/posts/{postId}/versions` - 수정 이력 조회

---

## 📋 Phase 5: 댓글 도메인 구현

### 5.1 댓글 CRUD (comments)
- [x] **Entity**
  - [x] `Comment` 엔티티 (PK: comment_id)
  - [x] `parent_id` 자기 참조 FK (대댓글 구조)

- [x] **Repository**
  - [x] `CommentRepository`
  - [x] 게시글별 댓글 조회 쿼리

- [x] **DTO**
  - [x] `CommentCreateRequest`
  - [x] `CommentResponse` (대댓글 포함 계층 구조)
  - [x] `CommentUpdateRequest`

- [x] **Service**
  - [x] 댓글 작성
  - [x] 댓글 수정/삭제 (Soft Delete)
  - [x] 댓글 목록 조회 (계층 구조 변환)

- [x] **Controller**
  - [x] `POST /api/posts/{postId}/comments` - 댓글 작성
  - [x] `PUT /api/comments/{commentId}` - 댓글 수정
  - [x] `DELETE /api/comments/{commentId}` - 댓글 삭제
  - [x] `GET /api/posts/{postId}/comments` - 댓글 목록
  - [x] `GET /api/comments/{commentId}/replies` - 대댓글 더보기

### 5.2 댓글 좋아요 (comment_likes)
- [x] **Entity**
  - [x] `CommentLike` 엔티티 (복합 PK)

- [x] **Service**
  - [x] 좋아요 토글

- [x] **Controller**
  - [x] `POST /api/comments/{commentId}/like` - 좋아요 토글

### 5.3 읽은 댓글 추적 (read_posts)
- [ ] **Entity**
  - [ ] `ReadPost` 엔티티

- [ ] **Service**
  - [ ] 읽은 위치 저장
  - [ ] 새 댓글 수 계산

---

## 📋 Phase 6: 태그 도메인 구현

### 6.1 태그 관리 (tags, post_tags)
- [x] **Entity**
  - [x] `Tag` 엔티티
  - [x] `PostTag` 엔티티 (복합 PK)

- [x] **Repository**
  - [x] `TagRepository`
  - [x] `PostTagRepository`

- [x] **Service**
  - [x] 태그 자동 생성/업데이트
  - [x] 태그별 게시글 조회 (PostService에서 구현)

- [ ] **Controller**
  - [ ] `GET /api/tags` - 인기 태그 목록
  - [ ] `GET /api/tags/{tagId}/posts` - 태그별 게시글

---

## 📋 Phase 7: 검색/통계 도메인 구현

### 7.1 검색 기능
- [x] **Entity**
  - [x] `SearchStatistic` 엔티티
  - [x] `SearchPersonalization` 엔티티

- [x] **Repository**
  - [x] `SearchStatisticRepository`
  - [x] `SearchPersonalizationRepository`

- [x] **Service**
  - [x] 검색 통계 업데이트 (search_statistics)
  - [x] 개인화 검색 이력 저장 (search_personalization)
  - [x] 최근 검색어 조회/삭제
  - [x] 통합 검색 (게시글)

- [x] **Controller**
  - [x] `GET /api/search?q={keyword}` - 통합 검색
  - [x] `GET /api/search/popular` - 인기 검색어
  - [x] `GET /api/search/recent` - 최근 검색어
  - [x] `DELETE /api/search/recent/{logId}` - 최근 검색어 삭제
  - [x] `DELETE /api/search/recent` - 최근 검색어 전체 삭제

### 7.2 인기글 시스템 (popular_posts)
- [x] **Entity**
  - [x] `PopularPost` 엔티티

- [ ] **Batch Job**
  - [ ] Spring Batch 또는 Scheduled Task
  - [ ] 일별/주별 인기글 집계

- [x] **Controller**
  - [x] `GET /api/posts/popular?type={DAILY|WEEKLY}` - 인기글 조회

---

## 📋 Phase 8: 알림 도메인 구현

### 8.1 알림 관리 (notifications)
- [ ] **Entity**
  - [ ] `Notification` 엔티티

- [ ] **Service**
  - [ ] 알림 생성 (댓글/좋아요/멘션 이벤트 리스너)
  - [ ] 알림 조회
  - [ ] 읽음 처리

- [ ] **Controller**
  - [ ] `GET /api/notifications` - 알림 목록
  - [ ] `PUT /api/notifications/{id}/read` - 읽음 처리

### 8.2 알림 설정 (user_notification_settings)
- [x] **Entity**
  - [x] `UserNotificationSetting` 엔티티

- [x] **Service**
  - [x] 알림 타입별 수신 설정

- [x] **Controller**
  - [x] `GET /api/users/me/notification-settings` - 설정 조회
  - [x] `PUT /api/users/me/notification-settings` - 설정 변경

---

## 📋 Phase 9: 포인트/상점 도메인 구현

### 9.1 포인트 시스템 (user_points, point_histories)
- [x] **Entity**
  - [x] `UserPoint` 엔티티
  - [x] `PointHistory` 엔티티

- [x] **Repository**
  - [x] `UserPointRepository`
  - [x] `PointHistoryRepository`

- [x] **Service**
  - [x] 포인트 적립/차감
  - [x] 포인트 이력 조회

- [x] **Controller**
  - [x] `GET /api/users/me/points` - 내 포인트 조회
  - [x] `GET /api/users/me/points/history` - 포인트 이력

### 9.2 상점 (shop_items, purchase_history)
- [x] **Entity**
  - [x] `ShopItem` 엔티티
  - [x] `PurchaseHistory` 엔티티

- [x] **Repository**
  - [x] `ShopItemRepository`
  - [x] `PurchaseHistoryRepository`

- [x] **Service**
  - [x] 아이템 목록 조회
  - [x] 아이템 구매 (트랜잭션 처리)
  - [x] 구매 이력 조회

- [x] **Controller**
  - [x] `GET /api/shop/items` - 아이템 목록
  - [x] `POST /api/shop/items/{itemId}/purchase` - 구매
  - [x] `GET /api/shop/me/purchases` - 내 구매 이력

---

## 📋 Phase 10: 파일 관리 도메인 구현

### 10.1 파일 업로드 (files, temp_files)
- [x] **Entity**
  - [x] `File` 엔티티
  - [ ] `TempFile` 엔티티 (files 테이블로 통합)

- [x] **Service**
  - [x] 파일 업로드 (로컬 또는 S3)
  - [x] 임시 파일 → 영구 파일 이동
  - [x] 임시 파일 정리 배치 작업

- [x] **Controller**
  - [x] `POST /api/files/upload` - 파일 업로드
  - [x] `GET /api/files/{fileId}` - 파일 다운로드

---

## 📋 Phase 11: 운영/관리자 도메인 구현

### 11.1 관리자 관리 (admins)
- [x] **Entity**
  - [x] `Admin` 엔티티

- [x] **Service**
  - [x] 관리자 등록/권한 관리

- [x] **Controller (Super Admin 전용)**
  - [x] `POST /api/admin/admins` - 관리자 등록
  - [x] `GET /api/admin/admins` - 관리자 목록 조회
  - [x] `PUT /api/admin/admins/{adminId}/deactivate` - 관리자 비활성화
  - [x] `PUT /api/admin/admins/{adminId}/activate` - 관리자 활성화

### 11.2 신고 처리 (reports)
- [x] **Entity**
  - [x] `Report` 엔티티

- [x] **Service**
  - [x] 신고 접수
  - [x] 신고 처리 (관리자)

- [x] **Controller**
  - [x] `POST /api/reports` - 신고 접수
  - [x] `GET /api/admin/reports` - 신고 목록 (관리자)
  - [x] `PUT /api/admin/reports/{id}` - 신고 처리

### 11.3 IP 차단 (ip_blocks)
- [x] **Entity**
  - [x] `IpBlock` 엔티티

- [x] **Service**
  - [x] IP 차단/해제
  - [ ] Interceptor에서 IP 차단 체크

- [x] **Controller (관리자 전용)**
  - [x] `POST /api/admin/ip-blocks` - IP 차단
  - [x] `DELETE /api/admin/ip-blocks/{ip}` - 차단 해제
  - [x] `GET /api/admin/ip-blocks` - IP 차단 목록 조회

---

## 📋 Phase 12: 고급 기능 구현

### 12.1 광고 시스템 (ads, ad_click_logs)
- [ ] **Entity**
  - [ ] `Ad` 엔티티
  - [ ] `AdClickLog` 엔티티

- [ ] **Service**
  - [ ] 광고 등록/관리
  - [ ] 클릭 로그 기록

- [ ] **Controller**
  - [ ] `GET /api/ads?placement={위치}` - 광고 조회
  - [ ] `POST /api/ads/{adId}/click` - 클릭 로그

### 12.2 사용자 피드 (user_feeds)
- [ ] **Entity**
  - [ ] `UserFeed` 엔티티

- [ ] **Service**
  - [ ] 피드 생성 로직 (팔로우/태그 기반)

- [ ] **Controller**
  - [ ] `GET /api/users/me/feeds` - 내 피드

### 12.3 메시지 큐 (message_queue)
- [ ] **Entity**
  - [ ] `MessageQueue` 엔티티

- [ ] **Service**
  - [ ] 이메일/SMS 발송 큐 처리
  - [ ] 비동기 메시지 발송 (@Async)

---

## 📋 Phase 13: 테스트 및 배포 준비

### 13.1 단위 테스트 작성
- [ ] Service 계층 테스트 (Mockito)
- [ ] Repository 테스트 (@DataJpaTest)

### 13.2 통합 테스트 작성
- [ ] Controller 테스트 (@WebMvcTest)
- [ ] End-to-End 테스트

### 13.3 API 문서화
- [ ] Swagger/OpenAPI 설정
- [ ] API 명세서 작성

### 13.4 성능 최적화
- [ ] N+1 쿼리 문제 해결 (Fetch Join)
- [ ] 인덱싱 전략 수립
- [ ] Redis 캐싱 적용 (선택 사항)

### 13.5 배포 준비
- [ ] application-prod.yml 설정
- [ ] 환경 변수 분리
- [ ] Docker/Docker Compose 설정 (선택 사항)

---

## 📊 진행 상황 요약

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
| 9 | 포인트/상점 도메인 | ✅ 완료 |
| 10 | 파일 관리 도메인 | ✅ 완료 |
| 11 | 운영/관리자 도메인 | ✅ 완료 |
| 12 | 고급 기능 | ⬜ 미착수 |
| 13 | 테스트 및 배포 | ⬜ 미착수 |

---

## 📝 작업 우선순위

### 🔴 High Priority (MVP 필수)
1. Phase 0: 프로젝트 초기 설정
2. Phase 1: 시스템/공통 도메인
3. Phase 2: 회원 도메인
4. Phase 3: 게시판 도메인
5. Phase 4: 게시글 도메인
6. Phase 5: 댓글 도메인

### 🟡 Medium Priority (핵심 기능)
7. Phase 6: 태그 도메인
8. Phase 7: 검색/통계 도메인
9. Phase 8: 알림 도메인
10. Phase 11: 운영/관리자 도메인

### 🟢 Low Priority (부가 기능)
11. Phase 9: 포인트/상점 도메인
12. Phase 10: 파일 관리 도메인
13. Phase 12: 고급 기능

---

## 🎯 다음 작업

**현재 작업**: Phase 11 - 운영/관리자 도메인 구현 완료

**진행 순서**:
1. ✅ DATABASE.md, FSD.md 분석 완료
2. ✅ `application.yml` 데이터베이스 설정
3. ✅ `build.gradle` 의존성 추가
4. ✅ 프로젝트 패키지 구조 생성
5. ✅ BaseEntity 및 공통 응답 DTO 작성
6. ✅ Phase 1: 시스템/공통 도메인 구현 완료
7. ✅ Phase 2: 회원 도메인 구현 완료
8. ✅ Phase 3: 게시판 도메인 구현 완료
9. ✅ Phase 4: 게시글 도메인 구현 완료
10. ✅ Phase 5: 댓글 도메인 구현 완료
11. ✅ Phase 6: 태그 도메인 구현 완료
12. ✅ Phase 7: 검색/통계 도메인 구현 완료
13. ✅ Phase 9: 포인트/상점 도메인 구현 완료
14. ✅ Phase 10: 파일 관리 도메인 구현 완료
15. ✅ Phase 11: 운영/관리자 도메인 구현 완료

**다음**: Phase 12 고급 기능 구현 시작.

---

**📌 참고사항**:
- 각 Phase는 순차적으로 진행하되, 필요시 병렬 작업 가능
- 복합 PK는 `@EmbeddedId` 또는 `@IdClass` 사용
- Soft Delete는 `delete_yn` 컬럼 활용
- 모든 날짜는 `DATETIME` → Java `LocalDateTime` 매핑
- 외래키 제약조건은 코드 레벨에서 관리 (DB 레벨 선택 사항)