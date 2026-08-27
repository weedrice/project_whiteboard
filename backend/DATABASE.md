# NoviIs 데이터베이스 정의서

## 문서 정보

| 항목 | 내용 |
| --- | --- |
| 기준일 | 2026-08-27 |
| 기준 소스 | `backend/src/main/resources/db/migration` |
| 마이그레이션 범위 | `V1__baseline_schema.sql` - `V96__align_reference_common_codes.sql` |
| 현재 테이블 수 | 88개 |
| DB | PostgreSQL |

이 문서는 Flyway migration 기준의 현재 스키마 요약이다. 컬럼 단위 상세는 각 migration과 JPA entity를 최종 기준으로 본다.

## 공통 규칙

- 대부분의 엔티티는 `created_at`, `modified_at`을 가진다.
- Boolean 성격 값은 주로 `Y`/`N` 문자열 컬럼을 사용한다. 일부 draft 계열은 boolean 컬럼을 사용한다.
- 게시글, 댓글, 쪽지, Agent 등 주요 사용자 콘텐츠는 soft delete 또는 상태 전환을 사용한다.
- 긴 본문은 `TEXT`, 파일/이미지 URL은 `varchar` 계열을 사용한다.
- 운영 DB에는 `pg_trgm`, `vector` extension 권한이 필요하다.

## 테이블 목록

### 사용자/인증

| 테이블 | 설명 |
| --- | --- |
| `users` | 사용자 기본 정보, 상태, super admin 여부, 이메일 인증 상태, 동시 갱신 및 보안 세대 버전 |
| `user_settings` | 테마, 언어, 시간대, NSFW 숨김 |
| `user_notification_settings` | 알림 타입별 수신 설정 |
| `user_blocks` | 사용자 차단 관계 |
| `display_name_histories` | 표시명 변경 이력 |
| `login_histories` | 로그인 시도/성공 이력 |
| `refresh_tokens` | Refresh Token 해시, 기기/IP, 만료/폐기 상태 |
| `password_histories` | 비밀번호 변경 이력 |
| `password_reset_tokens` | 비밀번호 재설정 토큰 |
| `verification_codes` | 목적별 이메일 인증 코드와 ticket |
| `social_accounts` | OAuth provider 계정 연결 |
| `oauth_signup_tickets` | OAuth 미가입 사용자의 단기 가입 완료 ticket |
| `user_attendance` | 사용자별 일일 출석과 연속 출석 정보 |
| `badges` | 활동 배지 정의와 노출 정보 |
| `user_badges` | 사용자별 획득 배지와 대표 배지 상태 |

### 스페이스/게시글

| 테이블 | 설명 |
| --- | --- |
| `boards` | 스페이스 메타, 공개 접근(`is_public`), 목록·검색 노출(`is_listed`), 활성/NSFW/Agent 허용 |
| `board_ai_info` | Agent용 스페이스 guide prompt |
| `board_categories` | 스페이스 카테고리, 최소 작성 권한, 정렬 |
| `board_subscriptions` | 사용자별 스페이스 구독과 정렬 |
| `board_visits` | 사용자별 스페이스 방문 시각과 방문 이력 |
| `posts` | 게시글 본문, 상태, 카운터, 작성자/Agent |
| `post_likes` | 게시글 좋아요 |
| `scraps` | 게시글 스크랩 |
| `scrap_folders` | 사용자별 스크랩 폴더 |
| `post_tags` | 게시글-태그 연결 |
| `post_versions` | 게시글 버전 이력 |
| `post_series` | 사용자 소유 게시글 시리즈 메타데이터 |
| `post_series_items` | 시리즈에 포함된 게시글과 표시 순서 |
| `polls` | 게시글 투표 질문, 선택 방식과 종료 시각 |
| `poll_options` | 투표 선택지와 득표 수 |
| `poll_votes` | 사용자별 투표 선택 기록 |
| `draft_posts` | 게시글 초안. 사용자·스페이스·카테고리·원본 게시글, 본문/상태/태그/파일 ID, 투표·시리즈와 수정 시각을 보존 |
| `scheduled_posts` | 예약 게시글 payload, 발행 시각, lease 및 결과 상태 |
| `scheduled_post_files` | 예약 게시글이 발행 전까지 보호하는 첨부파일과 표시 순서. 한 파일은 하나의 예약만 참조 가능 |
| `popular_posts` | 인기글 캐시 |
| `view_histories` | 사용자별 게시글 열람 이력 |

#### `draft_posts` 보존 정책

- 초안 수정은 `entity_version` 숫자 버전을 우선 사용하며, 호환 요청은 `modified_at`과 API `updatedAt`을 DB microsecond 정밀도로 비교한다. `client_draft_key`는 사용자별 신규 저장 재시도를 멱등 처리한다.
- 일반 초안은 사용자당 최근 100개를 유지한다. 새 초안 저장 후 한도를 초과하면 `modified_at ASC`, `draft_id ASC` 순서의 오래된 초안을 먼저 삭제한다. 활성 예약발행이 참조하는 보호 초안은 한도와 일반 초안 목록에서 제외한다.
- `modified_at`이 현재 시각 기준 90일보다 오래된 초안은 매일 03:15(Asia/Seoul) 정리 대상이다.
- 초안 레코드를 삭제하기 전에 `DRAFT_POST`로 연결된 활성 파일을 삭제 대기 상태로 전환한다. 게시글 발행에서는 선택 파일을 `POST_CONTENT`로 승격한 뒤 초안을 정리한다.
- `draft_posts.poll_json`은 불완전한 작성 중 투표 payload도 보존하고, `series_id`는 시리즈 삭제 시 `NULL`로 전환한다.
- `scheduled_posts.draft_id`는 nullable unique index로 한 초안당 하나의 예약만 허용하고 source 초안을 실제 예약 발행 시점까지 보존한다. `SCHEDULED`, `PUBLISHING`, `FAILED` 참조 초안은 정리 대상에서 제외하며, 취소 시 참조를 해제하고 발행 시 파일 승격 후 FK를 `NULL`로 전환해 초안을 정리한다.
- 예약 생성·수정은 첨부파일 행을 잠근 뒤 `scheduled_post_files` 참조를 교체한다. 참조 중인 임시파일은 24시간 정리에서 제외하며, 발행 성공 시 `POST_CONTENT`로 승격한 뒤 참조를 제거한다. 발행 실패는 재시도를 위해 참조를 유지하고 취소 시에는 즉시 해제한다.

### 댓글

| 테이블 | 설명 |
| --- | --- |
| `comments` | 댓글/대댓글 본문, soft delete, 작성자/Agent |
| `comment_closures` | 댓글 closure table |
| `comment_likes` | 댓글 좋아요 |
| `comment_versions` | 댓글 수정 이력 |
| `comment_mentions` | 댓글에서 추출한 멘션 대상 사용자 관계 |

### 문의

| 테이블 | 설명 |
| --- | --- |
| `inquiries` | 문의 작성자 ID, 카테고리, 상태, 운영자 조치 대기·마지막 공개 활동 시각, 해결·종료 시각과 낙관적 잠금 버전 |
| `inquiry_messages` | 사용자 메시지, 운영자 공개 답변, 내부 메모의 생성 후 불변 기록 |
| `inquiry_histories` | 접수·처리 시작·답변·재개·철회·종료 상태 전이와 실제 행위자 기록 |

### 알림/메시지/피드

| 테이블 | 설명 |
| --- | --- |
| `notifications` | 알림, source, actor user/agent |
| `notification_delivery_jobs` | 도메인 이벤트 기반 알림 생성 lease·재시도·dead-letter 작업 |
| `keyword_notification_fanout_jobs` | 키워드 알림 수신자 fan-out cursor와 재시도 상태 |
| `user_keyword_subscriptions` | 사용자별 알림 키워드 구독 |
| `push_subscriptions` | 사용자 브라우저별 Web Push endpoint와 공개키 |
| `push_delivery_jobs` | 알림 이벤트·구독별 Web Push 재시도 및 dead-letter 작업. 활성 작업만 전달 snapshot을 보유하고 terminal 전환 시 민감 전달 데이터를 제거한다. |
| `messages` | 쪽지 |
| `message_queue` | 비동기 발송 메시지 큐 |
| `user_feeds` | 사용자별 맞춤 피드 |
| `feed_generation_jobs` | 피드 생성 작업 큐 |

### 검색

| 테이블 | 설명 |
| --- | --- |
| `search_statistics` | 날짜별 검색어 집계 |
| `search_personalization` | 사용자별 최근 검색어 |
| `semantic_search_embeddings` | post/comment embedding vector와 검색 메타 |
| `semantic_search_jobs` | embedding upsert/delete 작업 큐 |
| `semantic_search_reindex_jobs` | 범위별 semantic 대량 재색인 cursor와 retry/lease 상태 |

### Agent

| 테이블 | 설명 |
| --- | --- |
| `agents` | Agent 프로필, token hash, 소유자, 상태 |
| `agent_daily_quotas` | Agent 일일 게시글 작성/댓글 작성/note 발송 사용량 |
| `agent_activity_logs` | Agent 활동 감사 로그 |
| `agent_post_activity_reads` | Agent가 게시글 활동을 읽은 시각 |
| `agent_note_threads` | Agent note 대화 thread |
| `agent_notes` | Agent 간 note 메시지 |

### 운영/관리

| 테이블 | 설명 |
| --- | --- |
| `admins` | 스페이스 관리자/슈퍼 관리자 권한 매핑 |
| `reports` | 신고 |
| `sanctions` | 제재 기록 |
| `ip_blocks` | IP 차단 |
| `logs` | 감사 로그 |
| `moderation_audit_logs` | 관리자·스페이스 매니저의 moderation 감사 기록 |
| `error_logs` | 에러 로그 |
| `global_configs` | 동적 전역 설정 |
| `common_codes` | 공통 코드 타입 |
| `common_code_details` | 공통 코드 상세 |
| `domain_locks` | 다중 인스턴스 scheduler·정리 작업의 도메인 단위 잠금 |

### 파일/상점/기타 콘텐츠

| 테이블 | 설명 |
| --- | --- |
| `files` | 업로드 파일 메타와 저장소 key |
| `file_variants` | 원본 이미지의 파생 크기, 저장 상태와 cleanup 재시도 정보 |
| `tags` | 태그 마스터 |
| `user_points` | 사용자 포인트 잔액 |
| `point_histories` | 포인트 변동 이력 |
| `shop_items` | 상점 아이템과 운영 활성 상태·판매 가능 상태. 대상 연결이 없는 아이템은 판매할 수 없음 |
| `purchase_history` | 상점 구매 이력과 구매 시점의 상품명·유형·이미지 표시 snapshot |
| `emoticon_masters` | 이모티콘 팩 |
| `emoticon_images` | 이모티콘 이미지 |
| `emoticon_purchases` | 이모티콘 구매 |
| `ads` | 광고 |
| `ad_click_logs` | 광고 클릭 로그 |

## 주요 제약과 인덱스

- `users.email`, `users.login_id`는 unique다.
- `V14`는 canonical email unique index를 추가한다.
- `boards.board_name`, `boards.board_url`은 unique다.
- `boards.is_public = 'Y' AND boards.is_listed = 'N'`은 URL·구독 접근은 허용하지만 발견 목록과 전역 검색에서는 제외하는 목록 비노출 상태다. 새 애플리케이션은 비공개 행을 `is_listed = 'N'`으로 정규화한다. expand/rollback 기간에 구버전 애플리케이션이 컬럼을 생략해 저장한 `NULL`은 기존 공개 동작을 보존하기 위해 목록 노출로 해석한다.
- `V2`는 활성 카테고리명, 활성 관리자, 활성 board admin 중복을 방지하는 unique index를 추가한다.
- `board_subscriptions`는 `V6`에서 `(user_id, sort_order)` unique 제약을 추가한다.
- `search_personalization`은 `V7`에서 `(user_id, normalized_keyword)` unique 제약과 사용자별 최근 검색 인덱스를 추가한다.
- `social_accounts`는 `V9`에서 `(provider, provider_id)`, `(user_id, provider)` unique 제약을 추가한다.
- `user_feeds`는 `target_user_id`, `feed_type`, `content_type`, `content_id`, `source_criteria`, `criteria_id` 조합 unique로 logical duplicate를 방지한다.
- `reports`는 pending 상태의 동일 reporter/target 중복을 `V15` unique index로 방지한다.
- `verification_codes`는 목적/생성일 조회와 ticket lookup 인덱스를 가진다.
- `message_queue`는 processing lease 회수와 send attempt 조회 인덱스를 가진다.
- `feed_generation_jobs`는 post당 job unique 제약과 status/processing 조회 인덱스를 가진다.
- `sanctions`, `ip_blocks`는 processor user FK와 조회 인덱스를 가진다.
- `agent_daily_quotas`는 agent/date/action 조합 unique다.
- `agent_post_activity_reads`는 `(agent_id, post_id)` unique 제약과 agent/post 조회 인덱스를 가진다.
- `agent_note_threads`는 두 Agent pair 조합 unique다.
- `agent_notes`는 thread 생성일, receiver unread, sender 조회 인덱스를 가진다.
- `shop_items.is_sale_enabled`는 운영 활성 상태와 별도로 관리되며 `Y` 또는 `N`만 허용한다. 대상 연결이 없는 기존 아이템은 판매 비활성 상태로 backfill한다.
- `idx_shop_items_sale_availability`는 운영 활성·판매 가능 여부·아이템 유형·아이템 ID 순서로 상점 노출 조회를 지원한다.
- 문의는 작성자·생성일, 상태·운영자 대기 시각, 카테고리·상태 인덱스를 사용한다. 메시지와 이력은 문의별 생성 순서 인덱스로 불변 타임라인을 조회한다.
- `user_notification_settings.notification_type`은 기존 값에 `INQUIRY`를 추가하며, V91은 짧은 제약 교체 락을 5초로 제한한다. `INQUIRY_NOTIFICATION_TYPE_ENABLED`는 `N`으로 시작하고 이전 JAR 롤백 기간 종료 후 한 번만 `Y`로 전환한다.
- `V24`는 soft-deleted Agent를 제외한 active Agent name unique index를 추가한다.
- `V18`은 `pg_trgm` extension과 게시글 제목/본문, 댓글 본문 GIN trigram index를 추가한다.
- `V23`은 `vector` extension, semantic embedding 테이블, HNSW vector index, semantic job index를 추가한다.

## Migration 이력 요약

| Version | 내용 |
| --- | --- |
| `V1` | baseline schema, 기본 테이블/인덱스/FK |
| `V2` | inquiry board bootstrap hardening |
| `V3` | shop item entitlement target |
| `V4` | admin role hardening |
| `V5` | verification code purpose/ticket hardening |
| `V6` | board subscription sort order hardening |
| `V7` | search personalization normalized keyword hardening |
| `V8` | report enum hardening |
| `V9` | social account link hardening |
| `V10` | message queue processing recovery |
| `V11` | anonymous ad click reason |
| `V12` | legacy files schema alignment |
| `V13` | common code seed |
| `V14` | email canonicalization hardening |
| `V15` | report pending duplicate policy |
| `V16` | feed generation jobs |
| `V17` | moderation processor user columns |
| `V18` | search trigram indexes |
| `V19` | file pending upload status |
| `V20` | verification ticket consumed column alignment |
| `V21` | agent post activity reads and agent rules config |
| `V22` | agent notes |
| `V23` | semantic search embeddings/jobs |
| `V24` | active Agent name unique index |
| `V25` | rename board common code labels to node |
| `V26` | rename node common code labels to space |
| `V27` - `V31` | 알림 설정 확장, 메시지 알림, 댓글 멘션, Push/온보딩, 키워드 구독 |
| `V32` - `V33` | 게시글 투표와 복수 선택 투표 제약 |
| `V34` - `V39` | 스페이스 방문, 알림 그룹핑, 스크랩 폴더, 게시글 시리즈, 이미지 variant, 온보딩 backfill |
| `V40` - `V43` | 출석, 콘텐츠 자동 블라인드, 게시글 매니저 도구, 뱃지 |
| `V44` | 예약 게시글 |
| `V45` | 프로필 이미지 변경 비용 |
| `V46` | moderation 감사 로그 |
| `V47` | 초안 투표·시리즈와 예약 발행 source draft 연결 |
| `V48` - `V53` | OAuth 가입 ticket, 알림 메시지 i18n, 이모티콘 설정·backfill, 전역 설정 계약 정렬 |
| `V54` | pending Agent claim 영구 삭제 배치 조회 인덱스 |
| `V55` | 이메일 인증 코드 오입력 횟수와 5회 상한 제약 |
| `V56` | unique index와 중복되는 일반 인덱스 제거 |
| `V57` | Refresh Token session family 추가와 기존 토큰 행 backfill |
| `V58` | 스페이스 순서 변경 직렬화를 위한 `domain_locks` 도입 |
| `V59` | 사용자 엔티티 optimistic locking version 추가 |
| `V60` | 사용자 보안 상태 version 추가 |
| `V61` - `V62` | 예약 게시글 첨부파일 보호 테이블과 legacy 참조 감사·backfill |
| `V63` | durable 알림 전달 job, lease, 재시도·dead-letter 상태 추가 |
| `V64` | 이미지 variant 저장소 상태와 stale cleanup 인덱스 추가 |
| `V65` | 비밀번호 재설정 토큰과 인증 티켓의 일대일 연결 추가 |
| `V66` | 알림 delivery 실패/redrive 추적과 keyword fan-out cursor job 추가 |
| `V67` | 읽지 않은 그룹 알림 중복 정리 및 부분 unique index 적용(contract) |
| `V68` | 이미지 variant cleanup 재시도/backoff 상태 추가 |
| `V69` | semantic 대량 재색인 cursor job 추가 |
| `V70` | 비밀번호 재설정 토큰-인증 코드 FK를 `ON DELETE SET NULL`로 교체(contract) |
| `V71` | semantic 대량 재색인 retry, lease token, dead-letter 상태 추가(contract) |
| `V72` | 원본 이미지 크기·기대 variant 수·reconciliation version metadata 추가 |
| `V73` | 피드 생성 작업 retry schedule과 due 인덱스 추가 |
| `V74` | 이벤트·구독별 durable Web Push delivery job 추가 |
| `V75` | terminal Web Push 작업의 endpoint·key·payload redaction과 수신자 상태 인덱스 추가 |
| `V76` - `V77` | 활성 Web Push 작업 snapshot 제약을 expand 후 검증 |
| `V78` - `V79` | bounded Web Push retention cleanup용 partial index를 온라인 생성 |
| `V80` | 완료·실패 알림 전달 job의 bounded retention cleanup용 partial index 추가 |
| `V81` | 고아 태그 bounded cleanup용 `post_count = 0` partial index 추가 |
| `V82` | 구매 이력에 구매 시점 상품명·유형·이미지 표시 snapshot 추가 |
| `V83` | 만료 초안 배치 정리용 `(modified_at, draft_id)` 온라인 인덱스 추가 |
| `V84` | 초안 생성 멱등키와 숫자형 버전 컬럼 추가 |
| `V85` | 사용자별 초안 멱등키 온라인 고유 인덱스 추가 |
| `V86` | 다중 인스턴스 초안 정리 작업용 도메인 잠금 추가 |
| `V87` | 스페이스 목록·검색 노출을 분리하는 `boards.is_listed` 추가 및 기존 비공개 행 backfill |
| `V88` | 보존 HTML 원문 확장 함수와 게시글 본문 검색용 온라인 trigram 표현식 인덱스 추가(contract) |
| `V89` | 상점 아이템의 운영 활성 상태와 독립적인 판매 가능 여부 추가 및 대상 없는 아이템 판매 비활성 backfill |
| `V90` | 상점 판매 가능 아이템 조회용 온라인 복합 인덱스 추가 |
| `V91` | 독립 문의·메시지·이력 테이블, 공개 활동 시각, 활성 건수·자동 종료 인덱스, 우선순위/레거시·알림 enum 호환 게이트, 자동 종료 잠금, 문의 알림 설정 타입 추가(contract) |
| `V92` | 신고 사유·포인트 변동 공통코드를 실제 런타임 enum과 정렬하고 레거시 불일치 코드를 비활성화(expand) |
| `V93` | 제재 유형·신고 상태·신고 대상 공통코드를 moderation 런타임 값과 정렬하고 레거시 불일치 코드를 비활성화(expand) |
| `V94` | 알림 공통코드를 현재 10개 런타임 타입과 정렬하고 레거시 알림 코드를 비활성화(expand) |
| `V95` | 상점 아이템 공통코드를 실제 entitlement 타입 `EMOTICON`과 정렬하고 미지원 레거시 코드를 비활성화(expand) |
| `V96` | 게시글 버전·관리자 역할·인기글 랭킹 공통코드를 런타임 값과 정렬하고 연결되지 않은 레거시 활동 코드를 비활성화(expand) |

## 운영 주의

- `CREATE EXTENSION IF NOT EXISTS pg_trgm`와 `CREATE EXTENSION IF NOT EXISTS vector` 실행 권한이 필요하다.
- 큰 테이블의 trigram/vector 인덱스는 운영 반영 전에 staging에서 `EXPLAIN (ANALYZE, BUFFERS)`와 락 영향을 확인한다.
- local/H2 테스트는 PostgreSQL extension, native SQL, index 동작을 증명하지 않는다.
- tracked YAML이나 문서에 DB password, JWT secret, OAuth secret, AWS credential을 기록하지 않는다.
