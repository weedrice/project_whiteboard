# Search 도메인 가이드

`search` 도메인은 통합 검색, 게시글 검색, semantic 검색, 인기 키워드 집계, 개인 검색 기록 관리 기능을 제공합니다.

## 1. 주요 기능 및 로직
- 검색 기록: 검색 시 검색어를 일(日) 단위로 집계(`search_statistics`), 로그인 사용자는 개인화 기록(`search_personalization`)을 최신 순으로 유지합니다.
- 통합 검색: 게시글/댓글/사용자를 미리보기 형태로 최대 5건씩 조회하며, 차단 사용자 게시글을 제외하고 이미지 존재 여부를 계산합니다.
- 게시글 검색: 검색 타입/스페이스 필터로 게시글만 검색하고 썸네일 여부(hasImage)를 포함합니다.
- semantic 검색: pgvector embedding 검색을 수행하고, 비활성/장애 시 keyword fallback을 반환합니다. semantic 색인과 scoped 검색은 활성 공개 스페이스의 비밀글이 아닌 post/comment만 대상으로 합니다.
- backfill: 슈퍼관리자 API로 기존 post/comment embedding job을 enqueue합니다.
- 대량 재색인: cursor job은 페이지별 lease와 지수 backoff를 사용하며 5회 실패하면 `FAILED`로 격리합니다. 슈퍼관리자는 원인을 해소한 뒤 실패 job을 수동 redrive할 수 있습니다.
- 최근 검색 관리: 최근 검색어 목록 조회, 단건 삭제, 전체 삭제 제공.
- 인기 키워드: 일간/주간/월간 기간별 상위 키워드 목록 제공.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------------- | :------------------------------- |
| `GET` | `/api/v1/search?q=` | 통합 검색 및 검색 기록 적재 |
| `GET` | `/api/v1/search/posts` | 게시글 검색 |
| `GET` | `/api/v1/search/semantic` | semantic 검색 및 keyword fallback |
| `GET` | `/api/v1/search/popular` | 인기 키워드 조회 |
| `GET` | `/api/v1/search/recent` | 내 최근 검색어 조회 |
| `DELETE` | `/api/v1/search/recent/{logId}` | 최근 검색어 단건 삭제 |
| `DELETE` | `/api/v1/search/recent` | 최근 검색어 전체 삭제 |
| `POST` | `/api/v1/admin/search/semantic/backfill` | semantic embedding backfill enqueue |
| `POST` | `/api/v1/admin/search/semantic/reindex/jobs/{jobId}/redrive` | 실패한 대량 재색인 job 수동 재시도 |

## 3. 관련 DB 테이블

| 테이블명 | 매핑/접근 방식 | 설명 |
| :------- | :----- | :--- |
| `search_statistics` | JPA `SearchStatistic` | 키워드별 일자·검색 횟수 집계 |
| `search_personalization` | JPA `SearchPersonalization` | 사용자별 최근 검색어 로그 |
| `semantic_search_embeddings` | `SemanticSearchEmbeddingRepository` JDBC | post/comment embedding 검색 문서 |
| `semantic_search_jobs` | JDBC record `SemanticSearchJob` | embedding upsert/delete 작업 큐 |
| `semantic_search_reindex_jobs` | JDBC cursor job | 범위별 대량 재색인 cursor, lease, retry/dead-letter 상태 |

대량 재색인 job의 오류 요약은 최대 500자로 제한한다. `FAILED` job은 자동 처리 대상에서 제외되며, 원인을 해소한 뒤 슈퍼관리자 API로 redrive하면 현재 cursor를 유지한 채 retry 상태만 초기화한다.
