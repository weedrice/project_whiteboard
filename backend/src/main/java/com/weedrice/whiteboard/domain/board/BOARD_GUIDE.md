# Board 도메인 가이드

`board` 도메인은 게시판 메타 정보, 카테고리, 구독 관리와 게시판별 최신 게시글 제공을 담당합니다.

## 1. 주요 기능 및 로직
- 게시판 조회: 활성 게시판/인기 게시판/전체 게시판을 조회하고, 관리자 여부·구독 여부·카테고리·최신 게시글 15개를 함께 반환합니다.
- 게시판 상세: 비활성 보드는 작성자/관리자/슈퍼관리자만 접근 가능하도록 검증합니다.
- 구독 관리: 게시판 구독/해지 및 사용자가 정렬한 구독 순서 저장.
- 게시판 생성: 이름·URL 중복 체크 후 기본 카테고리(“일반”)와 게시판 관리자(Admin) 자동 생성.
- 게시판 수정/삭제: 관리자 권한 검사 후 정보 수정 또는 비활성화(Soft Delete).
- 카테고리 관리: 최소 작성 권한(minWriteRole) 기반으로 생성/수정/비활성화 처리.
- 공지 조회: 게시판별 공지 게시글 목록 제공.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :----------------------------------------------- | :-------------------------- |
| `GET` | `/api/v1/boards` | 활성 게시판 목록 조회 |
| `GET` | `/api/v1/boards/all` | 전체 게시판 목록 조회 (SUPER_ADMIN) |
| `GET` | `/api/v1/boards/top` | 인기 게시판 상위 15개 조회 |
| `GET` | `/api/v1/boards/{boardUrl}` | 게시판 상세/카테고리/구독 상태 조회 |
| `GET` | `/api/v1/boards/{boardUrl}/notices` | 게시판 공지 게시글 목록 |
| `POST` | `/api/v1/boards` | 게시판 생성 |
| `PUT` | `/api/v1/boards/{boardUrl}` | 게시판 정보 수정 (게시판/슈퍼 관리자) |
| `DELETE` | `/api/v1/boards/{boardUrl}` | 게시판 비활성화 (게시판/슈퍼 관리자) |
| `GET` | `/api/v1/boards/{boardUrl}/categories` | 활성 카테고리 목록 |
| `POST` | `/api/v1/boards/{boardUrl}/categories` | 카테고리 생성 (게시판/슈퍼 관리자) |
| `PUT` | `/api/v1/boards/categories/{categoryId}` | 카테고리 수정 |
| `DELETE` | `/api/v1/boards/categories/{categoryId}` | 카테고리 비활성화 |
| `POST` | `/api/v1/boards/{boardUrl}/subscribe` | 게시판 구독 |
| `DELETE` | `/api/v1/boards/{boardUrl}/subscribe` | 게시판 구독 해지 |
| `PUT` | `/api/v1/boards/subscriptions/order` | 내 구독 게시판 순서 변경 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `boards` | `Board` | 게시판 메타 정보/상태 |
| `board_categories` | `BoardCategory` | 게시판별 카테고리와 작성 권한 |
| `board_subscriptions` | `BoardSubscription` | 사용자별 게시판 구독 및 정렬 순서 |
| `admins` | `Admin` | 게시판 관리자 권한 부여 정보 |
| `posts` | `Post` | 게시판 내 게시글(공지·최신글 조회 시 사용) |
