# Comment 도메인 가이드

`comment` 도메인은 게시글 댓글 트리 관리, 버전 관리, 좋아요, 알림/포인트 적립을 담당합니다.

## 1. 주요 기능 및 로직
- 댓글 조회: 부모 댓글 페이지네이션 후 자식/후손을 Closure Table로 한번에 불러와 트리 형태로 응답하고, 차단 사용자 댓글은 내용/작성자 정보를 마스킹합니다.
- 댓글 작성: 부모 유효성 검증 후 저장, 게시글 댓글 수 증가, 버전 기록, Closure Table 적재, 작성 포인트(+10) 부여, 부모/게시글 작성자에게 알림 발행.
- 댓글 수정/삭제: 본인 소유 검증, 수정/삭제 시 원본 내용을 버전으로 남기고 댓글 수 및 포인트(-10) 조정.
- 댓글 좋아요: 중복 방지, 좋아요 수 증감 및 작성자에게 알림 발행.
- 내 활동: 내가 작성한 댓글 조회, 특정 댓글 단건 조회, 특정 댓글의 대댓글 페이지 조회.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :----------------------------------- | :-------------------------- |
| `GET` | `/api/v1/posts/{postId}/comments` | 게시글 댓글 트리 조회 |
| `GET` | `/api/v1/comments/{commentId}/replies` | 대댓글 목록 조회 |
| `GET` | `/api/v1/comments/{commentId}` | 댓글 단건 조회 |
| `POST` | `/api/v1/posts/{postId}/comments` | 댓글/대댓글 작성 |
| `PUT` | `/api/v1/comments/{commentId}` | 댓글 수정 |
| `DELETE` | `/api/v1/comments/{commentId}` | 댓글 삭제 |
| `POST` | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 |
| `DELETE` | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 취소 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `comments` | `Comment` | 댓글 본문/작성자/계층 정보 |
| `comment_closure` | `CommentClosure` | 댓글 트리 선조-후손 관계 저장 |
| `comment_versions` | `CommentVersion` | 생성/수정/삭제 시점의 원본 내용 기록 |
| `comment_likes` | `CommentLike` | 댓글 좋아요 정보 |
