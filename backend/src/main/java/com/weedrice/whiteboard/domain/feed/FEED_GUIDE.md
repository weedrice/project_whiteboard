# Feed 도메인 가이드

`feed` 도메인은 사용자별 맞춤 콘텐츠 피드와 홈 랜딩 데이터를 제공하는 영역입니다.

## 1. 주요 기능 및 로직
- 피드 조회: 로그인 사용자의 `user_feeds` 데이터를 최신순 페이지로 조회합니다.
- 피드 생성: 게시글 생성 이벤트를 `feed_generation_jobs`에 enqueue하고 스케줄러가 구독/조건 기반 `user_feeds`를 적재합니다.
- 피드 정리: 삭제된 게시글과 연결된 feed를 정리합니다.
- 홈 랜딩: 프론트 홈 화면에 필요한 featured, trending, board spotlight 데이터를 제공합니다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :--------------------------- | :---------------- |
| `GET` | `/api/v1/users/me/feeds` | 내 피드 목록 조회 |
| `GET` | `/api/v1/home/landing` | 홈 랜딩 데이터 조회 |
| `POST` | `/api/v1/admin/feed-generation/jobs/{jobId}/redrive` | 영구 실패한 피드 생성 작업 단건 재시도(SUPER_ADMIN) |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `user_feeds` | `UserFeed` | 사용자별 추천 피드 데이터 |
| `feed_generation_jobs` | `FeedGenerationJob` | 피드 생성 작업 큐. 실패 시 제한된 지수 backoff를 적용하고 5회 실패하면 `FAILED`로 격리합니다. |

피드 작업은 pending/processing/failed/oldest pending gauge와 success/retry/dead-letter/recovered/redrive 결과 counter를 제공합니다.
