# 검색 LIKE 쿼리 최적화 현황

## 기준

| 항목 | 내용 |
| --- | --- |
| 기준일 | 2026-05-29 |
| 관련 migration | `V18__search_trigram_indexes.sql`, `V23__semantic_search_embeddings.sql` |
| 관련 API | `/api/v1/search`, `/api/v1/search/posts`, `/api/v1/search/semantic` |

## 현재 상태

기존 `containsIgnoreCase`/LIKE 의미를 유지하는 검색 경로는 PostgreSQL `pg_trgm` GIN 인덱스로 보강되어 있다.

적용된 migration:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_posts_title_trgm
    ON posts USING gin (lower(title) gin_trgm_ops)
    WHERE is_deleted = 'N';

CREATE INDEX IF NOT EXISTS idx_posts_contents_trgm
    ON posts USING gin (lower(contents) gin_trgm_ops)
    WHERE is_deleted = 'N';

CREATE INDEX IF NOT EXISTS idx_comments_content_trgm
    ON comments USING gin (lower(content) gin_trgm_ops)
    WHERE is_deleted = 'N';
```

별도로 semantic search는 `pgvector` 기반 테이블과 HNSW 인덱스를 사용한다. 자세한 API 계약은 `docs/semantic-search-api-for-mcp.md`를 본다.

## 검색 경로

### 통합 검색

- API: `GET /api/v1/search?q=...`
- 서비스: `SearchService.integratedSearch`
- 대상: 게시글, 댓글, 사용자, 노드
- 게시글/댓글 조건: 삭제 제외, 공개/권한 필터, 비밀글 정책, 차단 관계 반영

### 게시글 검색

- API: `GET /api/v1/search/posts`
- 대상: 제목, 본문, 작성자
- 노드 범위 지정 시 `BoardAccessPolicy`를 먼저 적용한다.
- 허용 정렬은 repository/service 정책을 따른다.

### 노드 내 목록 검색

- API: `GET /api/v1/boards/{boardUrl}/posts?keyword=...`
- 대상: 제목 또는 본문
- 조건: 노드, 카테고리, 최소 좋아요, 삭제 여부, 비밀글 정책, 차단 작성자 제외

### Semantic Search

- API: `GET /api/v1/search/semantic`
- vector search가 가능하면 `similarity DESC, created_at DESC, content_id DESC` 기준으로 반환한다.
- 비활성 상태 또는 embedding provider 장애 시 keyword fallback을 반환한다.
- fallback도 기존 API envelope와 result schema를 유지한다.

## 운영 검증 항목

- 운영 데이터 기준으로 trigram index 사용 여부를 `EXPLAIN (ANALYZE, BUFFERS)`로 확인한다.
- 1-2글자 검색어는 비용과 UX 정책을 함께 보고 제한 또는 fallback 정책을 결정한다.
- HTML 본문 검색에서 태그/속성 문자열 노출이 문제되면 plain text 검색 컬럼 또는 검색 문서 테이블을 별도 설계한다.
- 관련성 정렬을 제품 요구사항으로 채택할 경우 `tsvector` 또는 semantic rank 조합을 별도 설계한다.

## 운영 주의

- `pg_trgm`, `vector` extension 생성 권한이 필요하다.
- 인덱스 생성은 운영 DB 크기에 따라 락/시간 영향을 줄 수 있으므로 staging에서 먼저 확인한다.
- API URL, response envelope, DTO shape는 변경하지 않는 것이 현재 원칙이다.
