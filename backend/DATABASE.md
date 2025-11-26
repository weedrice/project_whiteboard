# 커뮤니티 서비스 전체 테이블 정의서 (v2)

## 변경 이력
| 버전 | 날짜 | 변경 내용 |
|-----|------|----------|
| v2 | 2025-11-25 | users 구조 변경(이메일 인증, 마지막 로그인, 탈퇴일시), display_name_histories 추가, boards 아이콘/배너 추가, board_categories 활성화 추가, search_statistics 일자별 집계, point_histories 설명 추가, reports 중복방지, Closure Table 적용, 인증/보안 테이블 추가, messages 추가, 인덱스 정의, FK 오류 수정, temp_files/read_posts 삭제 |
| v1 | 2025-11-25 | 최초 작성 |

---

## 공통 규칙

### 공통 컬럼 (전 테이블 공통)
```text
created_at   DATETIME   생성일
modified_at  DATETIME   수정일
```

### Boolean 컬럼 네이밍 규칙
- 모든 Boolean 성격 컬럼은 `is_` 접두사 사용
- 값은 `CHAR(1)`로 'Y'/'N' 사용

### 삭제 정책
| 정책 | 적용 테이블 | 설명 |
|-----|-----------|------|
| Soft Delete | posts, comments, messages | is_deleted 플래그 사용 |
| Hard Delete | files, refresh_tokens, 기타 | 물리적 삭제 |

### 비정규화 카운터
`like_count`, `view_count`, `comment_count`, `post_count` 등의 집계 컬럼은 성능을 위한 비정규화 적용.

**구현 시 주의사항:**
- INSERT/DELETE 시 애플리케이션 또는 트리거에서 카운터 증감
- 동시성: `UPDATE posts SET like_count = like_count + 1` 형태 사용
- 정합성: 주기적 배치로 실제 COUNT와 동기화 권장

---

## 테이블 목록 (총 45개)

### 회원/인증 (8개)
1. users - 회원
2. user_settings - 사용자 설정
3. user_blocks - 차단
4. login_histories - 로그인 이력
5. refresh_tokens - JWT Refresh Token
6. password_histories - 비밀번호 변경 이력
7. user_notification_settings - 알림 설정
8. display_name_histories - 닉네임 변경 이력

### 게시판/게시글 (8개)
9. boards - 게시판
10. board_categories - 게시판 카테고리
11. board_subscriptions - 게시판 구독
12. posts - 게시글
13. post_likes - 게시글 좋아요
14. post_tags - 태그 연결
15. post_versions - 게시글 버전 관리
16. draft_posts - 임시 저장 게시글

### 댓글 (4개)
17. comments - 댓글
18. comment_closures - 댓글 계층 관계
19. comment_likes - 댓글 좋아요
20. comment_versions - 댓글 버전 관리

### 상호작용 (5개)
21. notifications - 알림
22. messages - 쪽지
23. scraps - 스크랩
24. tags - 태그 마스터
25. popular_posts - 인기글 캐시

### 관리/운영 (7개)
26. admins - 관리자
27. reports - 신고
28. sanctions - 제재 기록
29. ip_blocks - IP 차단
30. logs - 활동 기록
31. global_configs - 전역 설정
32. files - 첨부파일

### 검색 (2개)
33. search_statistics - 검색 통계
34. search_personalization - 검색 개인화

### 포인트/상점 (4개)
35. user_points - 사용자 포인트
36. point_histories - 포인트 이력
37. shop_items - 상점 아이템
38. purchase_history - 구매 기록

### 기타 (7개)
39. common_codes - 공통코드
40. common_code_details - 상세코드
41. view_histories - 열람 기록
42. user_feeds - 사용자 피드
43. message_queue - 발송 메세지 큐
44. ads - 광고
45. ad_click_logs - 광고 클릭 로그

---

## 1. 회원 (users)

| 컬럼명            | 타입          | PK | FK  | NULL     | 설명                                    |
|------------------|--------------|----|----- |----------|----------------------------------------|
| user_id          | BIGINT       | PK |     | NOT NULL | 회원 고유 ID                            |
| login_id         | VARCHAR(30)  |    |     | NOT NULL | 로그인/멘션용 고유 식별자 (UNIQUE)        |
| display_name     | VARCHAR(50)  |    |     | NOT NULL | 화면 표시명                             |
| password         | VARCHAR(255) |    |     | NOT NULL | 비밀번호 (BCrypt 해시)                   |
| email            | VARCHAR(100) |    |     | NOT NULL | 이메일 (UNIQUE)                         |
| profile_image_url| VARCHAR(255) |    |     | YES      | 프로필 이미지 URL                        |
| status           | VARCHAR(20)  |    |     | NOT NULL | 계정 상태 (ACTIVE/SUSPENDED/DELETED)    |
| is_email_verified| CHAR(1)      |    |     | NOT NULL | 이메일 인증 여부 (Y/N)                   |
| last_login_at    | DATETIME     |    |     | YES      | 마지막 로그인 일시                       |
| deleted_at       | DATETIME     |    |     | YES      | 탈퇴 일시 (탈퇴 후 보존 기간 계산용)      |
| created_at       | DATETIME     |    |     | NOT NULL | 생성일                                  |
| modified_at      | DATETIME     |    |     | NOT NULL | 수정일                                  |

**인덱스:**
```sql
CREATE UNIQUE INDEX uk_users_login_id ON users(login_id);
CREATE UNIQUE INDEX uk_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_deleted ON users(deleted_at);
```

---

## 2. 사용자 설정 (user_settings)

| 컬럼명          | 타입          | PK | FK            | NULL     | 설명                          |
|----------------|--------------|----|--------------  |----------|------------------------------|
| user_id        | BIGINT       | PK | users.user_id | NOT NULL | 사용자 ID                     |
| theme          | VARCHAR(20)  |    |               | NOT NULL | 테마 (LIGHT/DARK/SYSTEM)      |
| language       | VARCHAR(10)  |    |               | NOT NULL | 언어 (ko/en 등)               |
| timezone       | VARCHAR(50)  |    |               | NOT NULL | 타임존 (Asia/Seoul 등)        |
| hide_nsfw      | CHAR(1)      |    |               | NOT NULL | NSFW 콘텐츠 숨김 (Y/N)        |
| created_at     | DATETIME     |    |               | NOT NULL | 생성일                        |
| modified_at    | DATETIME     |    |               | NOT NULL | 수정일                        |

---

## 3. 차단 (user_blocks)

| 컬럼명     | 타입      | PK | FK            | NULL     | 설명            |
|-----------|----------|----|---------------|----------|-----------------|
| relation_id| BIGINT  | PK |               | NOT NULL | 차단 관계 ID    |
| user_id   | BIGINT   |    | users.user_id | NOT NULL | 차단한 사용자   |
| target_id | BIGINT   |    | users.user_id | NOT NULL | 차단 대상 사용자|
| created_at| DATETIME |    |               | NOT NULL | 생성일          |
| modified_at|DATETIME |    |               | NOT NULL | 수정일          |

**인덱스:**
```sql
CREATE UNIQUE INDEX uk_user_blocks ON user_blocks(user_id, target_id);
CREATE INDEX idx_user_blocks_target ON user_blocks(target_id);
```

---

## 4. 로그인 이력 (login_histories)

| 컬럼명        | 타입          | PK | FK            | NULL     | 설명                                  |
|--------------|--------------|----|--------------  |----------|--------------------------------------|
| history_id   | BIGINT       | PK |               | NOT NULL | 로그인 이력 ID                        |
| user_id      | BIGINT       |    | users.user_id | YES      | 사용자 ID (실패 시 NULL 가능)          |
| login_id     | VARCHAR(30)  |    |               | NOT NULL | 로그인 시도한 ID                       |
| ip_address   | VARCHAR(45)  |    |               | NOT NULL | IP 주소                               |
| user_agent   | VARCHAR(500) |    |               | YES      | User-Agent                            |
| is_success   | CHAR(1)      |    |               | NOT NULL | 성공 여부 (Y/N)                        |
| failure_reason| VARCHAR(50) |    |               | YES      | 실패 사유 (WRONG_PASSWORD/ACCOUNT_LOCKED 등) |
| created_at   | DATETIME     |    |               | NOT NULL | 생성일                                 |
| modified_at  | DATETIME     |    |               | NOT NULL | 수정일                                 |

**인덱스:**
```sql
CREATE INDEX idx_login_histories_user ON login_histories(user_id, created_at DESC);
CREATE INDEX idx_login_histories_ip ON login_histories(ip_address, created_at DESC);
CREATE INDEX idx_login_histories_login_id ON login_histories(login_id, created_at DESC);
```

---

## 5. Refresh Token (refresh_tokens)

| 컬럼명        | 타입          | PK | FK            | NULL     | 설명                          |
|--------------|--------------|----|--------------  |----------|------------------------------|
| token_id     | BIGINT       | PK |               | NOT NULL | 토큰 ID                       |
| user_id      | BIGINT       |    | users.user_id | NOT NULL | 사용자 ID                     |
| token_hash   | VARCHAR(255) |    |               | NOT NULL | Refresh Token 해시값 (SHA-256)|
| device_info  | VARCHAR(255) |    |               | YES      | 기기 정보 (User-Agent 파싱)    |
| ip_address   | VARCHAR(45)  |    |               | NOT NULL | IP 주소                       |
| expires_at   | DATETIME     |    |               | NOT NULL | 만료 일시                      |
| is_revoked   | CHAR(1)      |    |               | NOT NULL | 폐기 여부 (Y/N)               |
| created_at   | DATETIME     |    |               | NOT NULL | 생성일                        |
| modified_at  | DATETIME     |    |               | NOT NULL | 수정일                        |

**인덱스:**
```sql
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id, is_revoked);
CREATE UNIQUE INDEX uk_refresh_tokens_hash ON refresh_tokens(token_hash); -- 중복 토큰 방지
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);
```

**삭제 정책:** Hard Delete

---

## 6. 비밀번호 변경 이력 (password_histories)

| 컬럼명        | 타입          | PK | FK            | NULL     | 설명                          |
|--------------|--------------|----|--------------  |----------|------------------------------|
| history_id   | BIGINT       | PK |               | NOT NULL | 이력 ID                       |
| user_id      | BIGINT       |    | users.user_id | NOT NULL | 사용자 ID                     |
| password_hash| VARCHAR(255) |    |               | NOT NULL | 비밀번호 해시값 (BCrypt)       |
| created_at   | DATETIME     |    |               | NOT NULL | 생성일                        |
| modified_at  | DATETIME     |    |               | NOT NULL | 수정일                        |

**인덱스:**
```sql
CREATE INDEX idx_password_histories_user ON password_histories(user_id, created_at DESC);
```

**용도:** 최근 N개 비밀번호 재사용 방지 정책 적용 시 활용

---

## 7. 알림 설정 (user_notification_settings)

| 컬럼명           | 타입          | PK | FK            | NULL     | 설명                      |
|-----------------|--------------|----|---------------|----------|---------------------------|
| user_id         | BIGINT       | PK | users.user_id | NOT NULL | 사용자 ID                 |
| notification_type|VARCHAR(50)  | PK |               | NOT NULL | 알림 타입(COMMENT/LIKE/MENTION 등)|
| is_enabled      | CHAR(1)      |    |               | NOT NULL | 사용 여부(Y/N)            |
| created_at      | DATETIME     |    |               | NOT NULL | 생성일                    |
| modified_at     | DATETIME     |    |               | NOT NULL | 수정일                    |

---

## 8. 닉네임 변경 이력 (display_name_histories)

| 컬럼명        | 타입          | PK | FK            | NULL     | 설명              |
|--------------|--------------|----|---------------|----------|-------------------|
| history_id   | BIGINT       | PK |               | NOT NULL | 이력 ID           |
| user_id      | BIGINT       |    | users.user_id | NOT NULL | 사용자 ID         |
| previous_name| VARCHAR(50)  |    |               | NOT NULL | 변경 전 닉네임     |
| new_name     | VARCHAR(50)  |    |               | NOT NULL | 변경 후 닉네임     |
| changed_at   | DATETIME     |    |               | NOT NULL | 변경 일시          |
| created_at   | DATETIME     |    |               | NOT NULL | 생성일            |
| modified_at  | DATETIME     |    |               | NOT NULL | 수정일            |

**인덱스:**
```sql
CREATE INDEX idx_display_name_histories_user ON display_name_histories(user_id, changed_at DESC);
CREATE INDEX idx_display_name_histories_name ON display_name_histories(previous_name);
```

---

## 9. 게시판 (boards)

| 컬럼명       | 타입          | PK | FK            | NULL     | 설명                    |
|-------------|--------------|----|--------------  |----------|------------------------|
| board_id    | BIGINT       | PK |               | NOT NULL | 게시판 ID               |
| board_name  | VARCHAR(100) |    |               | NOT NULL | 게시판 이름              |
| description | VARCHAR(255) |    |               | YES      | 설명                    |
| creator_id  | BIGINT       |    | users.user_id | NOT NULL | 게시판 생성자 ID         |
| icon_url    | VARCHAR(255) |    |               | YES      | 게시판 아이콘 URL        |
| banner_url  | VARCHAR(255) |    |               | YES      | 게시판 배너 URL          |
| sort_order  | INT          |    |               | NOT NULL | 정렬 순서               |
| is_active   | CHAR(1)      |    |               | NOT NULL | 활성 여부 (Y/N)          |
| allow_nsfw  | CHAR(1)      |    |               | NOT NULL | NSFW 허용 여부 (Y/N)     |
| created_at  | DATETIME     |    |               | NOT NULL | 생성일                  |
| modified_at | DATETIME     |    |               | NOT NULL | 수정일                  |

**인덱스:**
```sql
CREATE INDEX idx_boards_active ON boards(is_active, sort_order);
CREATE INDEX idx_boards_creator ON boards(creator_id);
```

---

## 10. 게시판 카테고리 (board_categories)

| 컬럼명      | 타입          | PK | FK              | NULL     | 설명             |
|------------|--------------|----|-----------------|----------|------------------|
| category_id| BIGINT       | PK |                 | NOT NULL | 카테고리 ID      |
| board_id   | BIGINT       |    | boards.board_id | NOT NULL | 게시판 ID        |
| name       | VARCHAR(100) |    |                 | NOT NULL | 카테고리명       |
| sort_order | INT          |    |                 | NOT NULL | 정렬 순서        |
| is_active  | CHAR(1)      |    |                 | NOT NULL | 활성 여부 (Y/N)  |
| created_at | DATETIME     |    |                 | NOT NULL | 생성일           |
| modified_at| DATETIME     |    |                 | NOT NULL | 수정일           |

**인덱스:**
```sql
CREATE INDEX idx_board_categories_board ON board_categories(board_id, is_active, sort_order);
```

---

## 11. 게시판 구독 (board_subscriptions)

| 컬럼명   | 타입        | PK | FK              | NULL     | 설명                              |
|---------|------------|----|-----------------|----------|----------------------------------|
| user_id | BIGINT     | PK | users.user_id   | NOT NULL | 사용자 ID                         |
| board_id| BIGINT     | PK | boards.board_id | NOT NULL | 게시판 ID                         |
| role    | VARCHAR(20)|    |                 | NOT NULL | 역할 (MEMBER/MODERATOR/BANNED)   |
| created_at | DATETIME |  |                 | NOT NULL | 생성일                            |
| modified_at|DATETIME  |  |                 | NOT NULL | 수정일                            |

**인덱스:**
```sql
CREATE INDEX idx_board_subscriptions_board ON board_subscriptions(board_id, role);
CREATE INDEX idx_board_subscriptions_user ON board_subscriptions(user_id);
```

---

## 12. 게시글 (posts)

| 컬럼명        | 타입          | PK | FK                        | NULL     | 설명                  |
|--------------|--------------|----|--------------------------  |----------|----------------------|
| post_id      | BIGINT       | PK |                           | NOT NULL | 게시글 ID             |
| board_id     | BIGINT       |    | boards.board_id           | NOT NULL | 게시판 ID             |
| user_id      | BIGINT       |    | users.user_id             | NOT NULL | 작성자 ID             |
| title        | VARCHAR(200) |    |                           | NOT NULL | 제목                  |
| contents     | TEXT         |    |                           | NOT NULL | 내용                  |
| view_count   | INT          |    |                           | NOT NULL | 조회수 (비정규화)      |
| like_count   | INT          |    |                           | NOT NULL | 좋아요 수 (비정규화)   |
| comment_count| INT          |    |                           | NOT NULL | 댓글 수 (비정규화)     |
| is_deleted   | CHAR(1)      |    |                           | NOT NULL | 삭제 여부 (Y/N)        |
| is_notice    | CHAR(1)      |    |                           | NOT NULL | 공지사항 여부 (Y/N)    |
| is_nsfw      | CHAR(1)      |    |                           | NOT NULL | 성인 콘텐츠 여부 (Y/N) |
| is_spoiler   | CHAR(1)      |    |                           | NOT NULL | 스포일러 여부 (Y/N)    |
| category_id  | BIGINT       |    | board_categories.category_id | YES   | 카테고리              |
| created_at   | DATETIME     |    |                           | NOT NULL | 생성일                |
| modified_at  | DATETIME     |    |                           | NOT NULL | 수정일                |

**인덱스:**
```sql
CREATE INDEX idx_posts_board_created ON posts(board_id, is_deleted, created_at DESC);
CREATE INDEX idx_posts_board_notice ON posts(board_id, is_notice, created_at DESC);
CREATE INDEX idx_posts_user ON posts(user_id, is_deleted, created_at DESC);
CREATE INDEX idx_posts_category ON posts(category_id);
CREATE INDEX idx_posts_popular ON posts(board_id, is_deleted, like_count DESC, created_at DESC);
```

**삭제 정책:** Soft Delete

---

## 13. 게시글 좋아요 (post_likes)

| 컬럼명   | 타입      | PK | FK                | NULL     | 설명          |
|---------|----------|----|-------------------|----------|---------------|
| post_id | BIGINT   | PK | posts.post_id     | NOT NULL | 게시글 ID     |
| user_id | BIGINT   | PK | users.user_id     | NOT NULL | 사용자 ID     |
| created_at | DATETIME |  |                   | NOT NULL | 생성일        |
| modified_at| DATETIME | |                   | NOT NULL | 수정일        |

**인덱스:**
```sql
CREATE INDEX idx_post_likes_user ON post_likes(user_id);
```

---

## 14. 태그 연결 (post_tags)

| 컬럼명   | 타입      | PK | FK                | NULL     | 설명      |
|---------|----------|----|-------------------|----------|-----------|
| post_id | BIGINT   | PK | posts.post_id     | NOT NULL | 게시글 ID |
| tag_id  | BIGINT   | PK | tags.tag_id       | NOT NULL | 태그 ID   |
| created_at | DATETIME |  |                   | NOT NULL | 생성일    |
| modified_at|DATETIME  |  |                   | NOT NULL | 수정일    |

**인덱스:**
```sql
CREATE INDEX idx_post_tags_tag ON post_tags(tag_id);
```

---

## 15. 게시글 버전 관리 (post_versions)

| 컬럼명           | 타입          | PK | FK              | NULL     | 설명                   |
|-----------------|--------------|----|-----------------|----------|------------------------|
| history_id      | BIGINT       | PK |                 | NOT NULL | 버전 이력 ID          |
| post_id         | BIGINT       |    | posts.post_id   | NOT NULL | 게시글 ID             |
| modifier_id     | BIGINT       |    | users.user_id   | NOT NULL | 수정자 ID             |
| version_type    | VARCHAR(50)  |    |                 | NOT NULL | 버전 타입(CREATE/MODIFY/DELETE) |
| original_title  | VARCHAR(200) |    |                 | YES      | 수정 전 제목          |
| original_contents|TEXT         |    |                 | YES      | 수정 전 내용          |
| created_at      | DATETIME     |    |                 | NOT NULL | 생성일                |
| modified_at     | DATETIME     |    |                 | NOT NULL | 수정일                |

**인덱스:**
```sql
CREATE INDEX idx_post_versions_post ON post_versions(post_id, created_at DESC);
```

---

## 16. 임시 저장 게시글 (draft_posts)

| 컬럼명         | 타입          | PK | FK              | NULL     | 설명                  |
|----------------|--------------|----|-----------------|----------|-----------------------|
| draft_id       | BIGINT       | PK |                 | NOT NULL | 임시 저장 ID          |
| user_id        | BIGINT       |    | users.user_id   | NOT NULL | 사용자 ID             |
| board_id       | BIGINT       |    | boards.board_id | NOT NULL | 게시판 ID             |
| title          | VARCHAR(200) |    |                 | YES      | 제목(임시)            |
| contents       | TEXT         |    |                 | YES      | 내용(임시)            |
| original_post_id| BIGINT      |    | posts.post_id   | YES      | 수정 대상 게시글 ID   |
| created_at     | DATETIME     |    |                 | NOT NULL | 생성일                |
| modified_at    | DATETIME     |    |                 | NOT NULL | 수정일                |

**인덱스:**
```sql
CREATE INDEX idx_draft_posts_user ON draft_posts(user_id, modified_at DESC);
```

---

## 16. 댓글 (comments)

| 컬럼명      | 타입      | PK | FK                    | NULL     | 설명                           |
|------------|----------|----|-----------------------|----------|-------------------------------|
| comment_id | BIGINT   | PK |                       | NOT NULL | 댓글 ID                        |
| post_id    | BIGINT   |    | posts.post_id         | NOT NULL | 게시글 ID                      |
| user_id    | BIGINT   |    | users.user_id         | NOT NULL | 작성자 ID                      |
| parent_id  | BIGINT   |    | comments.comment_id   | YES      | 직접 부모 댓글 ID (NULL이면 최상위) |
| depth      | INT      |    |                       | NOT NULL | 댓글 깊이 (0이면 최상위, 캐싱용)  |
| content    | TEXT     |    |                       | NOT NULL | 댓글 내용                       |
| is_deleted | CHAR(1)  |    |                       | NOT NULL | 삭제 여부 (Y/N)                 |
| like_count | INT      |    |                       | NOT NULL | 좋아요 수 (비정규화)            |
| created_at | DATETIME |    |                       | NOT NULL | 생성일                          |
| modified_at| DATETIME |    |                       | NOT NULL | 수정일                          |

**인덱스:**
```sql
CREATE INDEX idx_comments_post ON comments(post_id, is_deleted, created_at);
CREATE INDEX idx_comments_user ON comments(user_id, is_deleted);
CREATE INDEX idx_comments_parent ON comments(parent_id);
```

**삭제 정책:** Soft Delete

---

## 18. 댓글 계층 관계 (comment_closures)

| 컬럼명        | 타입      | PK | FK                    | NULL     | 설명                           |
|--------------|----------|----|-----------------------|----------|-------------------------------|
| ancestor_id  | BIGINT   | PK | comments.comment_id   | NOT NULL | 조상 댓글 ID                    |
| descendant_id| BIGINT   | PK | comments.comment_id   | NOT NULL | 자손 댓글 ID                    |
| depth        | INT      |    |                       | NOT NULL | 조상-자손 간 거리 (0이면 자기 자신) |
| created_at   | DATETIME |    |                       | NOT NULL | 생성일                          |
| modified_at  | DATETIME |    |                       | NOT NULL | 수정일                          |

**인덱스:**
```sql
CREATE INDEX idx_closure_ancestor ON comment_closures(ancestor_id, depth);
CREATE INDEX idx_closure_descendant ON comment_closures(descendant_id, depth);
```

**주요 쿼리 패턴:**

```sql
-- 특정 댓글의 모든 자손 조회
SELECT c.* 
FROM comments c
JOIN comment_closures cc ON c.comment_id = cc.descendant_id
WHERE cc.ancestor_id = ? AND cc.depth > 0
ORDER BY cc.depth, c.created_at;

-- 특정 댓글의 모든 조상 조회
SELECT c.*
FROM comments c
JOIN comment_closures cc ON c.comment_id = cc.ancestor_id
WHERE cc.descendant_id = ? AND cc.depth > 0
ORDER BY cc.depth DESC;

-- 특정 댓글의 직계 자식만 조회
SELECT c.*
FROM comments c
JOIN comment_closures cc ON c.comment_id = cc.descendant_id
WHERE cc.ancestor_id = ? AND cc.depth = 1;
```

**INSERT 로직 (새 댓글 추가 시):**

```sql
-- 1. 댓글 본문 INSERT
INSERT INTO comments (comment_id, post_id, user_id, parent_id, depth, content, ...)
VALUES (NEW_ID, ?, ?, PARENT_ID, PARENT_DEPTH + 1, ?, ...);

-- 2. 자기 자신에 대한 closure
INSERT INTO comment_closures (ancestor_id, descendant_id, depth, created_at, modified_at)
VALUES (NEW_ID, NEW_ID, 0, NOW(), NOW());

-- 3. 부모의 모든 조상에 대한 closure 복사 (depth + 1)
INSERT INTO comment_closures (ancestor_id, descendant_id, depth, created_at, modified_at)
SELECT ancestor_id, NEW_ID, depth + 1, NOW(), NOW()
FROM comment_closures
WHERE descendant_id = PARENT_ID;
```

---

## 19. 댓글 좋아요 (comment_likes)

| 컬럼명    | 타입      | PK | FK                   | NULL     | 설명         |
|----------|----------|----|----------------------|----------|--------------|
| comment_id | BIGINT | PK | comments.comment_id  | NOT NULL | 댓글 ID      |
| user_id  | BIGINT   | PK | users.user_id        | NOT NULL | 사용자 ID    |
| created_at | DATETIME |  |                      | NOT NULL | 생성일       |
| modified_at| DATETIME | |                      | NOT NULL | 수정일       |

**인덱스:**
```sql
CREATE INDEX idx_comment_likes_user ON comment_likes(user_id);
```

---

## 20. 댓글 버전 관리 (comment_versions)

| 컬럼명           | 타입          | PK | FK                  | NULL     | 설명                   |
|-----------------|--------------|----|--------------------- |----------|------------------------|
| history_id      | BIGINT       | PK |                     | NOT NULL | 버전 이력 ID           |
| comment_id      | BIGINT       |    | comments.comment_id | NOT NULL | 댓글 ID                |
| modifier_id     | BIGINT       |    | users.user_id       | NOT NULL | 수정자 ID              |
| version_type    | VARCHAR(50)  |    |                     | NOT NULL | 버전 타입(CREATE/MODIFY/DELETE) |
| original_content| TEXT         |    |                     | YES      | 수정 전 내용           |
| created_at      | DATETIME     |    |                     | NOT NULL | 생성일                 |
| modified_at     | DATETIME     |    |                     | NOT NULL | 수정일                 |

**인덱스:**
```sql
CREATE INDEX idx_comment_versions_comment ON comment_versions(comment_id, created_at DESC);
```

---

## 20. 알림 (notifications)

| 컬럼명           | 타입          | PK | FK            | NULL     | 설명                               |
|-----------------|--------------|----|--------------  |----------|-----------------------------------|
| notification_id | BIGINT       | PK |               | NOT NULL | 알림 ID                            |
| user_id         | BIGINT       |    | users.user_id | NOT NULL | 알림 받는 사용자                     |
| actor_id        | BIGINT       |    | users.user_id | YES      | 액션 수행자 (시스템 알림은 NULL)      |
| notification_type| VARCHAR(50) |    |               | NOT NULL | 알림 유형 (COMMENT/LIKE/MENTION 등) |
| source_type     | VARCHAR(50)  |    |               | NOT NULL | 원본 객체 타입 (POST/COMMENT 등)    |
| source_id       | BIGINT       |    |               | YES      | 원본 객체 ID                        |
| content         | VARCHAR(255) |    |               | NOT NULL | 알림 내용                           |
| is_read         | CHAR(1)      |    |               | NOT NULL | 읽음 여부 (Y/N)                     |
| created_at      | DATETIME     |    |               | NOT NULL | 생성일                              |
| modified_at     | DATETIME     |    |               | NOT NULL | 수정일                              |

**인덱스:**
```sql
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_actor ON notifications(actor_id);
```

---

## 22. 쪽지 (messages)

| 컬럼명              | 타입      | PK | FK            | NULL     | 설명                      |
|--------------------|----------|----|---------------|----------|--------------------------|
| message_id         | BIGINT   | PK |               | NOT NULL | 쪽지 ID                   |
| sender_id          | BIGINT   |    | users.user_id | NOT NULL | 발신자 ID                 |
| receiver_id        | BIGINT   |    | users.user_id | NOT NULL | 수신자 ID                 |
| content            | TEXT     |    |               | NOT NULL | 쪽지 내용                 |
| is_read            | CHAR(1)  |    |               | NOT NULL | 읽음 여부 (Y/N)           |
| is_deleted_by_sender  | CHAR(1)|   |               | NOT NULL | 발신자 삭제 여부 (Y/N)    |
| is_deleted_by_receiver| CHAR(1)|   |               | NOT NULL | 수신자 삭제 여부 (Y/N)    |
| created_at         | DATETIME |    |               | NOT NULL | 생성일                    |
| modified_at        | DATETIME |    |               | NOT NULL | 수정일                    |

**인덱스:**
```sql
CREATE INDEX idx_messages_receiver ON messages(receiver_id, is_deleted_by_receiver, is_read, created_at DESC);
CREATE INDEX idx_messages_sender ON messages(sender_id, is_deleted_by_sender, created_at DESC);
```

**삭제 정책:** Soft Delete

---

## 23. 스크랩 (scraps)

| 컬럼명   | 타입          | PK | FK            | NULL     | 설명        |
|---------|--------------|----|---------------|----------|-------------|
| user_id | BIGINT       | PK | users.user_id | NOT NULL | 사용자 ID   |
| post_id | BIGINT       | PK | posts.post_id | NOT NULL | 게시글 ID   |
| remark  | VARCHAR(255) |    |               | YES      | 메모        |
| created_at | DATETIME  |    |               | NOT NULL | 생성일      |
| modified_at|DATETIME   |    |               | NOT NULL | 수정일      |

**인덱스:**
```sql
CREATE INDEX idx_scraps_user ON scraps(user_id, created_at DESC);
```

---

## 24. 태그 마스터 (tags)

| 컬럼명    | 타입          | PK | FK | NULL     | 설명              |
|----------|--------------|----|----|----------|-------------------|
| tag_id   | BIGINT       | PK |    | NOT NULL | 태그 ID           |
| tag_name | VARCHAR(100) |    |    | NOT NULL | 태그명 (UNIQUE)   |
| post_count|INT          |    |    | NOT NULL | 게시글 사용 수 (비정규화) |
| created_at|DATETIME     |    |    | NOT NULL | 생성일            |
| modified_at|DATETIME    |    |    | NOT NULL | 수정일            |

**인덱스:**
```sql
CREATE UNIQUE INDEX uk_tags_name ON tags(tag_name);
CREATE INDEX idx_tags_count ON tags(post_count DESC);
```

---

## 25. 인기글 캐시 (popular_posts)

| 컬럼명      | 타입          | PK | FK            | NULL     | 설명                          |
|------------|--------------|----|---------------|----------|-------------------------------|
| cache_id   | BIGINT       | PK |               | NOT NULL | 캐시 ID                       |
| ranking_type|VARCHAR(50)  |    |               | NOT NULL | 랭킹 타입(DAILY/WEEKLY/MONTHLY)|
| post_id    | BIGINT       |    | posts.post_id | NOT NULL | 게시글 ID                     |
| score      | FLOAT        |    |               | NOT NULL | 점수                          |
| rank       | INT          |    |               | NOT NULL | 순위                          |
| created_at | DATETIME     |    |               | NOT NULL | 생성일                        |
| modified_at| DATETIME     |    |               | NOT NULL | 수정일                        |

**인덱스:**
```sql
CREATE INDEX idx_popular_posts_type ON popular_posts(ranking_type, rank);
CREATE INDEX idx_popular_posts_post ON popular_posts(post_id);
```

---

## 26. 관리자 (admins)

| 컬럼명      | 타입          | PK | FK              | NULL     | 설명          |
|------------|--------------|----|-----------------|----------|---------------|
| admin_id   | BIGINT       | PK |                 | NOT NULL | 관리자 ID     |
| user_id    | BIGINT       |    | users.user_id   | NOT NULL | 관리자 사용자 |
| board_id   | BIGINT       |    | boards.board_id | YES      | 담당 게시판 (NULL이면 전체 관리자) |
| role       | VARCHAR(50)  |    |                 | NOT NULL | 역할 (SUPER/BOARD_ADMIN/MODERATOR) |
| is_active  | CHAR(1)      |    |                 | NOT NULL | 활성 여부 (Y/N)|
| created_at | DATETIME     |    |                 | NOT NULL | 생성일        |
| modified_at| DATETIME     |    |                 | NOT NULL | 수정일        |

**인덱스:**
```sql
CREATE INDEX idx_admins_user ON admins(user_id, is_active);
CREATE INDEX idx_admins_board ON admins(board_id, is_active);
```

**참고:** board_id가 NULL이면 전체 사이트 관리자 (SUPER)

---

## 27. 신고 (reports)

| 컬럼명     | 타입          | PK | FK              | NULL     | 설명                  |
|-----------|--------------|----|-----------------|----------|-----------------------|
| report_id | BIGINT       | PK |                 | NOT NULL | 신고 ID               |
| reporter_id| BIGINT      |    | users.user_id   | NOT NULL | 신고자 ID             |
| target_type|VARCHAR(50)  |    |                 | NOT NULL | 대상 타입(POST/COMMENT/USER) |
| target_id | BIGINT       |    |                 | NOT NULL | 대상 ID               |
| reason_type|VARCHAR(50)  |    |                 | NOT NULL | 신고 사유 코드        |
| remark    | VARCHAR(255) |    |                 | YES      | 비고                  |
| status    | VARCHAR(50)  |    |                 | NOT NULL | 처리 상태 (PENDING/RESOLVED/REJECTED) |
| contents  | TEXT         |    |                 | YES      | 상세 내용             |
| admin_id  | BIGINT       |    | admins.admin_id | YES      | 처리 관리자 ID        |
| created_at| DATETIME     |    |                 | NOT NULL | 생성일                |
| modified_at|DATETIME     |    |                 | NOT NULL | 수정일                |

**인덱스:**
```sql
CREATE INDEX idx_reports_status ON reports(status, created_at DESC);
CREATE INDEX idx_reports_reporter ON reports(reporter_id);
CREATE INDEX idx_reports_target ON reports(target_type, target_id);
CREATE UNIQUE INDEX uk_reports_user_target ON reports(reporter_id, target_type, target_id);
```

---

## 28. 제재 기록 (sanctions)

| 컬럼명        | 타입          | PK | FK              | NULL     | 설명                |
|--------------|--------------|----|-----------------|----------|---------------------|
| sanction_id  | BIGINT       | PK |                 | NOT NULL | 제재 ID             |
| target_user_id | BIGINT     |    | users.user_id   | NOT NULL | 제재 대상 사용자 ID |
| admin_id     | BIGINT       |    | admins.admin_id | NOT NULL | 처리 관리자 ID      |
| type         | VARCHAR(50)  |    |                 | NOT NULL | 제재 유형 (WARNING/MUTE/BAN) |
| remark       | VARCHAR(255) |    |                 | YES      | 메모                |
| start_date   | DATETIME     |    |                 | NOT NULL | 시작 일시           |
| end_date     | DATETIME     |    |                 | YES      | 종료 일시 (NULL이면 영구) |
| content_id   | BIGINT       |    |                 | YES      | 관련 콘텐츠 ID      |
| content_type | VARCHAR(50)  |    |                 | YES      | 관련 콘텐츠 타입    |
| created_at   | DATETIME     |    |                 | NOT NULL | 생성일              |
| modified_at  | DATETIME     |    |                 | NOT NULL | 수정일              |

**인덱스:**
```sql
CREATE INDEX idx_sanctions_user ON sanctions(target_user_id, end_date);
CREATE INDEX idx_sanctions_admin ON sanctions(admin_id);
```

---

## 29. IP 차단 (ip_blocks)

| 컬럼명     | 타입          | PK | FK              | NULL     | 설명          |
|-----------|--------------|----|-----------------|----------|---------------|
| ip_address| VARCHAR(45)  | PK |                 | NOT NULL | IP 주소       |
| reason    | VARCHAR(255) |    |                 | YES      | 차단 사유     |
| admin_id  | BIGINT       |    | admins.admin_id | NOT NULL | 관리자 ID     |
| start_date| DATETIME     |    |                 | NOT NULL | 시작 일시     |
| end_date  | DATETIME     |    |                 | YES      | 종료 일시     |
| created_at| DATETIME     |    |                 | NOT NULL | 생성일        |
| modified_at|DATETIME     |    |                 | NOT NULL | 수정일        |

**인덱스:**
```sql
CREATE INDEX idx_ip_blocks_dates ON ip_blocks(start_date, end_date);
```

---

## 30. 활동 기록 (logs)

| 컬럼명      | 타입          | PK | FK            | NULL     | 설명           |
|------------|--------------|----|---------------|----------|----------------|
| log_id     | BIGINT       | PK |               | NOT NULL | 로그 ID        |
| user_id    | BIGINT       |    | users.user_id | YES      | 사용자 ID      |
| action_type| VARCHAR(100) |    |               | NOT NULL | 액션 유형      |
| ip_address | VARCHAR(45)  |    |               | NOT NULL | IP 주소        |
| details    | TEXT         |    |               | YES      | 상세 정보 (JSON 형태) |
| created_at | DATETIME     |    |               | NOT NULL | 생성일         |
| modified_at| DATETIME     |    |               | NOT NULL | 수정일         |

**인덱스:**
```sql
CREATE INDEX idx_logs_user ON logs(user_id, created_at DESC);
CREATE INDEX idx_logs_action ON logs(action_type, created_at DESC);
```

**details 예시:**
```json
{
  "target_type": "POST",
  "target_id": 12345,
  "before": {"title": "이전 제목"},
  "after": {"title": "변경된 제목"}
}
```

---

## 31. 전역 설정 (global_configs)

| 컬럼명     | 타입          | PK | FK | NULL     | 설명           |
|-----------|--------------|----|----|----------|----------------|
| config_key| VARCHAR(100) | PK |    | NOT NULL | 설정 키        |
| config_value| VARCHAR(255)|   |    | YES      | 설정 값        |
| description| VARCHAR(255)|   |    | YES      | 설명           |
| created_at| DATETIME     |    |    | NOT NULL | 생성일         |
| modified_at|DATETIME     |    |    | NOT NULL | 수정일         |

---

## 32. 첨부파일 (files)

| 컬럼명        | 타입          | PK | FK            | NULL     | 설명                            |
|--------------|--------------|----|---------------|----------|---------------------------------|
| file_id      | BIGINT       | PK |               | NOT NULL | 파일 ID                         |
| file_path    | VARCHAR(255) |    |               | NOT NULL | 저장 경로                       |
| original_name| VARCHAR(255) |    |               | NOT NULL | 원본 파일명                     |
| file_size    | BIGINT       |    |               | NOT NULL | 파일 크기 (bytes)               |
| mime_type    | VARCHAR(100) |    |               | NOT NULL | 파일 MIME 타입                  |
| uploader_id  | BIGINT       |    | users.user_id | NOT NULL | 업로더 ID                       |
| related_id   | BIGINT       |    |               | YES      | 관련 대상 ID (게시글/댓글 등)    |
| related_type | VARCHAR(50)  |    |               | YES      | 관련 대상 타입 (POST/COMMENT 등) |
| created_at   | DATETIME     |    |               | NOT NULL | 생성일                          |
| modified_at  | DATETIME     |    |               | NOT NULL | 수정일                          |

**인덱스:**
```sql
CREATE INDEX idx_files_uploader ON files(uploader_id);
CREATE INDEX idx_files_related ON files(related_type, related_id);
```

**삭제 정책:** Hard Delete

**참고:** related_id가 NULL이면 아직 게시글/댓글에 연결되지 않은 임시 파일

**임시파일 정리 정책:** created_at 기준 24시간 경과한 미연결 파일(related_id IS NULL)은 배치로 삭제

---

## 33. 검색 통계 (search_statistics)

| 컬럼명      | 타입          | PK | FK | NULL     | 설명          |
|------------|--------------|----|----|----------|---------------|
| keyword    | VARCHAR(255) | PK |    | NOT NULL | 검색어        |
| search_date| DATE         | PK |    | NOT NULL | 검색 일자     |
| search_count| INT         |    |    | NOT NULL | 검색 횟수     |
| created_at | DATETIME     |    |    | NOT NULL | 생성일        |
| modified_at| DATETIME     |    |    | NOT NULL | 수정일        |

**설명:** 검색 통계 로그용. 실제 검색은 Elasticsearch로 처리. 일자별 트렌드 분석 가능.

**인덱스:**
```sql
CREATE INDEX idx_search_statistics_date ON search_statistics(search_date, search_count DESC);
CREATE INDEX idx_search_statistics_keyword ON search_statistics(keyword, search_date DESC);
```

---

## 34. 검색 개인화 (search_personalization)

| 컬럼명  | 타입          | PK | FK            | NULL     | 설명              |
|--------|--------------|----|---------------|----------|-------------------|
| log_id | BIGINT       | PK |               | NOT NULL | 로그 ID           |
| user_id| BIGINT       |    | users.user_id | NOT NULL | 사용자 ID         |
| keyword| VARCHAR(255) |    |               | NOT NULL | 검색어            |
| created_at | DATETIME |   |               | NOT NULL | 생성일            |
| modified_at|DATETIME  |   |               | NOT NULL | 수정일            |

**설명:** 개인 검색 기록 저장용. 최근 검색어, 자동완성 등에 활용.

**인덱스:**
```sql
CREATE INDEX idx_search_personalization_user ON search_personalization(user_id, created_at DESC);
```

---

## 35. 사용자 포인트 (user_points)

| 컬럼명       | 타입      | PK | FK            | NULL     | 설명           |
|-------------|----------|----|---------------|----------|----------------|
| user_id     | BIGINT   | PK | users.user_id | NOT NULL | 사용자 ID      |
| current_point|INT      |    |               | NOT NULL | 현재 포인트    |
| created_at  | DATETIME |    |               | NOT NULL | 생성일         |
| modified_at | DATETIME |    |               | NOT NULL | 수정일         |

---

## 36. 포인트 이력 (point_histories)

| 컬럼명       | 타입          | PK | FK            | NULL     | 설명                         |
|-------------|--------------|----|---------------|----------|------------------------------|
| history_id  | BIGINT       | PK |               | NOT NULL | 포인트 이력 ID               |
| user_id     | BIGINT       |    | users.user_id | NOT NULL | 사용자 ID                    |
| type        | VARCHAR(50)  |    |               | NOT NULL | 타입(EARN/SPEND/EXPIRE 등)   |
| amount      | INT          |    |               | NOT NULL | 포인트 변동량                |
| balance_after| INT         |    |               | NOT NULL | 변동 후 잔액                 |
| description | VARCHAR(255) |    |               | YES      | 변동 사유 설명               |
| related_id  | BIGINT       |    |               | YES      | 관련 객체 ID                 |
| related_type| VARCHAR(50)  |    |               | YES      | 관련 객체 타입               |
| created_at  | DATETIME     |    |               | NOT NULL | 생성일                       |
| modified_at | DATETIME     |    |               | NOT NULL | 수정일                       |

**인덱스:**
```sql
CREATE INDEX idx_point_histories_user ON point_histories(user_id, created_at DESC);
```

---

## 37. 상점 아이템 (shop_items)

| 컬럼명    | 타입          | PK | FK | NULL     | 설명          |
|----------|--------------|----|----|----------|---------------|
| item_id  | BIGINT       | PK |    | NOT NULL | 아이템 ID     |
| item_name| VARCHAR(100) |    |    | NOT NULL | 아이템 이름   |
| description|VARCHAR(255)|    |    | YES      | 설명          |
| price    | INT          |    |    | NOT NULL | 가격          |
| item_type| VARCHAR(50)  |    |    | NOT NULL | 아이템 타입   |
| is_active| CHAR(1)      |    |    | NOT NULL | 사용 여부 (Y/N)|
| created_at|DATETIME     |    |    | NOT NULL | 생성일        |
| modified_at|DATETIME    |    |    | NOT NULL | 수정일        |

**인덱스:**
```sql
CREATE INDEX idx_shop_items_active ON shop_items(is_active, item_type);
```

---

## 38. 구매 기록 (purchase_history)

| 컬럼명        | 타입      | PK | FK                  | NULL     | 설명          |
|--------------|----------|----|---------------------|----------|---------------|
| purchase_id  | BIGINT   | PK |                     | NOT NULL | 구매 ID       |
| user_id      | BIGINT   |    | users.user_id       | NOT NULL | 사용자 ID     |
| item_id      | BIGINT   |    | shop_items.item_id  | NOT NULL | 아이템 ID     |
| purchased_price|INT     |    |                     | NOT NULL | 구매 가격     |
| created_at   | DATETIME |    |                     | NOT NULL | 생성일        |
| modified_at  | DATETIME |    |                     | NOT NULL | 수정일        |

**인덱스:**
```sql
CREATE INDEX idx_purchase_history_user ON purchase_history(user_id, created_at DESC);
```

---

## 39. 공통코드 (common_codes)

| 컬럼명    | 타입          | PK | FK | NULL     | 설명        |
|----------|--------------|----|----|----------|-------------|
| type_code| VARCHAR(50)  | PK |    | NOT NULL | 코드 유형   |
| type_name| VARCHAR(100) |    |    | NOT NULL | 코드 유형명 |
| description|VARCHAR(255)|    |    | YES      | 설명        |
| created_at|DATETIME     |    |    | NOT NULL | 생성일      |
| modified_at|DATETIME    |    |    | NOT NULL | 수정일      |

---

## 40. 상세코드 (common_code_details)

| 컬럼명    | 타입          | PK | FK                    | NULL     | 설명               |
|----------|--------------|----|-----------------------|----------|--------------------|
| id       | BIGINT       | PK |                       | NOT NULL | 상세 코드 ID       |
| type_code| VARCHAR(50)  |    | common_codes.type_code| NOT NULL | 코드 유형          |
| code_value|VARCHAR(100) |    |                       | NOT NULL | 코드 값            |
| code_name|VARCHAR(100)  |    |                       | NOT NULL | 코드명             |
| sort_order|INT          |    |                       | NOT NULL | 정렬 순서          |
| is_active|CHAR(1)       |    |                       | NOT NULL | 사용 여부 (Y/N)    |
| created_at|DATETIME     |    |                       | NOT NULL | 생성일             |
| modified_at|DATETIME    |    |                       | NOT NULL | 수정일             |

**인덱스:**
```sql
CREATE INDEX idx_common_code_details_type ON common_code_details(type_code, is_active, sort_order);
CREATE UNIQUE INDEX uk_code_details_value ON common_code_details(type_code, code_value); -- 그룹 내 코드값 중복 방지
```

---

## 41. 열람 기록 (view_histories)

| 컬럼명              | 타입      | PK | FK                  | NULL     | 설명                    |
|--------------------|----------|----|--------------------- |----------|-------------------------|
| view_history_id    | BIGINT   | PK |                     | NOT NULL | 열람 기록 ID            |
| user_id            | BIGINT   |    | users.user_id       | NOT NULL | 사용자 ID               |
| post_id            | BIGINT   |    | posts.post_id       | NOT NULL | 게시글 ID               |
| last_read_comment_id| BIGINT  |    | comments.comment_id | YES      | 마지막으로 읽은 댓글 ID |
| duration_ms        | BIGINT   |    |                     | NOT NULL | 체류 시간(ms)           |
| created_at         | DATETIME |    |                     | NOT NULL | 생성일                  |
| modified_at        | DATETIME |    |                     | NOT NULL | 수정일                  |

**인덱스:**
```sql
CREATE INDEX idx_view_histories_user ON view_histories(user_id, created_at DESC);
CREATE INDEX idx_view_histories_post ON view_histories(post_id);
CREATE UNIQUE INDEX uk_view_histories_user_post ON view_histories(user_id, post_id);
```

**구현 정책:** 동일 사용자가 같은 게시글 재방문 시 기존 row UPDATE (UPSERT)
- `last_read_comment_id`, `duration_ms`, `modified_at` 갱신
- 열람 이력은 사용자당 게시글당 1건만 유지

---

## 42. 사용자 피드 (user_feeds)

| 컬럼명             | 타입       | PK | FK              | NULL     | 설명              |
|-------------------|-----------|----|-----------------|---------  |------------------|
| feed_id           | BIGINT    | PK |                 | NOT NULL | 피드 ID           |
| target_user_id    | BIGINT    |    | users.user_id   | NOT NULL | 대상 사용자 ID     |
| feed_type         | VARCHAR(50)|   |                 | NOT NULL | 피드 유형         |
| content_type      | VARCHAR(50)|   |                 | NOT NULL | 콘텐츠 유형        |
| content_id        | BIGINT    |    |                 | NOT NULL | 대상 콘텐츠 ID     |
| source_criteria   | VARCHAR(50)|   |                 | NOT NULL | 피드 생성 기준     |
| criteria_id       | BIGINT    |    |                 | YES      | 기준 객체 ID      |
| is_read           | CHAR(1)   |    |                 | NOT NULL | 읽음 여부 (Y/N)   |
| created_at        | DATETIME  |    |                 | NOT NULL | 생성일            |
| modified_at       | DATETIME  |    |                 | NOT NULL | 수정일            |

**설명:** 유저 관심사 기반 개인화 피드를 위한 스냅샷 테이블

**인덱스:**
```sql
CREATE INDEX idx_user_feeds_user ON user_feeds(target_user_id, is_read, created_at DESC);
```

---

## 43. 발송 메세지 큐 (message_queue)

| 컬럼명             | 타입         | PK | FK            | NULL     | 설명              |
|-------------------|-------------|----|---------------|----------|------------------|
| queue_id          | BIGINT      | PK |               | NOT NULL | 발송 요청 ID      |
| target_user_id    | BIGINT      |    | users.user_id | NOT NULL | 수신 대상 사용자 ID|
| delivery_method   | VARCHAR(20) |    |               | NOT NULL | 발송 방법 (EMAIL/PUSH/SMS) |
| content           | TEXT        |    |               | NOT NULL | 발송 내용         |
| requested_at      | DATETIME    |    |               | NOT NULL | 발송 요청 시각     |
| status            | VARCHAR(50) |    |               | NOT NULL | 처리 상태 (PENDING/SENT/FAILED) |
| retry_count       | INT         |    |               | NOT NULL | 재시도 횟수        |
| created_at        | DATETIME    |    |               | NOT NULL | 생성일            |
| modified_at       | DATETIME    |    |               | NOT NULL | 수정일            |

**인덱스:**
```sql
CREATE INDEX idx_message_queue_status ON message_queue(status, requested_at);
CREATE INDEX idx_message_queue_user ON message_queue(target_user_id);
```

---

## 43. 광고 (ads)

| 컬럼명          | 타입          | PK | FK | NULL     | 설명               |
|----------------|--------------|----|----|----------|--------------------|
| ad_id          | BIGINT       | PK |    | NOT NULL | 광고 ID            |
| ad_name        | VARCHAR(100) |    |    | NOT NULL | 광고 이름          |
| image_url      | VARCHAR(255) |    |    | NOT NULL | 광고 이미지 URL    |
| placement      | VARCHAR(100) |    |    | NOT NULL | 노출 위치          |
| target_url     | VARCHAR(255) |    |    | NOT NULL | 클릭 시 이동 URL   |
| impression_count| INT         |    |    | NOT NULL | 노출 수 (비정규화)  |
| click_count    | INT          |    |    | NOT NULL | 클릭 수 (비정규화)  |
| start_date     | DATETIME     |    |    | NOT NULL | 시작 일시          |
| end_date       | DATETIME     |    |    | YES      | 종료 일시          |
| is_active      | CHAR(1)      |    |    | NOT NULL | 활성 여부 (Y/N)    |
| created_at     | DATETIME     |    |    | NOT NULL | 생성일             |
| modified_at    | DATETIME     |    |    | NOT NULL | 수정일             |

**인덱스:**
```sql
CREATE INDEX idx_ads_active ON ads(is_active, placement, start_date, end_date);
```

**CTR 계산:** `click_count / impression_count * 100`

---

## 45. 광고 클릭 로그 (ad_click_logs)

| 컬럼명    | 타입          | PK | FK            | NULL     | 설명                 |
|----------|--------------|----|---------------|----------|----------------------|
| log_id   | BIGINT       | PK |               | NOT NULL | 클릭 로그 ID         |
| ad_id    | BIGINT       |    | ads.ad_id     | NOT NULL | 광고 ID              |
| user_id  | BIGINT       |    | users.user_id | YES      | 사용자 ID(익명 가능) |
| clicked_at|DATETIME     |    |               | NOT NULL | 클릭 시각            |
| ip_address|VARCHAR(45)  |    |               | YES      | IP 주소              |
| created_at|DATETIME     |    |               | NOT NULL | 생성일               |
| modified_at|DATETIME    |    |               | NOT NULL | 수정일               |

**인덱스:**
```sql
CREATE INDEX idx_ad_click_logs_ad ON ad_click_logs(ad_id, clicked_at);
CREATE INDEX idx_ad_click_logs_user ON ad_click_logs(user_id);
```

---

## 변경 요약 (v1 → v2)

### 신규 테이블 (8개)
| 테이블 | 설명 |
|-------|------|
| user_settings | 사용자 설정 (테마, 언어, 타임존 등) |
| login_histories | 로그인 이력 |
| refresh_tokens | JWT Refresh Token 관리 |
| password_histories | 비밀번호 변경 이력 |
| display_name_histories | 닉네임 변경 이력 |
| comment_closures | 댓글 계층 관계 (Closure Table) |
| comment_versions | 댓글 버전 관리 |
| messages | 쪽지 |

### 삭제 테이블 (2개)
| 테이블 | 사유 |
|-------|------|
| temp_files | files 테이블로 통합 (related_id NULL로 처리) |
| read_posts | view_histories로 통합 |

### 수정 테이블 (19개)
| 테이블 | 변경 내용 |
|-------|----------|
| users | login_id, display_name, status, is_email_verified, last_login_at, deleted_at 추가 |
| boards | creator_id, sort_order, allow_nsfw, icon_url, banner_url 추가, is_active 네이밍 |
| board_categories | sort_order, is_active 추가 |
| posts | comment_count, is_notice, is_nsfw, is_spoiler 추가 |
| comments | depth 추가 |
| notifications | actor_id, notification_type, source_type 추가 |
| files | file_size, mime_type 추가, uploader_id 네이밍, 임시파일 정리 정책 명시 |
| admins | board_id NULL 허용 |
| ads | image_url, impression_count, click_count 추가 |
| view_histories | last_read_comment_id 추가, UPSERT 정책 |
| logs | details 컬럼 추가 |
| point_histories | balance_after, description 추가 |
| reports | 중복 방지 UNIQUE 인덱스 추가 |
| search_statistics | search_date 추가, PK 변경 (keyword, search_date) |
| favorite_boards → board_subscriptions | 테이블명 변경, role 추가 |
| user_feeds | FK 오류 수정 |
| message_queue | FK 오류 수정 |

### 전체 적용
- Boolean 네이밍 `is_` 접두사 통일
- 인덱스 정의 추가
- 삭제 정책 명시

### 총 테이블 수
- v1: 39개
- v2: **45개** (신규 8개, 삭제 2개)