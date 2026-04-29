# 검색 LIKE 쿼리 전문검색 전환 설계

## 배경

현재 게시글/댓글 검색은 Querydsl `containsIgnoreCase` 기반 조건을 사용한다. PostgreSQL에서는 대체로 `lower(column) like '%keyword%'` 형태가 되어 일반 B-tree 인덱스를 활용하기 어렵고, 데이터가 늘수록 게시글 본문과 댓글 본문 검색 비용이 커질 수 있다.

이번 문서는 전환 설계만 다룬다. DB extension, index, migration, API 변경은 포함하지 않는다.

## 현재 검색 경로

### 통합 검색

- API: `GET /api/v1/search?q=...`
- 서비스: `SearchService.integratedSearch`
- 게시글: `PostRepositoryCustomImpl.searchPostsByKeyword`
  - 검색 대상: `posts.title`, `posts.contents`
  - 조건: 공개/활성 게시판, 삭제되지 않은 게시글, 비밀글 제외 또는 본인 글 허용, 차단 작성자 제외
  - 정렬: 전달된 pageable sort가 없으면 `createdAt desc`
- 댓글: `CommentRepositoryCustomImpl.searchCommentsByKeyword`
  - 검색 대상: `comments.content`
  - 조건: 삭제되지 않은 댓글, 삭제되지 않은 게시글, 공개/활성 게시판, 비밀글 제외 또는 본인 게시글 허용, 차단 댓글 작성자 제외
  - 정렬: `comment.createdAt desc`
- 사용자: `UserRepositoryCustomImpl.searchUsersVisibleTo`
  - 검색 대상: `users.displayName`
  - 조건: 차단 사용자 제외
  - 이 문서의 전문검색 전환 대상은 아니며, 통합 검색 응답에서 현재 동작을 유지한다.
- 게시판: `BoardRepository.findByBoardNameContainingIgnoreCaseAndIsActiveTrueAndIsPublicTrueOrderBySortOrderAscBoardIdAsc`
  - 검색 대상: `boards.boardName`
  - 조건: 공개/활성 게시판
  - 정렬: `sortOrder asc`, `boardId asc`
  - 이 문서의 전문검색 전환 대상은 아니며, 통합 검색 응답에서 현재 동작을 유지한다.

### 게시글 상세 검색

- API: `GET /api/v1/search/posts?q=...`
- 서비스: `SearchService.searchPosts`
- repository: `PostRepositoryCustomImpl.searchPosts`
- `searchType`별 검색 대상:
  - `TITLE`: `posts.title`
  - `CONTENT`: `posts.contents`
  - `AUTHOR`: `users.displayName` 또는 `agents.name`
  - 그 외/null: `posts.title` 또는 `posts.contents`
- 게시판 지정이 없으면 공개/활성 게시판만 검색한다.
- 게시판 지정이 있으면 `BoardAccessPolicy`로 접근 가능 여부를 먼저 검증한다.
- 허용 정렬: `createdAt`, `postId`, `viewCount`, `likeCount`
- 기본 정렬: `createdAt desc`, `postId desc`

### 게시판 내 목록 검색

- API: `GET /api/v1/boards/{boardUrl}/posts?keyword=...`
- 컨트롤러/서비스: `PostController.getPosts`, `PostService.getPosts`
- repository: `PostRepositoryCustomImpl.findByBoardIdAndCategoryId`
- 검색 대상: `posts.title` 또는 `posts.contents`
- 추가 조건: 게시판, 카테고리, 최소 좋아요, 삭제 여부, 비밀글 정책, 차단 작성자 제외
- 정렬: pageable sort 또는 `createdAt desc`

### 제외 경로

- `GET /api/v1/search/popular`: 집계된 인기 검색어 조회이며 LIKE/전문검색 쿼리 전환 대상이 아니다.
- `GET /api/v1/search/recent`: 사용자 최근 검색어 조회이며 LIKE/전문검색 쿼리 전환 대상이 아니다.

## 전환 목표

- API URL, response envelope, DTO shape를 유지한다.
- 기존 공개/비밀글/차단/삭제 필터를 그대로 유지한다.
- 게시글 검색은 `title`, `contents`, 작성자 검색의 의미를 유지한다.
- 댓글 검색은 댓글 본문 검색 의미를 유지한다.
- 정렬 파라미터는 그대로 유지하되, 검색 관련성 정렬 도입은 별도 API/제품 결정 후 진행한다.

## 후보 1: pg_trgm GIN 인덱스

`pg_trgm`은 현재 LIKE/부분 문자열 검색 의미와 가장 가깝다.

후보 인덱스 예시:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY idx_posts_title_trgm
    ON posts USING gin (lower(title) gin_trgm_ops)
    WHERE is_deleted = 'N';

CREATE INDEX CONCURRENTLY idx_posts_contents_trgm
    ON posts USING gin (lower(contents) gin_trgm_ops)
    WHERE is_deleted = 'N';

CREATE INDEX CONCURRENTLY idx_comments_content_trgm
    ON comments USING gin (lower(content) gin_trgm_ops)
    WHERE is_deleted = 'N';
```

장점:

- `containsIgnoreCase`와 유사한 부분 문자열 검색을 유지하기 쉽다.
- 짧은 키워드와 중간 문자열 매칭에 비교적 강하다.
- 기존 정렬 파라미터를 유지하기 쉽다.

주의점:

- `lower(column) LIKE lower('%keyword%')` 또는 `lower(column) LIKE :normalizedKeyword` 형태로 쿼리를 명시해야 인덱스 활용 여부를 예측하기 쉽다.
- 본문 HTML이 저장되어 있으면 태그/속성 문자열까지 검색될 수 있다.
- 1-2글자 검색은 효과가 제한될 수 있어 최소 검색어 길이 정책을 함께 검토해야 한다.

## 후보 2: tsvector 전문검색

`tsvector`는 자연어 검색과 ranking을 도입할 때 유리하다.

후보 컬럼/인덱스 예시:

```sql
ALTER TABLE posts
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce(contents_plain_text, '')), 'B')
    ) STORED;

CREATE INDEX CONCURRENTLY idx_posts_search_vector
    ON posts USING gin (search_vector)
    WHERE is_deleted = 'N';

ALTER TABLE comments
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        to_tsvector('simple', coalesce(content_plain_text, ''))
    ) STORED;

CREATE INDEX CONCURRENTLY idx_comments_search_vector
    ON comments USING gin (search_vector)
    WHERE is_deleted = 'N';
```

장점:

- title/body 가중치와 ranking 기반 정렬을 도입할 수 있다.
- 장문 본문 검색에서 LIKE보다 확장성이 좋다.
- 토큰 기반 검색, prefix 검색, highlight 기능 확장 여지가 있다.

주의점:

- 현재 부분 문자열 검색과 결과가 달라질 수 있다.
- 한국어 검색 품질은 PostgreSQL 기본 dictionary만으로 충분하지 않을 수 있다.
- ranking 정렬을 도입하면 기존 `createdAt`, `viewCount`, `likeCount` 정렬 정책과 제품 결정이 필요하다.
- `contents_plain_text`, `content_plain_text` 같은 HTML 제거 텍스트 컬럼 또는 별도 검색 문서 테이블이 필요하다.

## HTML 본문 plain text 분리 필요성

`posts.contents`와 `comments.content`가 HTML 또는 마크업을 포함할 수 있으면 검색 인덱스에는 표시 텍스트만 넣는 편이 안전하다.

권장 방향:

- 저장 시 sanitizing 결과와 별개로 검색용 plain text를 생성한다.
- 기존 본문 컬럼은 API 응답 호환을 위해 유지한다.
- 검색용 컬럼은 DB migration 승인 후 추가한다.
- backfill은 운영 부하를 고려해 batch로 나누어 수행한다.

## 권장 단계

1. 운영 데이터의 평균/상위 본문 길이, 검색 빈도, 느린 쿼리 로그를 수집한다.
2. 현재 검색 의미를 유지해야 하면 `pg_trgm`을 1차 후보로 검증한다.
3. 관련성 정렬과 자연어 검색 품질이 목표면 `tsvector`와 검색용 plain text 컬럼을 별도 설계한다.
4. staging DB에서 `EXPLAIN (ANALYZE, BUFFERS)`로 다음 조합을 비교한다.
   - 통합 검색 게시글 preview
   - 통합 검색 댓글 preview
   - 게시글 상세 검색 `TITLE`, `CONTENT`, 전체
   - 게시판 내 목록 검색 keyword + 정렬
5. migration 승인 후 index 생성, query 변경, repository 통합 테스트를 별도 커밋으로 진행한다.

## 이번 단계에서 하지 않는 일

- DB migration 작성
- PostgreSQL extension 활성화
- repository 쿼리 변경
- API 응답, DTO, 정렬 파라미터 변경
- 검색 결과 ranking 정책 변경
