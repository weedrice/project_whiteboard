# Preserved post HTML search index contract

## 배경

보존 HTML은 게시글 본문 안에서 Base64 마커로 저장된다. 기존 `lower(contents)` 검색과
`idx_posts_contents_trgm` 인덱스는 마커 내부의 원문을 보지 못하므로, 보존된 HTML에만 있는
키워드를 검색할 수 없다.

V88은 저장 본문을 다시 쓰지 않고 검색 시점에 마커를 확장하는
`noviis_expand_preserved_post_html(text)` 함수와 그 표현식에 대한 GIN trigram 인덱스를 추가한다.
함수 정의는 정적 분석으로 부작용을 완전히 증명할 수 없는 procedural SQL이므로, additive
변경이지만 migration 정책상 contract 단계로 분류한다.

## 변경과 호환성

- 함수 이름은 기존 객체와 겹치지 않는 전용 이름을 사용한다.
- 새 인덱스는 기존 `posts` 테이블에 `CREATE INDEX CONCURRENTLY`로 생성한다.
- 기존 `idx_posts_contents_trgm` 인덱스는 rollback window 동안 유지한다. 이전 JAR은 종전 검색
  표현식과 인덱스를 계속 사용할 수 있다.
- V88은 `executeInTransaction=false`로 실행한다. 동시 인덱스 생성 중 쓰기 트래픽을 장시간
  막지 않도록 `lock_timeout`을 5초로 제한한다.
- 새 애플리케이션은 함수 기반 검색을 사용하지만 테이블과 기존 인덱스를 제거하거나 변경하지
  않으므로 이전 애플리케이션의 스키마 계약은 유지된다.

## 적용과 실패 복구

- 적용 전 RDS 자동 백업의 최근 복구 가능 시각이 운영 RPO 안에 있는지 확인한다.
- `CREATE INDEX CONCURRENTLY` 실패 후 재시도하기 전에 `pg_index.indisvalid`를 확인한다.
- `idx_posts_expanded_contents_trgm`이 invalid 상태이면 해당 인덱스만
  `DROP INDEX CONCURRENTLY`로 제거한 뒤 Flyway repair와 재실행을 수행한다.
- 같은 이름의 valid 인덱스가 있거나 catalog 상태를 확인할 수 없으면 자동 재시도하지 않고
  운영자가 검토한다.

## 롤백과 후속 정리

- 애플리케이션을 이전 버전으로 되돌려도 기존 `idx_posts_contents_trgm` 인덱스가 남아 있으므로
  종전 검색 경로를 그대로 사용할 수 있다.
- 새 검색 경로를 철회할 때는 새 애플리케이션 사용을 중단한 뒤 표현식 인덱스와 함수를 별도의
  승인된 contract migration에서 제거한다.
- 기존 인덱스 제거는 rollback window 종료 후 catalog 증거와 before/after 실행 계획을 갖춘
  별도 migration으로 진행한다.

## 검증

- 보존 마커 안의 키워드가 일반 검색과 semantic fallback 검색에서 조회되는지 확인한다.
- 일반 본문과 잘못된 Base64 마커가 기존과 동일하게 검색되는지 확인한다.
- `EXPLAIN`에서 함수 기반 검색이 `idx_posts_expanded_contents_trgm`을 사용할 수 있는지 확인한다.
- V88 적용 후 이전 백엔드 revision의 PostgreSQL compatibility smoke test를 실행한다.
