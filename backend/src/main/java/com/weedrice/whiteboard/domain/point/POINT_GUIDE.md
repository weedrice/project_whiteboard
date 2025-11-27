# Point 도메인 가이드

`point` 도메인은 사용자의 포인트 적립, 사용 및 이력 관리를 담당합니다.

## 1. 주요 기능 및 로직

`PointService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **포인트 조회 (`getUserPoint`):**
  - 특정 사용자의 현재 보유 포인트를 조회합니다.

- **포인트 이력 조회 (`getPointHistories`):**
  - 특정 사용자의 포인트 적립/사용 이력을 페이징하여 조회합니다.
  - `type` 파라미터(EARN, SPEND 등)로 특정 유형의 이력만 필터링할 수 있습니다.

- **포인트 적립 (`addPoint`):**
  - 특정 사용자에게 포인트를 적립합니다.
  - `user_points` 테이블의 `current_point`를 증가시킵니다.
  - `point_histories` 테이블에 'EARN' 타입의 이력을 기록합니다.

- **포인트 차감 (`subtractPoint`):**
  - 특정 사용자의 포인트를 차감합니다.
  - 차감 전, 사용자의 보유 포인트가 충분한지 확인합니다.
  - `user_points` 테이블의 `current_point`를 감소시킵니다.
  - `point_histories` 테이블에 'SPEND' 타입의 이력을 기록합니다.

## 2. API Endpoints

`PointController`에서 다음 API를 제공합니다.

| Method | URI                         | 설명               |
| :----- | :-------------------------- | :----------------- |
| `GET`    | `/api/v1/points/me`         | 내 포인트 조회     |
| `GET`    | `/api/v1/points/me/history` | 내 포인트 이력 조회|

## 3. 관련 DB 테이블

`point` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명          | 엔티티          | 설명             |
| :---------------- | :-------------- | :--------------- |
| `user_points`     | `UserPoint`     | 사용자별 현재 포인트 정보 |
| `point_histories` | `PointHistory`  | 포인트 변동 이력 |

이 문서는 `point` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
