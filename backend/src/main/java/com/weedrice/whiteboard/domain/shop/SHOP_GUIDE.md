# Shop 도메인 가이드

`shop` 도메인은 포인트로 구매할 수 있는 아이템(상점)과 관련된 기능을 담당합니다.

## 1. 주요 기능 및 로직

`ShopService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **아이템 목록 조회 (`getShopItems`):**
  - 현재 판매 중(`is_active = 'Y'`)인 아이템 목록을 페이징하여 조회합니다.
  - `itemType` 파라미터로 특정 타입의 아이템만 필터링할 수 있습니다.

- **아이템 상세 조회 (`getShopItem`):**
  - 특정 아이템의 상세 정보를 조회합니다.

- **아이템 구매 (`purchaseItem`):**
  - `PointService`를 호출하여 사용자의 포인트가 충분한지 확인하고 차감합니다.
  - 포인트 차감이 성공하면, `purchase_history` 테이블에 구매 이력을 기록합니다.
  - 이 모든 과정은 하나의 트랜잭션으로 처리되어 데이터 정합성을 보장합니다.

- **구매 이력 조회 (`getPurchaseHistories`):**
  - 현재 로그인한 사용자의 아이템 구매 이력을 페이징하여 조회합니다.

## 2. API Endpoints

`ShopController`에서 다음 API를 제공합니다.

| Method | URI                                  | 설명               |
| :----- | :----------------------------------- | :----------------- |
| `GET`    | `/api/v1/shop/items`                 | 상점 아이템 목록 조회|
| `POST`   | `/api/v1/shop/items/{itemId}/purchase` | 아이템 구매        |
| `GET`    | `/api/v1/shop/me/purchases`          | 내 구매 이력 조회  |

## 3. 관련 DB 테이블

`shop` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명            | 엔티티            | 설명             |
| :------------------ | :---------------- | :--------------- |
| `shop_items`        | `ShopItem`        | 판매 아이템 정보 |
| `purchase_history`  | `PurchaseHistory` | 아이템 구매 이력 |
| `user_points`       | `UserPoint`       | 아이템 구매 시 포인트 차감 |

이 문서는 `shop` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
