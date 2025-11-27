# Board 도메인 가이드

`board` 도메인은 게시판, 카테고리, 구독 등 게시판의 기본 구조와 관련된 기능을 담당합니다.

## 1. 주요 기능 및 로직

`BoardService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **게시판 목록 조회 (`getActiveBoards`):**
  - 활성화(`is_active = 'Y'`)된 모든 게시판을 정렬 순서(`sort_order`)에 따라 조회합니다.

- **인기 게시판 조회 (`getTopBoards`):**
  - 게시글 수가 많은 순서대로 상위 15개의 게시판을 조회합니다.

- **게시판 상세 조회 (`getBoardDetails`):**
  - 특정 게시판의 상세 정보(구독자 수, 관리자 닉네임 포함)를 조회합니다.

- **게시판 생성 (`createBoard`):**
  - **인증된 모든 사용자**가 새로운 게시판을 생성할 수 있습니다.
  - 게시판 이름은 중복될 수 없습니다.
  - 생성 시, 기본적으로 '일반' 카테고리가 함께 생성됩니다.
  - 게시판을 생성한 사용자는 해당 게시판의 `BOARD_ADMIN` 권한을 자동으로 부여받습니다.

- **게시판 수정 및 삭제 (`updateBoard`, `deleteBoard`):**
  - **게시판 생성자 또는 전체 관리자(`ROLE_ADMIN`)**만 게시판 정보를 수정하거나 삭제할 수 있습니다.

- **카테고리 관리 (`getActiveCategories`, `createCategory` 등):**
  - 특정 게시판에 속한 카테고리를 조회, 생성, 수정, 삭제합니다.
  - 카테고리 삭제는 실제 데이터를 삭제하는 대신 `is_active` 플래그를 'N'으로 변경하는 Soft Delete 방식입니다.

- **게시판 구독/해제 (`subscribeBoard`, `unsubscribeBoard`):**
  - 사용자가 특정 게시판을 구독하거나 구독을 해제합니다.

## 2. API Endpoints

`BoardController`에서 다음 API를 제공합니다.

| Method | URI                                      | 설명                     | 권한                |
| :----- | :--------------------------------------- | :----------------------- | :------------------ |
| `GET`    | `/api/v1/boards`                         | 전체 게시판 목록 조회    | 누구나              |
| `GET`    | `/api/v1/boards/top`                     | 인기 게시판 목록 조회    | 누구나              |
| `GET`    | `/api/v1/boards/{boardId}`               | 특정 게시판 상세 조회    | 누구나              |
| `POST`   | `/api/v1/boards`                         | 게시판 생성              | 인증된 사용자     |
| `PUT`    | `/api/v1/boards/{boardId}`               | 게시판 수정              | 생성자 또는 관리자  |
| `DELETE` | `/api/v1/boards/{boardId}`               | 게시판 삭제              | 생성자 또는 관리자  |
| `GET`    | `/api/v1/boards/{boardId}/categories`    | 카테고리 목록 조회       | 누구나              |
| `POST`   | `/api/v1/boards/{boardId}/subscribe`     | 게시판 구독              | 인증된 사용자     |
| `DELETE` | `/api/v1/boards/{boardId}/subscribe`     | 게시판 구독 해제         | 인증된 사용자     |
| `POST`   | `/api/v1/admin/boards/{boardId}/categories` | 카테고리 생성 (관리자)   | 관리자              |
| `PUT`    | `/api/v1/admin/boards/categories/{categoryId}` | 카테고리 수정 (관리자)   | 관리자              |
| `DELETE` | `/api/v1/admin/boards/categories/{categoryId}` | 카테고리 삭제 (관리자)   | 관리자              |

## 3. 관련 DB 테이블

`board` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명              | 엔티티                | 설명                   |
| :-------------------- | :-------------------- | :--------------------- |
| `boards`              | `Board`               | 게시판 마스터 정보     |
| `board_categories`    | `BoardCategory`       | 게시판별 카테고리 정보 |
| `board_subscriptions` | `BoardSubscription`   | 사용자별 게시판 구독 정보 |
| `admins`              | `Admin`               | 게시판 생성 시 관리자 권한 부여 |
| `posts`               | `Post`                | 게시글 수 집계 시 사용 |

이 문서는 `board` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
