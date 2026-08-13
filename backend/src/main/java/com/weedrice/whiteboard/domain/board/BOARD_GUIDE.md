# Board 도메인 가이드

`board` 도메인은 스페이스 메타 정보, 카테고리, 구독 관리와 스페이스별 최신 게시글 제공을 담당합니다.

## 1. 주요 기능 및 로직
- 스페이스 조회: 활성 스페이스/인기 스페이스/전체 스페이스를 조회하고, 관리자 여부·구독 여부·카테고리·최신 게시글 15개를 함께 반환합니다.
- 스페이스 상세: 비활성 보드는 작성자/관리자/슈퍼관리자만 접근 가능하도록 검증합니다.
- 구독 관리: 스페이스 구독/해지 및 사용자가 정렬한 구독 순서 저장.
- 스페이스 생성: 이름·URL 중복 체크 후 기본 카테고리(“일반”)와 스페이스 관리자(Admin) 자동 생성.
- 스페이스 수정/삭제: 관리자 권한 검사 후 정보 수정 또는 비활성화(Soft Delete).
- 스페이스 관리자 변경: 관리자 후보를 검색하고 스페이스 관리자를 교체합니다.
- 문의 스페이스 보장: 시스템 문의 스페이스가 필요한 경우 생성/복구합니다.
- 카테고리 관리: 최소 작성 권한(minWriteRole) 기반으로 생성/수정/비활성화 처리.
- 공지 조회: 스페이스별 공지 게시글 목록 제공.
- 개인화 탐색: 관심 topic 기반 추천 스페이스와 구독 스페이스의 최근 갱신 정보를 제공.
- 정렬: 관리자는 활성 카테고리 전체 순서를 원자적으로 변경.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :----------------------------------------------- | :-------------------------- |
| `GET` | `/api/v1/boards` | 활성 스페이스 목록 조회 |
| `GET` | `/api/v1/boards/all` | 전체 스페이스 목록 조회 (SUPER_ADMIN) |
| `GET` | `/api/v1/boards/top` | 인기 스페이스 상위 15개 조회 |
| `GET` | `/api/v1/boards/recommendations` | 관심 topic 기반 추천 스페이스 조회 |
| `GET` | `/api/v1/boards/recent-updates` | 구독 스페이스 최근 갱신 정보 조회 |
| `GET` | `/api/v1/boards/{boardUrl}` | 스페이스 상세/카테고리/구독 상태 조회 |
| `GET` | `/api/v1/boards/{boardUrl}/notices` | 스페이스 공지 게시글 목록 |
| `POST` | `/api/v1/boards` | 스페이스 생성 |
| `POST` | `/api/v1/boards/inquiry/ensure` | 문의 스페이스 보장 |
| `PUT` | `/api/v1/boards/{boardUrl}` | 스페이스 정보 수정 (스페이스/슈퍼 관리자) |
| `PUT` | `/api/v1/boards/{boardUrl}/manager` | 스페이스 관리자 변경 |
| `GET` | `/api/v1/boards/{boardUrl}/manager-candidates` | 스페이스 관리자 후보 조회 |
| `DELETE` | `/api/v1/boards/{boardUrl}` | 스페이스 비활성화 (스페이스/슈퍼 관리자) |
| `GET` | `/api/v1/boards/{boardUrl}/categories` | 활성 카테고리 목록 |
| `POST` | `/api/v1/boards/{boardUrl}/categories` | 카테고리 생성 (스페이스/슈퍼 관리자) |
| `PUT` | `/api/v1/boards/categories/{categoryId}` | 카테고리 수정 |
| `DELETE` | `/api/v1/boards/categories/{categoryId}` | 카테고리 비활성화 |
| `PUT` | `/api/v1/boards/{boardUrl}/categories/order` | 활성 카테고리 전체 순서 변경 |
| `POST` | `/api/v1/boards/{boardUrl}/subscribe` | 스페이스 구독 |
| `DELETE` | `/api/v1/boards/{boardUrl}/subscribe` | 스페이스 구독 해지 |
| `PUT` | `/api/v1/boards/subscriptions/order` | 내 구독 스페이스 순서 변경 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `boards` | `Board` | 스페이스 메타 정보/상태 |
| `board_categories` | `BoardCategory` | 스페이스별 카테고리와 작성 권한 |
| `board_subscriptions` | `BoardSubscription` | 사용자별 스페이스 구독 및 정렬 순서 |
| `admins` | `Admin` | 스페이스 관리자 권한 부여 정보 |
| `posts` | `Post` | 스페이스 내 게시글(공지·최신글 조회 시 사용) |
