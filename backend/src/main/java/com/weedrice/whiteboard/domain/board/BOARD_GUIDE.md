# Board 도메인 가이드

`board` 도메인은 노드 메타 정보, 카테고리, 구독 관리와 노드별 최신 게시글 제공을 담당합니다.

## 1. 주요 기능 및 로직
- 노드 조회: 활성 노드/인기 노드/전체 노드을 조회하고, 관리자 여부·구독 여부·카테고리·최신 게시글 15개를 함께 반환합니다.
- 노드 상세: 비활성 보드는 작성자/관리자/슈퍼관리자만 접근 가능하도록 검증합니다.
- 구독 관리: 노드 구독/해지 및 사용자가 정렬한 구독 순서 저장.
- 노드 생성: 이름·URL 중복 체크 후 기본 카테고리(“일반”)와 노드 관리자(Admin) 자동 생성.
- 노드 수정/삭제: 관리자 권한 검사 후 정보 수정 또는 비활성화(Soft Delete).
- 노드 관리자 변경: 관리자 후보를 검색하고 노드 관리자를 교체합니다.
- 문의 노드 보장: 시스템 문의 노드이 필요한 경우 생성/복구합니다.
- 카테고리 관리: 최소 작성 권한(minWriteRole) 기반으로 생성/수정/비활성화 처리.
- 공지 조회: 노드별 공지 게시글 목록 제공.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :----------------------------------------------- | :-------------------------- |
| `GET` | `/api/v1/boards` | 활성 노드 목록 조회 |
| `GET` | `/api/v1/boards/all` | 전체 노드 목록 조회 (SUPER_ADMIN) |
| `GET` | `/api/v1/boards/top` | 인기 노드 상위 15개 조회 |
| `GET` | `/api/v1/boards/{boardUrl}` | 노드 상세/카테고리/구독 상태 조회 |
| `GET` | `/api/v1/boards/{boardUrl}/notices` | 노드 공지 게시글 목록 |
| `POST` | `/api/v1/boards` | 노드 생성 |
| `POST` | `/api/v1/boards/inquiry/ensure` | 문의 노드 보장 |
| `PUT` | `/api/v1/boards/{boardUrl}` | 노드 정보 수정 (노드/슈퍼 관리자) |
| `PUT` | `/api/v1/boards/{boardUrl}/manager` | 노드 관리자 변경 |
| `GET` | `/api/v1/boards/{boardUrl}/manager-candidates` | 노드 관리자 후보 조회 |
| `DELETE` | `/api/v1/boards/{boardUrl}` | 노드 비활성화 (노드/슈퍼 관리자) |
| `GET` | `/api/v1/boards/{boardUrl}/categories` | 활성 카테고리 목록 |
| `POST` | `/api/v1/boards/{boardUrl}/categories` | 카테고리 생성 (노드/슈퍼 관리자) |
| `PUT` | `/api/v1/boards/categories/{categoryId}` | 카테고리 수정 |
| `DELETE` | `/api/v1/boards/categories/{categoryId}` | 카테고리 비활성화 |
| `POST` | `/api/v1/boards/{boardUrl}/subscribe` | 노드 구독 |
| `DELETE` | `/api/v1/boards/{boardUrl}/subscribe` | 노드 구독 해지 |
| `PUT` | `/api/v1/boards/subscriptions/order` | 내 구독 노드 순서 변경 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `boards` | `Board` | 노드 메타 정보/상태 |
| `board_categories` | `BoardCategory` | 노드별 카테고리와 작성 권한 |
| `board_subscriptions` | `BoardSubscription` | 사용자별 노드 구독 및 정렬 순서 |
| `admins` | `Admin` | 노드 관리자 권한 부여 정보 |
| `posts` | `Post` | 노드 내 게시글(공지·최신글 조회 시 사용) |
