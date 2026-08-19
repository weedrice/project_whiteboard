# Shop 도메인 가이드

`shop` 도메인은 포인트로 구매 가능한 상점 아이템과 구매 내역을 관리합니다.

## 1. 주요 기능 및 로직
- 상품 조회: 소스 활성 상태(`is_active`)와 판매 허용 상태(`is_sale_enabled`)가 모두 활성화된 아이템만 유형별로 페이지 조회.
- 구매: 두 활성 상태·가격·entitlement를 검증하고 포인트 잔액이 충분한 경우 `SPEND` 이력으로 차감한 뒤, entitlement 부여와 가격 스냅샷을 포함한 구매 이력을 같은 트랜잭션에 저장.
- 구매 이력: 사용자별 구매 내역을 최신순으로 제공.
- 관리자 판매 제어: 슈퍼 관리자가 상품 소스 상태와 독립적으로 판매를 중지하거나 재개하며, 변경 사유를 moderation audit log에 기록.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------------- | :----------------------- |
| `GET` | `/api/v1/shop/items` | 상점 아이템 목록 조회 |
| `POST` | `/api/v1/shop/items/{itemId}/purchase` | 아이템 구매 |
| `GET` | `/api/v1/shop/me/purchases` | 내 구매 이력 조회 |
| `GET` | `/api/v1/admin/shop/items` | 관리자용 상품 검색 및 상태 조회 |
| `PUT` | `/api/v1/admin/shop/items/{itemId}/sale-status` | 판매 중지/재개 및 감사 사유 기록 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `shop_items` | `ShopItem` | 상점 상품 정보/가격/소스 활성 여부/판매 허용 여부(`Y`/`N`) |
| `purchase_history` | `PurchaseHistory` | 구매 시점/가격/사용자 기록 |

`is_active`는 원본 리소스와의 연동 상태를, `is_sale_enabled`는 관리자의 판매 정책을 나타냅니다. 공개 목록 노출과 구매는 두 값이 모두 `Y`일 때만 허용됩니다.
