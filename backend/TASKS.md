# 백엔드 개발 작업 계획서 (TASKS.md)

> 📅 작성일: 2025-11-25
> 🎯 목표: DATABASE.md와 FSD.md 기반 커뮤니티 서비스 백엔드 구축

---

## 📋 Phase 0: 프로젝트 초기 설정

### 0.1 개발 환경 구성
- [ ] **데이터베이스 설정**
  - [ ] MySQL/MariaDB 연결 설정 (`application.properties` 또는 `application.yml`)
  - [ ] JPA/Hibernate 설정
  - [ ] DDL Auto 전략 설정 (개발: `update`, 운영: `validate`)

- [ ] **필수 의존성 추가** (`build.gradle`)
  - [ ] Spring Data JPA
  - [ ] MySQL Connector
  - [ ] Lombok
  - [ ] Validation
  - [ ] Spring Security (JWT)
  - [ ] ModelMapper / MapStruct (DTO 변환)

- [ ] **프로젝트 구조 설정**
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
- [ ] **BaseEntity 추상 클래스**
  - [ ] `created_at`, `modified_at` 공통 컬럼 정의
  - [ ] `@EntityListeners(AuditingEntityListener.class)` 설정

- [ ] **공통 응답 DTO**
  - [ ] `ApiResponse<T>` (성공/실패 응답 포맷)
  - [ ] `PageResponse<T>` (페이징 응답)

- [ ] **공통 예외 처리**
  - [ ] `GlobalExceptionHandler` (@RestControllerAdvice)
  - [ ] Custom Exception 정의 (EntityNotFoundException, DuplicateException 등)

- [ ] **공통 유틸리티**
  - [ ] PasswordEncoder 설정
  - [ ] JWT 토큰 유틸리티
  - [ ] 날짜/시간 유틸리티

---

## 📋 Phase 1: 시스템/공통 도메인 구현

### 1.1 공통코드 관리 (common_codes, common_code_details)
- [ ] **Entity 작성**
  - [ ] `CommonCode` 엔티티 (PK: type_code)
  - [ ] `CommonCodeDetail` 엔티티 (PK: id, FK: type_code)
  - [ ] 양방향 연관관계 설정 (`@OneToMany`, `@ManyToOne`)

- [ ] **Repository**
  - [ ] `CommonCodeRepository`
  - [ ] `CommonCodeDetailRepository`
  - [ ] Custom Query 메서드 (활성화된 코드만 조회 등)

- [ ] **Service**
  - [ ] 코드 유형 CRUD
  - [ ] 상세 코드 CRUD
  - [ ] 코드 조회 (type_code별)

- [ ] **Controller (관리자 전용)**
  - [ ] `POST /api/admin/codes` - 코드 유형 생성
  - [ ] `GET /api/codes/{typeCode}` - 코드 상세 목록 조회
  - [ ] `PUT /api/admin/codes/{id}` - 코드 수정
  - [ ] `DELETE /api/admin/codes/{id}` - 코드 삭제

### 1.2 전역 설정 (global_configs)
- [ ] **Entity**
  - [ ] `GlobalConfig` (PK: config_key)

- [ ] **Repository & Service**
  - [ ] 설정 조회/수정 기능
  - [ ] 캐싱 적용 (`@Cacheable`)

- [ ] **Controller**
  - [ ] `GET /api/configs/{key}` - 설정 조회
  - [ ] `PUT /api/admin/configs/{key}` - 설정 수정

### 1.3 활동 기록 (logs)
- [ ] **Entity**
  - [ ] `Log` 엔티티

- [ ] **Service**
  - [ ] AOP를 통한 자동 로깅 (@Aspect)
  - [ ] 로그인/로그아웃 로그 기록
  - [ ] IP 주소 추출 유틸리티

- [ ] **Controller (관리자 전용)**
  - [ ] `GET /api/admin/logs` - 활동 로그 조회

---

## 📋 Phase 2: 회원 도메인 구현

### 2.1 회원 관리 (users)
- [ ] **Entity**
  - [ ] `User` 엔티티 (PK: user_id)
  - [ ] 비밀번호 암호화 (@PrePersist)

- [ ] **Repository**
  - [ ] `UserRepository`
  - [ ] `findByEmail()`, `existsByEmail()` 쿼리 메서드

- [ ] **DTO**
  - [ ] `UserSignupRequest`
  - [ ] `UserLoginRequest`
  - [ ] `UserResponse`
  - [ ] `UserUpdateRequest`

- [ ] **Service**
  - [ ] 회원 가입 (이메일 중복 체크)
  - [ ] 로그인 (JWT 토큰 발급)
  - [ ] 프로필 조회/수정
  - [ ] 회원 탈퇴 (Soft Delete 고려)

- [ ] **Controller**
  - [ ] `POST /api/auth/signup` - 회원 가입
  - [ ] `POST /api/auth/login` - 로그인
  - [ ] `GET /api/users/me` - 내 프로필 조회
  - [ ] `PUT /api/users/me` - 프로필 수정

### 2.2 Spring Security & JWT 설정
- [ ] **Security Configuration**
  - [ ] `SecurityConfig` 작성
  - [ ] JWT 필터 구현 (`JwtAuthenticationFilter`)
  - [ ] 인증 EntryPoint 설정

- [ ] **JWT Provider**
  - [ ] JWT 생성/검증 로직
  - [ ] Refresh Token 구현 (선택 사항)

### 2.3 회원 차단 (user_blocks)
- [ ] **Entity**
  - [ ] `UserBlock` 엔티티 (복합 PK: user_id, target_id)
  - [ ] `@IdClass` 또는 `@EmbeddedId` 사용

- [ ] **Service**
  - [ ] 사용자 차단/해제
  - [ ] 차단 목록 조회

- [ ] **Controller**
  - [ ] `POST /api/users/blocks/{targetId}` - 차단
  - [ ] `DELETE /api/users/blocks/{targetId}` - 차단 해제
  - [ ] `GET /api/users/blocks` - 차단 목록

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
- [ ] **Entity**
  - [ ] `Board` 엔티티 (PK: board_id)
  - [ ] `active_yn` 컬럼 (CHAR(1))

- [ ] **Repository**
  - [ ] `BoardRepository`
  - [ ] 활성화된 게시판만 조회 쿼리

- [ ] **Service**
  - [ ] 게시판 생성/수정/삭제 (관리자 권한)
  - [ ] 게시판 목록 조회

- [ ] **Controller**
  - [ ] `GET /api/boards` - 게시판 목록
  - [ ] `POST /api/admin/boards` - 게시판 생성
  - [ ] `PUT /api/admin/boards/{id}` - 게시판 수정
  - [ ] `DELETE /api/admin/boards/{id}` - 게시판 삭제

### 3.2 게시판 카테고리 (board_categories)
- [ ] **Entity**
  - [ ] `BoardCategory` 엔티티
  - [ ] `Board`와 다대일 관계 설정

- [ ] **Service**
  - [ ] 카테고리 CRUD

- [ ] **Controller**
  - [ ] `GET /api/boards/{boardId}/categories` - 카테고리 목록
  - [ ] `POST /api/admin/boards/{boardId}/categories` - 카테고리 생성

### 3.3 즐겨찾는 게시판 (favorite_boards)
- [ ] **Entity**
  - [ ] `FavoriteBoard` 엔티티 (복합 PK)

- [ ] **Service**
  - [ ] 즐겨찾기 추가/삭제

- [ ] **Controller**
  - [ ] `POST /api/boards/{boardId}/favorite` - 즐겨찾기 추가
  - [ ] `DELETE /api/boards/{boardId}/favorite` - 즐겨찾기 삭제
  - [ ] `GET /api/users/me/favorite-boards` - 내 즐겨찾기 목록

---

## 📋 Phase 4: 게시글 도메인 구현

### 4.1 게시글 기본 CRUD (posts)
- [ ] **Entity**
  - [ ] `Post` 엔티티 (PK: post_id)
  - [ ] `User`, `Board` FK 연관관계
  - [ ] `delete_yn`, `view_count`, `like_count` 기본값 설정

- [ ] **Repository**
  - [ ] `PostRepository`
  - [ ] Custom Query (페이징, 검색, 삭제되지 않은 게시글만 조회)

- [ ] **DTO**
  - [ ] `PostCreateRequest`
  - [ ] `PostUpdateRequest`
  - [ ] `PostResponse`
  - [ ] `PostListResponse`

- [ ] **Service**
  - [ ] 게시글 작성
  - [ ] 게시글 수정 (버전 이력 저장)
  - [ ] 게시글 삭제 (Soft Delete)
  - [ ] 게시글 조회 (조회수 증가)
  - [ ] 게시글 목록 조회 (페이징)

- [ ] **Controller**
  - [ ] `POST /api/boards/{boardId}/posts` - 게시글 작성
  - [ ] `GET /api/posts/{postId}` - 게시글 상세 조회
  - [ ] `PUT /api/posts/{postId}` - 게시글 수정
  - [ ] `DELETE /api/posts/{postId}` - 게시글 삭제
  - [ ] `GET /api/boards/{boardId}/posts` - 게시글 목록

### 4.2 게시글 조회수 증가 (view_count)
- [ ] **Service**
  - [ ] 조회수 증가 로직 (원자적 연산)
  - [ ] `@Transactional` 처리

- [ ] **열람 기록 (view_histories)**
  - [ ] `ViewHistory` 엔티티
  - [ ] 체류 시간 기록 로직

### 4.3 게시글 좋아요 (post_likes)
- [ ] **Entity**
  - [ ] `PostLike` 엔티티 (복합 PK)

- [ ] **Service**
  - [ ] 좋아요 토글 (추가/삭제)
  - [ ] `Post.like_count` 동기화

- [ ] **Controller**
  - [ ] `POST /api/posts/{postId}/like` - 좋아요 토글

### 4.4 게시글 스크랩 (scraps)
- [ ] **Entity**
  - [ ] `Scrap` 엔티티

- [ ] **Service**
  - [ ] 스크랩 추가/삭제
  - [ ] 내 스크랩 목록 조회

- [ ] **Controller**
  - [ ] `POST /api/posts/{postId}/scrap` - 스크랩
  - [ ] `GET /api/users/me/scraps` - 내 스크랩 목록

### 4.5 임시 저장 (draft_posts)
- [ ] **Entity**
  - [ ] `DraftPost` 엔티티

- [ ] **Service**
  - [ ] 임시 저장 CRUD

- [ ] **Controller**
  - [ ] `POST /api/drafts` - 임시 저장
  - [ ] `GET /api/drafts` - 임시 저장 목록

### 4.6 게시글 버전 관리 (post_versions)
- [ ] **Entity**
  - [ ] `PostVersion` 엔티티

- [ ] **Service**
  - [ ] 게시글 수정 시 이전 버전 저장

- [ ] **Controller (관리자 전용)**
  - [ ] `GET /api/admin/posts/{postId}/versions` - 수정 이력 조회

---

## 📋 Phase 5: 댓글 도메인 구현

### 5.1 댓글 CRUD (comments)
- [ ] **Entity**
  - [ ] `Comment` 엔티티 (PK: comment_id)
  - [ ] `parent_id` 자기 참조 FK (대댓글 구조)

- [ ] **Repository**
  - [ ] `CommentRepository`
  - [ ] 게시글별 댓글 조회 쿼리

- [ ] **DTO**
  - [ ] `CommentCreateRequest`
  - [ ] `CommentResponse` (대댓글 포함 계층 구조)

- [ ] **Service**
  - [ ] 댓글 작성
  - [ ] 댓글 수정/삭제 (Soft Delete)
  - [ ] 댓글 목록 조회 (계층 구조 변환)

- [ ] **Controller**
  - [ ] `POST /api/posts/{postId}/comments` - 댓글 작성
  - [ ] `PUT /api/comments/{commentId}` - 댓글 수정
  - [ ] `DELETE /api/comments/{commentId}` - 댓글 삭제
  - [ ] `GET /api/posts/{postId}/comments` - 댓글 목록

### 5.2 댓글 좋아요 (comment_likes)
- [ ] **Entity**
  - [ ] `CommentLike` 엔티티 (복합 PK)

- [ ] **Service**
  - [ ] 좋아요 토글

- [ ] **Controller**
  - [ ] `POST /api/comments/{commentId}/like` - 좋아요 토글

### 5.3 읽은 댓글 추적 (read_posts)
- [ ] **Entity**
  - [ ] `ReadPost` 엔티티

- [ ] **Service**
  - [ ] 읽은 위치 저장
  - [ ] 새 댓글 수 계산

---

## 📋 Phase 6: 태그 도메인 구현

### 6.1 태그 관리 (tags, post_tags)
- [ ] **Entity**
  - [ ] `Tag` 엔티티
  - [ ] `PostTag` 엔티티 (복합 PK)

- [ ] **Service**
  - [ ] 태그 자동 생성/업데이트
  - [ ] 태그별 게시글 조회

- [ ] **Controller**
  - [ ] `GET /api/tags` - 인기 태그 목록
  - [ ] `GET /api/tags/{tagId}/posts` - 태그별 게시글

---

## 📋 Phase 7: 검색/통계 도메인 구현

### 7.1 검색 기능
- [ ] **Service**
  - [ ] Full-Text Search 구현 (JPA @Query 또는 Elasticsearch)
  - [ ] 검색 통계 업데이트 (search_statistics)
  - [ ] 개인화 검색 이력 저장 (search_personalization)

- [ ] **Controller**
  - [ ] `GET /api/search?q={keyword}` - 통합 검색
  - [ ] `GET /api/search/popular` - 인기 검색어

### 7.2 인기글 시스템 (popular_posts)
- [ ] **Entity**
  - [ ] `PopularPost` 엔티티

- [ ] **Batch Job**
  - [ ] Spring Batch 또는 Scheduled Task
  - [ ] 일별/주별 인기글 집계

- [ ] **Controller**
  - [ ] `GET /api/posts/popular?type={DAILY|WEEKLY}` - 인기글 조회

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
- [ ] **Entity**
  - [ ] `UserNotificationSetting` 엔티티

- [ ] **Service**
  - [ ] 알림 타입별 수신 설정

- [ ] **Controller**
  - [ ] `GET /api/users/me/notification-settings` - 설정 조회
  - [ ] `PUT /api/users/me/notification-settings` - 설정 변경

---

## 📋 Phase 9: 포인트/상점 도메인 구현

### 9.1 포인트 시스템 (user_points, point_histories)
- [ ] **Entity**
  - [ ] `UserPoint` 엔티티
  - [ ] `PointHistory` 엔티티

- [ ] **Service**
  - [ ] 포인트 적립/차감
  - [ ] 포인트 이력 조회

- [ ] **Controller**
  - [ ] `GET /api/users/me/points` - 내 포인트 조회
  - [ ] `GET /api/users/me/points/history` - 포인트 이력

### 9.2 상점 (shop_items, purchase_history)
- [ ] **Entity**
  - [ ] `ShopItem` 엔티티
  - [ ] `PurchaseHistory` 엔티티

- [ ] **Service**
  - [ ] 아이템 목록 조회
  - [ ] 아이템 구매 (트랜잭션 처리)

- [ ] **Controller**
  - [ ] `GET /api/shop/items` - 아이템 목록
  - [ ] `POST /api/shop/items/{itemId}/purchase` - 구매

---

## 📋 Phase 10: 파일 관리 도메인 구현

### 10.1 파일 업로드 (files, temp_files)
- [ ] **Entity**
  - [ ] `File` 엔티티
  - [ ] `TempFile` 엔티티

- [ ] **Service**
  - [ ] 파일 업로드 (로컬 또는 S3)
  - [ ] 임시 파일 → 영구 파일 이동
  - [ ] 임시 파일 정리 배치 작업

- [ ] **Controller**
  - [ ] `POST /api/files/upload` - 파일 업로드
  - [ ] `GET /api/files/{fileId}` - 파일 다운로드

---

## 📋 Phase 11: 운영/관리자 도메인 구현

### 11.1 관리자 관리 (admins)
- [ ] **Entity**
  - [ ] `Admin` 엔티티

- [ ] **Service**
  - [ ] 관리자 등록/권한 관리

- [ ] **Controller (Super Admin 전용)**
  - [ ] `POST /api/admin/admins` - 관리자 등록

### 11.2 신고 처리 (reports)
- [ ] **Entity**
  - [ ] `Report` 엔티티

- [ ] **Service**
  - [ ] 신고 접수
  - [ ] 신고 처리 (관리자)

- [ ] **Controller**
  - [ ] `POST /api/reports` - 신고 접수
  - [ ] `GET /api/admin/reports` - 신고 목록 (관리자)
  - [ ] `PUT /api/admin/reports/{id}` - 신고 처리

### 11.3 IP 차단 (ip_blocks)
- [ ] **Entity**
  - [ ] `IpBlock` 엔티티

- [ ] **Service**
  - [ ] IP 차단/해제
  - [ ] Interceptor에서 IP 차단 체크

- [ ] **Controller (관리자 전용)**
  - [ ] `POST /api/admin/ip-blocks` - IP 차단
  - [ ] `DELETE /api/admin/ip-blocks/{ip}` - 차단 해제

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

**현재 작업**: Phase 0 - 프로젝트 초기 설정

**진행 순서**:
1. ✅ DATABASE.md, FSD.md 분석 완료
2. ⏭️ **다음**: `application.yml` 데이터베이스 설정
3. ⏭️ `build.gradle` 의존성 추가
4. ⏭️ 프로젝트 패키지 구조 생성
5. ⏭️ BaseEntity 및 공통 응답 DTO 작성

---

**📌 참고사항**:
- 각 Phase는 순차적으로 진행하되, 필요시 병렬 작업 가능
- 복합 PK는 `@EmbeddedId` 또는 `@IdClass` 사용
- Soft Delete는 `delete_yn` 컬럼 활용
- 모든 날짜는 `DATETIME` → Java `LocalDateTime` 매핑
- 외래키 제약조건은 코드 레벨에서 관리 (DB 레벨 선택 사항)