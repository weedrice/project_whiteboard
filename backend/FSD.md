# 📝 커뮤니티 서비스 백엔드 기능 정의서 (통합 최종판)

## 1. 회원 도메인 (Users & Auth)

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **1.1 회원 가입** | 이메일 중복 체크. 비밀번호 **단방향 암호화** 저장. 기본 프로필 생성 및 `users`에 기록. | `users` |
| **1.2 로그인/로그아웃** | 이메일/비밀번호 검증. 성공 시 JWT/세션 발급. **로그인/로그아웃 시 `logs` 테이블에 활동 기록.** | `users`, `logs` |
| **1.3 프로필 관리** | 닉네임, 프로필 이미지 URL, 이메일 수정. 프로필 수정 시 `logs` 기록. | `users`, `logs` |
| **1.4 회원 차단** | `user_blocks`에 복합 PK로 차단 관계 기록. 차단 회원의 서비스 접근 및 상호작용 원천 차단. | `user_blocks` |
| **1.5 회원 제재 확인** | 로그인 또는 주요 활동 시 `sanctions` 테이블을 조회하여 **이용 정지 여부** 확인. | `sanctions` |

---

## 2. 게시판 도메인 (Boards)

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **2.1 게시판 생성/수정/삭제** | 관리자 권한 확인 후 `boards` CRUD. | `boards`, `admins` |
| **2.2 게시판 카테고리 관리** | 게시판별 카테고리 (`board_categories`) 생성/수정/삭제. | `board_categories` |
| **2.3 즐겨찾는 게시판** | `favorite_boards`에 사용자 ID와 게시판 ID를 **복합 PK**로 저장 및 삭제. | `favorite_boards` |

---

## 3. 게시글 도메인 (Posts)

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **3.1 게시글 작성** | HTML 에디터 콘텐츠(`TEXT` 타입) 저장. 태그/첨부파일 포함. 임시 파일(`temp_files`)을 영구 파일(`files`)로 이동 처리. | `posts`, `post_tags`, `files`, `tags`, `logs` |
| **3.2 게시글 수정/이력 저장** | 수정 시 `posts` 업데이트 후, 이전 내용과 `VERSION_TYPE=UPDATE`를 `post_versions`에 기록. | `posts`, `post_versions`, `logs` |
| **3.3 게시글 삭제** | **Soft Delete (`delete_yn='Y'`) 처리.** 실제 데이터는 보존. | `posts` |
| **3.4 게시글 조회수 증가** | 게시글 조회 시 `view_count`를 **원자적(Atomic) 연산**으로 1 증가. `view_histories`에 조회 이력 및 체류 시간 기록. | `posts`, `view_histories` |
| **3.5 게시글 좋아요** | `post_likes`에 **복합 PK**로 저장/삭제(토글). `posts.like_count` 조정. | `post_likes`, `posts` |
| **3.6 게시글 스크랩** | `scraps` 테이블에 복합 PK로 기록. 중복 스크랩 방지. | `scraps` |
| **3.7 게시글 임시 저장** | 작성 중인 콘텐츠를 `draft_posts`에 저장. | `draft_posts` |

---

## 4. 댓글 도메인 (Comments)

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **4.1 댓글 작성** | `comments`에 저장. `parent_id`를 사용하여 대댓글 구조 지원. `content`는 **NOT NULL** 필수. | `comments`, `logs` |
| **4.2 댓글 수정/삭제** | `comments` 레코드 수정/삭제(Soft Delete). | `comments` |
| **4.3 댓글 좋아요** | `comment_likes`에 **복합 PK**로 저장/삭제(토글). `comments.like_count` 조정. | `comment_likes`, `comments` |
| **4.4 읽은 댓글 추적** | 게시글 조회 시 `read_posts`에 `last_read_comment_id`를 기록하여 새로운 댓글 수를 클라이언트에 제공. | `read_posts` |

---

## 5. 태그 도메인

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **5.1 태그 자동 생성/관리** | 게시글 작성 시 사용된 태그를 `tags`에 자동 생성하고 `post_count` 업데이트. `post_tags`에 게시글-태그 연결 기록. | `tags`, `post_tags` |
| **5.2 태그 기반 게시글 목록** | `post_tags`를 조인하여 특정 태그가 포함된 게시글 목록을 조회. | `post_tags`, `posts` |

---

## 6. 검색/통계 도메인

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **6.1 검색 기능** | `posts` 및 `tags` 테이블을 이용한 전문 검색(Full-Text Search) 제공. | `posts`, `tags` |
| **6.2 인기 검색어** | 검색 발생 시 `search_statistics.search_count`를 증가시켜 실시간 인기 검색어 제공. | `search_statistics` |
| **6.3 검색 개인화** | 로그인 사용자의 검색 이력을 `search_personalization`에 기록하여 개인화된 검색 결과를 제공. | `search_personalization` |

---

## 7. 알림 도메인 (Notifications)

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **7.1 알림 생성** | 댓글/좋아요/멘션 등 발생 시 `notifications`에 기록. **`source_type` (RELATED_TYPE) 필수 지정.** | `notifications` |
| **7.2 알림 읽음 처리** | 사용자 ID로 알림 조회 후 `is_read='Y'`로 변경. | `notifications` |
| **7.3 알림 설정(세분화)** | `user_notification_settings`를 통해 **`notification_type`별** 수신 여부(`is_enabled`)를 설정. (복합 PK) | `user_notification_settings` |

---

## 8. 포인트/상점 도메인

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **8.1 포인트 적립/소모** | 활동에 따른 `user_points` 잔액 변경. `point_histories`에 **`POINT_CHANGE_TYPE`** (`EARN`/`SPEND`) 포함 상세 이력 기록. | `user_points`, `point_histories` |
| **8.2 상점 아이템 조회** | `shop_items`에서 `item_type`별 활성화된(`is_active='Y'`) 아이템 목록 제공. | `shop_items` |
| **8.3 상점 아이템 구매** | 잔액 확인 후 `user_points` 차감. `purchase_history`에 구매 이력 기록. | `purchase_history`, `user_points`, `point_histories` |

---

## 9. 광고 도메인

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **9.1 광고 등록/관리** | 관리자용 광고 (`ads`) CRUD 및 노출 위치/기간 관리. | `ads` |
| **9.2 광고 클릭 추적** | `ad_click_logs`에 광고 ID, **사용자 ID (익명 가능)**, IP 주소 등을 기록하여 클릭 통계 추적. | `ad_click_logs` |

---

## 10. 운영/관리자 도메인

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **10.1 운영자 관리** | 관리자 계정 생성 및 역할(`ADMIN_ROLE`) 설정. `board_id`가 NULL이면 전역 관리자. | `admins` |
| **10.2 신고 처리** | `reports`에 신고 접수. 관리자 처리 후 `REPORT_STATUS` 변경 및 제재(M3) 실행. | `reports` |
| **10.3 IP 차단** | `ip_blocks`에 IP 등록 및 해제. 악의적인 IP 접근 차단. | `ip_blocks` |
| **10.4 사용자 제재 등록** | 신고 처리 결과에 따라 관리자가 `sanctions` 테이블에 `SANCTION_TYPE` (TEMP_BAN, PERM_BAN 등)을 기록. | `sanctions` |

---

## 11. 시스템/공통 도메인

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **11.1 공통 코드 관리** | 관리자 페이지에서 `common_codes` / `common_code_details`를 **CRUD**하여 모든 시스템 유형 코드 관리. | `common_codes`, `common_code_details` |
| **11.2 파일 정리** | 배치 작업을 통해 `temp_files`의 `expiration_time`이 지난 임시 파일을 물리적으로 삭제. | `files`, `temp_files` |
| **11.3 전역 환경설정** | `global_configs`를 통해 서비스 전체에 영향을 미치는 설정 값(예: 게시글 작성 최소 포인트)을 동적으로 관리. | `global_configs` |

---

## 12. 인기글 시스템

| 기능 | 세부 동작 및 로직 | 관련 테이블 |
| :--- | :--- | :--- |
| **12.1 인기글 집계 및 캐시** | **배치 작업을 통해 미리 계산**된 랭킹 점수(`score`)와 순위(`rank`)를 `popular_posts`에 저장(캐시). | `popular_posts` |
| **12.2 인기글 조회** | `popular_posts`를 조회 기준(`RANKING_TYPE`: DAILY/WEEKLY 등)에 따라 제공. | `popular_posts` |