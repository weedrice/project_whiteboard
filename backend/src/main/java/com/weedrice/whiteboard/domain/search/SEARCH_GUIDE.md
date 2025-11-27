# Search 도메인 가이드

`search` 도메인은 게시글, 댓글, 사용자 등 서비스 전반의 검색 기능과 검색 관련 통계/이력 관리를 담당합니다.

## 1. 주요 기능 및 로직

`SearchService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **통합 검색 (`integratedSearch`):**
  - `type` 파라미터에 따라 게시글, 댓글, 사용자 등 다양한 리소스를 검색합니다.
  - 현재는 게시글(`title`, `contents`), 댓글(`content`), 사용자(`displayName`) 검색을 지원합니다.
  - 각 도메인의 Repository에 구현된 검색 메서드를 호출하여 결과를 취합합니다.

- **검색어 기록 (`recordSearch`):**
  - 검색이 수행될 때마다 호출됩니다.
  - **검색 통계:** `search_statistics` 테이블에 날짜별, 키워드별 검색 횟수를 누적하여 저장합니다.
  - **개인화 검색 이력:** 로그인한 사용자의 경우, `search_personalization` 테이블에 검색어 이력을 저장합니다. 동일한 키워드를 다시 검색하면 최신 시간으로 업데이트됩니다.

- **인기 검색어 조회 (`getPopularKeywords`):**
  - `search_statistics` 테이블의 데이터를 집계하여, 특정 기간(일간, 주간, 월간) 동안 가장 많이 검색된 키워드 목록을 반환합니다.

- **최근 검색어 관리 (`getRecentSearches`, `deleteRecentSearch` 등):**
  - 로그인한 사용자의 최근 검색어 목록을 조회하거나 삭제합니다.

## 2. API Endpoints

`SearchController`에서 다음 API를 제공합니다.

| Method | URI                         | 설명                     |
| :----- | :-------------------------- | :----------------------- |
| `GET`    | `/api/v1/search`            | 통합 검색                |
| `GET`    | `/api/v1/search/popular`    | 인기 검색어 목록 조회    |
| `GET`    | `/api/v1/search/recent`     | 최근 검색어 목록 조회    |
| `DELETE` | `/api/v1/search/recent/{logId}` | 특정 최근 검색어 삭제    |
| `DELETE` | `/api/v1/search/recent`     | 최근 검색어 전체 삭제    |

## 3. 관련 DB 테이블

`search` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명                 | 엔티티                  | 설명                     |
| :----------------------- | :---------------------- | :----------------------- |
| `search_statistics`      | `SearchStatistic`       | 검색 통계 정보           |
| `search_personalization` | `SearchPersonalization` | 사용자별 검색 이력 정보  |

이 문서는 `search` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
