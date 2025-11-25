# 프로젝트 단계별 작업 가이드

이 문서는 `DATABASE.md`, `API명세서.md`, `기능명세서.md`를 기반으로 작성된 단계별 구현 가이드입니다.

---

## 1. 환경 설정 및 프로젝트 초기화
- [ ] **프로젝트 구조 확인**: `backend` (Spring Boot), `frontend` (Vue.js) 디렉토리 구조 확인
- [ ] **의존성 확인**: `build.gradle` (Backend), `package.json` (Frontend) 확인 및 라이브러리 설치
- [ ] **데이터베이스 설정**: `application.yml` 또는 `application.properties`에 DB 연결 정보 설정 (MySQL/MariaDB 권장)

## 2. 데이터베이스 구축
- [ ] **스키마 생성**: `backend/DATABASE_INIT.sql` 또는 `DATABASE.md`를 참조하여 테이블 생성
    - [ ] 회원 관련 (users, user_settings, user_blocks, login_histories, refresh_tokens, password_histories, user_notification_settings, display_name_histories)
    - [ ] 게시판 관련 (boards, board_categories, board_subscriptions)
    - [ ] 게시글 관련 (posts, post_likes, post_tags, post_versions, draft_posts)
    - [ ] 댓글 관련 (comments, comment_closures, comment_likes, comment_versions)
    - [ ] 상호작용 관련 (notifications, messages, scraps, tags, popular_posts)
    - [ ] 관리자/운영 관련 (admins, reports, sanctions, ip_blocks, logs, global_configs, files)
    - [ ] 검색 관련 (search_statistics, search_personalization)
    - [ ] 포인트/상점 관련 (user_points, point_histories, shop_items, purchase_history)
    - [ ] 기타 (common_codes, common_code_details, view_histories, user_feeds, message_queue, ads, ad_click_logs)
- [ ] **인덱스 적용**: 각 테이블 정의서에 명시된 인덱스 생성

## 3. 회원 모듈 구현 (User)
- [ ] **도메인/엔티티 구현**: `User`, `UserSettings` 등 엔티티 클래스 작성
- [ ] **회원가입 (USER-001)**: `POST /auth/signup` 구현 (비밀번호 암호화, 이메일 중복 체크)
- [ ] **로그인 (USER-002)**: `POST /auth/login` 구현 (JWT 발급, Refresh Token 저장)
- [ ] **토큰 재발급 (USER-004)**: `POST /auth/refresh` 구현
- [ ] **이메일 인증 (USER-005)**: `POST /auth/verify-email` 구현
- [ ] **내 정보 조회/수정 (USER-009, USER-010)**: `GET /users/me`, `PUT /users/me` 구현
- [ ] **비밀번호 변경/찾기 (USER-006, USER-007, USER-008)**: 관련 API 구현
- [ ] **회원 탈퇴 (USER-011)**: Soft Delete 처리 및 토큰 폐기

## 4. 게시판 모듈 구현 (Board)
- [ ] **게시판 CRUD (BOARD-001 ~ BOARD-004)**: 게시판 생성, 조회, 수정 구현
- [ ] **카테고리 관리 (BOARD-008, BOARD-009)**: 게시판별 카테고리 조회 및 관리 구현
- [ ] **구독 시스템 (BOARD-005 ~ BOARD-007)**: 게시판 구독, 취소, 내 구독 목록 조회 구현

## 5. 게시글 모듈 구현 (Post)
- [ ] **게시글 CRUD (POST-001 ~ POST-005)**:
    - [ ] 게시글 작성 (태그 처리, 파일 연결, 포인트 적립)
    - [ ] 게시글 목록 조회 (필터링, 페이징)
    - [ ] 게시글 상세 조회 (조회수 증가, 열람 기록 저장)
    - [ ] 게시글 수정/삭제 (버전 관리 `post_versions` 저장)
- [ ] **좋아요/스크랩 (POST-006 ~ POST-010)**: 좋아요 및 스크랩 기능 구현
- [ ] **임시저장 (POST-011 ~ POST-013)**: 임시저장 목록, 저장, 삭제 구현
- [ ] **인기글/내 글 조회 (POST-014, POST-015)**: 인기글 집계 및 내 글 목록 구현

## 6. 댓글 모듈 구현 (Comment)
- [ ] **댓글 CRUD (COMMENT-001 ~ COMMENT-004)**:
    - [ ] 댓글 작성 (계층형 구조 `comment_closures` 처리, 알림 발송)
    - [ ] 댓글 목록 조회 (계층 구조로 변환하여 반환)
    - [ ] 댓글 수정/삭제 (버전 관리)
- [ ] **대댓글 (COMMENT-008)**: 대댓글 더보기 기능 구현
- [ ] **댓글 좋아요 (COMMENT-005, COMMENT-006)**: 댓글 좋아요 기능 구현

## 7. 상호작용 모듈 구현 (Interaction)
- [ ] **알림 시스템 (NOTI-001 ~ NOTI-004)**: 알림 목록, 읽음 처리, 설정 연동
- [ ] **쪽지 시스템 (MSG-001 ~ MSG-006)**: 쪽지 발송, 수신함/발신함, 상세 조회, 삭제 구현
- [ ] **신고 기능 (ETC-001)**: 게시글/댓글/사용자 신고 접수 구현

## 8. 검색 모듈 구현 (Search)
- [ ] **통합 검색 (SEARCH-001)**: Elasticsearch 연동 및 검색 API 구현
- [ ] **검색 기록 (SEARCH-003 ~ SEARCH-005)**: 인기 검색어, 최근 검색어 저장 및 조회

## 9. 포인트/상점 모듈 구현 (Point/Shop)
- [ ] **포인트 관리 (POINT-001, POINT-002)**: 포인트 조회 및 이력 조회
- [ ] **상점 기능 (SHOP-001 ~ SHOP-003)**: 아이템 목록, 구매 처리 (트랜잭션 주의), 구매 이력

## 10. 관리자 모듈 구현 (Admin)
- [ ] **관리자 권한 체크**: Interceptor 또는 Security Filter로 관리자 권한 확인
- [ ] **신고 처리 (ADMIN-001, ADMIN-002)**: 신고 목록 조회 및 승인/반려 처리
- [ ] **제재 관리 (ADMIN-003, ADMIN-004)**: 사용자 제재(정지 등) 및 이력 조회
- [ ] **콘텐츠 관리 (ADMIN-007, ADMIN-008)**: 게시글/댓글 강제 삭제/복구
- [ ] **IP 차단 (ADMIN-005, ADMIN-006)**: IP 차단 등록 및 해제

## 11. 배치 작업 구현 (Batch)
- [ ] **인기글 집계 (BATCH-001)**: 주기적으로 인기글 점수 계산하여 `popular_posts` 갱신
- [ ] **데이터 정리 (BATCH-002 ~ BATCH-004)**: 임시파일, 만료 토큰, 탈퇴 회원 데이터 정리
- [ ] **카운터 동기화 (BATCH-005)**: 비정규화된 카운터(좋아요 수 등) 정합성 보정
- [ ] **메시지 큐 처리 (BATCH-006)**: 발송 대기 중인 이메일/알림 처리

---

## 참고 사항
- **공통**: 모든 API는 `API명세서.md`의 응답 포맷을 준수해야 합니다.
- **보안**: 민감한 정보(비밀번호 등)는 반드시 암호화하여 저장해야 합니다.
- **성능**: 조회 쿼리 작성 시 `DATABASE.md`에 정의된 인덱스를 고려해야 합니다.
