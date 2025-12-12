# Post 도메인 가이드

`post` 도메인은 게시글 CRUD, 트렌드/태그/스크랩/초안 관리와 조회 이력을 책임집니다.

## 1. 주요 기능 및 로직
- 게시글 목록: 게시판/카테고리/최소 좋아요 필터로 조회하며 차단 사용자 글은 제외, 썸네일 여부(hasImage) 계산.
- 트렌드/최신: 최근 24시간 인기 글, 게시판별 최신 글 15개, 태그별 글을 제공하고 좋아요/스크랩/구독 여부를 함께 반환합니다.
- 게시글 상세: 조회수 증가 및 ViewHistory 갱신, 태그·이미지 URL·좋아요/스크랩 여부·관리자 여부 포함 응답.
- 작성/수정/삭제: 카테고리 권한/공지 작성 권한 검증, 태그·첨부 파일 연결, 버전 기록, 작성시 포인트 +50 / 삭제시 -50.
- 좋아요/스크랩: 중복 방지, 좋아요 시 작성자에게 알림 발행, 스크랩/해제 지원.
- 초안: 초안 저장/수정/삭제 및 단건/목록 조회.
- 이력: 게시글 버전 목록, 최근 본 글(Page) 조회, 게시글별 열람 기록 수정.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------------------------- | :---------------------------------------- |
| `GET` | `/api/v1/boards/{boardUrl}/posts` | 게시판 게시글 목록 조회(필터/키워드 기록 포함) |
| `GET` | `/api/v1/posts/trending` | 최근 24시간 인기 게시글 목록 |
| `GET` | `/api/v1/posts/{postId}` | 게시글 상세 조회 |
| `PUT` | `/api/v1/posts/{postId}/history` | 게시글 열람 이력(체류/마지막 댓글) 갱신 |
| `POST` | `/api/v1/boards/{boardUrl}/posts` | 게시글 작성 |
| `PUT` | `/api/v1/posts/{postId}` | 게시글 수정 |
| `DELETE` | `/api/v1/posts/{postId}` | 게시글 삭제 |
| `POST` | `/api/v1/posts/{postId}/like` | 게시글 좋아요 |
| `DELETE` | `/api/v1/posts/{postId}/like` | 게시글 좋아요 취소 |
| `POST` | `/api/v1/posts/{postId}/scrap` | 게시글 스크랩 |
| `DELETE` | `/api/v1/posts/{postId}/scrap` | 게시글 스크랩 해제 |
| `GET` | `/api/v1/users/me/scraps` | 내 스크랩 목록 |
| `GET` | `/api/v1/users/me/drafts` | 내 초안 목록 |
| `GET` | `/api/v1/drafts/{draftId}` | 초안 단건 조회 |
| `POST` | `/api/v1/drafts` | 초안 저장/수정 |
| `DELETE` | `/api/v1/drafts/{draftId}` | 초안 삭제 |
| `GET` | `/api/v1/posts/{postId}/versions` | 게시글 버전 이력 조회 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `posts` | `Post` | 게시글 본문/메타/노출 정보 |
| `post_likes` | `PostLike` | 게시글 좋아요 |
| `scraps` | `Scrap` | 사용자 스크랩 |
| `draft_posts` | `DraftPost` | 게시글 초안 |
| `post_versions` | `PostVersion` | 게시글 버전 기록 |
| `view_history` | `ViewHistory` | 사용자별 열람 기록/체류시간 |
| `post_tags` | `PostTag` | 게시글-태그 매핑 |
| `tags` | `Tag` | 태그 메타/사용량 |
