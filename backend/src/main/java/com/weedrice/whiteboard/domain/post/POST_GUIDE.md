# Post 도메인 가이드

`post` 도메인은 게시글의 생성, 조회, 수정, 삭제 및 관련 상호작용(좋아요, 스크랩, 임시저장, 버전관리)을 담당합니다.

## 1. 주요 기능 및 로직

`PostService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **게시글 목록 조회 (`getPosts`):**
  - 특정 게시판(`boardUrl`)의 게시글 목록을 페이징하여 조회합니다. 
  - 카테고리(`categoryId`), 검색어(`keyword`), 최소 좋아요 수(`minLikes`) 필터링이 가능합니다.
  - 검색어가 존재할 경우 `SearchService`를 통해 검색 기록을 남깁니다.

- **게시글 상세 조회 (`getPostById`):**
  - 특정 게시글의 상세 내용을 조회합니다.
  - 조회 시, 해당 게시글의 `view_count`가 1 증가합니다.
  - 로그인한 사용자의 경우, `view_histories` 테이블에 열람 기록을 남깁니다.
  - **로그인한 사용자의 경우, 해당 게시글에 '좋아요'를 눌렀는지 여부(`isLiked`), '스크랩' 여부(`isScrapped`)를 함께 반환합니다.**

- **게시글 열람 기록 업데이트 (`updateViewHistory`):**
  - 사용자가 게시글을 읽는 동안 체류 시간(`durationMs`)과 마지막으로 읽은 댓글(`lastReadCommentId`) 정보를 업데이트합니다.
  - 프론트엔드에서 주기적으로 또는 페이지 이탈 시 호출하여 상세한 열람 이력을 남깁니다.

- **게시글 생성 (`createPost`):**
  - 게시글을 생성하고, `posts` 테이블에 저장합니다.
  - `boardUrl`을 통해 게시판을 식별합니다.
  - 게시글 생성 시, `TagService`를 호출하여 태그 정보를 처리합니다.
  - `post_versions` 테이블에 'CREATE' 타입의 버전 이력을 기록합니다.

- **게시글 수정 (`updatePost`):**
  - 게시글 작성자만 내용을 수정할 수 있습니다.
  - 수정 전 내용을 `post_versions` 테이블에 'MODIFY' 타입으로 기록한 후, `posts` 테이블의 내용을 업데이트합니다.
  - `TagService`를 호출하여 태그 정보를 업데이트합니다.

- **게시글 삭제 (`deletePost`):**
  - 게시글 작성자 또는 관리자만 삭제할 수 있습니다.
  - 실제 데이터를 삭제하는 대신, `is_deleted` 플래그를 'Y'로 변경합니다. (Soft Delete)
  - `post_versions` 테이블에 'DELETE' 타입의 버전 이력을 기록합니다.

- **좋아요/좋아요 취소 (`likePost`, `unlikePost`):**
  - 특정 게시글에 대한 좋아요를 추가하거나 취소합니다.
  - `post_likes` 테이블에 정보를 기록/삭제하고, `posts` 테이블의 `like_count`를 동기화합니다.

- **스크랩/스크랩 취소 (`scrapPost`, `unscrapPost`):**
  - 특정 게시글을 스크랩하거나 취소합니다.

- **임시저장 (`saveDraftPost`, `getDraftPosts` 등):**
  - 작성 중인 게시글을 임시로 저장, 조회, 삭제합니다.

- **버전 관리 (`getPostVersions`):**
  - 게시글의 수정/삭제 이력을 조회합니다.

## 2. API Endpoints

`PostController`에서 다음 API를 제공합니다.

| Method | URI                                  | 설명                   |
| :----- | :----------------------------------- | :--------------------- |
| `GET`    | `/api/v1/boards/{boardUrl}/posts`    | 게시글 목록 조회       |
| `GET`    | `/api/v1/posts/{postId}`             | 게시글 상세 조회       |
| `PUT`    | `/api/v1/posts/{postId}/history`     | 게시글 열람 기록 업데이트 |
| `POST`   | `/api/v1/boards/{boardUrl}/posts`    | 게시글 생성            |
| `PUT`    | `/api/v1/posts/{postId}`             | 게시글 수정            |
| `DELETE` | `/api/v1/posts/{postId}`             | 게시글 삭제            |
| `POST`   | `/api/v1/posts/{postId}/like`        | 게시글 좋아요          |
| `DELETE` | `/api/v1/posts/{postId}/like`        | 게시글 좋아요 취소     |
| `POST`   | `/api/v1/posts/{postId}/scrap`       | 게시글 스크랩          |
| `DELETE` | `/api/v1/posts/{postId}/scrap`       | 게시글 스크랩 취소     |
| `GET`    | `/api/v1/users/me/scraps`            | 내 스크랩 목록 조회    |
| `GET`    | `/api/v1/users/me/drafts`            | 내 임시저장 글 목록 조회 |
| `POST`   | `/api/v1/drafts`                     | 임시저장 글 생성/수정  |
| `GET`    | `/api/v1/drafts/{draftId}`           | 임시저장 글 상세 조회  |
| `DELETE` | `/api/v1/drafts/{draftId}`           | 임시저장 글 삭제       |
| `GET`    | `/api/v1/posts/{postId}/versions`    | 게시글 수정 이력 조회  |

## 3. 관련 DB 테이블

`post` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명            | 엔티티                | 설명                     |
| :------------------ | :-------------------- | :----------------------- |
| `posts`             | `Post`                | 게시글 정보              |
| `post_likes`        | `PostLike`            | 게시글 좋아요 정보       |
| `scraps`            | `Scrap`               | 게시글 스크랩 정보       |
| `draft_posts`       | `DraftPost`           | 임시 저장 게시글 정보    |
| `post_versions`     | `PostVersion`         | 게시글 수정 이력         |
| `view_histories`    | `ViewHistory`         | 게시글 열람 기록         |
| `popular_posts`     | `PopularPost`         | 인기글 집계 정보         |

이 문서는 `post` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
