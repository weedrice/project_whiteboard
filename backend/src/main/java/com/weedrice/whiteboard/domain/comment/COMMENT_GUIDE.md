# Comment 도메인 가이드

`comment` 도메인은 게시글의 댓글과 관련된 모든 기능을 담당합니다. (댓글/대댓글 CRUD, 좋아요 등)

## 1. 주요 기능 및 로직

`CommentService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **댓글 목록 조회 (`getComments`):**
  - 특정 게시글의 댓글 목록을 페이징하여 조회합니다.
  - 1차 자식 댓글(대댓글)까지 함께 조회하여 계층 구조로 반환합니다.
  - N+1 쿼리 문제를 방지하기 위해, 부모 댓글 ID 목록으로 자식 댓글들을 한 번에 조회한 후 메모리에서 조합합니다.

- **대댓글 더보기 (`getReplies`):**
  - 특정 부모 댓글에 속한 대댓글 목록을 페이징하여 조회합니다.

- **단일 댓글 조회 (`getComment`):**
  - 특정 댓글의 상세 정보를 조회합니다.

- **댓글/대댓글 생성 (`createComment`):**
  - 새로운 댓글 또는 대댓글을 생성합니다.
  - `parent_id` 유무에 따라 일반 댓글과 대댓글을 구분합니다.
  - **Closure Table (`comment_closures`)**을 사용하여 계층 관계를 저장합니다. 이를 통해 깊은 대댓글 구조도 효율적으로 조회할 수 있습니다.
  - 댓글 생성 시, `posts` 테이블의 `comment_count`를 1 증가시킵니다.
  - **버전 관리**: 댓글 생성 시 `comment_versions` 테이블에 'CREATE' 이력을 저장합니다.
  - 알림을 보내기 위해 `NotificationEvent`를 발행합니다.

- **댓글 수정 (`updateComment`):**
  - 댓글 작성자만 내용을 수정할 수 있습니다.
  - **버전 관리**: 댓글 수정 전 내용을 `comment_versions` 테이블에 'MODIFY' 이력으로 저장합니다.

- **댓글 삭제 (`deleteComment`):**
  - 댓글 작성자 또는 관리자만 삭제할 수 있습니다.
  - 실제 데이터를 삭제하는 대신, `is_deleted` 플래그를 'Y'로 변경합니다. (Soft Delete)
  - **버전 관리**: 댓글 삭제 시 `comment_versions` 테이블에 'DELETE' 이력을 저장합니다.
  - `posts` 테이블의 `comment_count`를 1 감소시킵니다.

- **좋아요/좋아요 취소 (`likeComment`, `unlikeComment`):**
  - 특정 댓글에 대한 좋아요를 추가하거나 취소합니다.
  - `comment_likes` 테이블에 정보를 기록/삭제하고, `comments` 테이블의 `like_count`를 동기화합니다.
  - 좋아요 시, 댓글 작성자에게 알림을 보내기 위해 `NotificationEvent`를 발행합니다.

## 2. API Endpoints

`CommentController`에서 다음 API를 제공합니다.

| Method | URI                                  | 설명               |
| :----- | :----------------------------------- | :----------------- |
| `GET`    | `/api/v1/posts/{postId}/comments`    | 댓글 목록 조회     |
| `GET`    | `/api/v1/comments/{commentId}/replies` | 대댓글 더보기      |
| `GET`    | `/api/v1/comments/{commentId}`       | 단일 댓글 조회     |
| `POST`   | `/api/v1/posts/{postId}/comments`    | 댓글/대댓글 생성   |
| `PUT`    | `/api/v1/comments/{commentId}`       | 댓글 수정          |
| `DELETE` | `/api/v1/comments/{commentId}`       | 댓글 삭제          |
| `POST`   | `/api/v1/comments/{commentId}/like`  | 댓글 좋아요        |
| `DELETE` | `/api/v1/comments/{commentId}/like`  | 댓글 좋아요 취소   |

## 3. 관련 DB 테이블

`comment` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명          | 엔티티          | 설명             |
| :---------------- | :-------------- | :--------------- |
| `comments`        | `Comment`       | 댓글 정보        |
| `comment_likes`   | `CommentLike`   | 댓글 좋아요 정보 |
| `comment_closures`| `CommentClosure`| 댓글 계층 관계 (Closure Table) |
| `comment_versions`| `CommentVersion`| 댓글 버전 이력 |

이 문서는 `comment` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
