# Frontend–Backend 연결 계약 감사 및 개선 결과

## 기준

- 기준일: 2026-07-14
- 범위: 현재 frontend가 호출하는 backend API
- 호환 정책: URL, HTTP method, `ApiResponse` envelope와 현재 backend wire JSON을 유지하고 frontend 경계에서 정규화
- 제외: backend에만 존재하고 frontend에서 호출하지 않는 기능의 신규 UI 구현

## 개선 완료

| 영역 | 기존 차이 | 적용 결과 |
| --- | --- | --- |
| 공통 페이지 | backend `page/hasNext/hasPrevious`, frontend `number/last` | 공통 wire 타입과 페이지 정규화기로 통합하고 게시판·댓글 무한 스크롤에도 적용 |
| 게시글 목록 | `notice/nsfw`와 `isNotice/isNsfw` 차이 | 모든 연결된 `PostSummary` 경로에 공통 mapper 적용 |
| 게시글 상세 | `isLiked/isScrapped`와 `liked/scrapped` 차이 | API 경계 mapper로 통합하고 화면의 임시 alias 처리 제거 |
| 게시글 카테고리 | 요약 응답에 전체 `Category` 타입 사용 | `PostCategorySummary(categoryId, name)`로 분리 |
| 게시판·카테고리 요청 | 생성 요청의 미지원 필드와 카테고리 `isActive` 전송 | backend가 수신하는 필드만 전송하도록 요청 타입 분리 |
| 사용자 | 회원가입·프로필·액션 응답 타입, 설정 언어 대소문자와 오래된 알림 필드 | 실제 DTO에 맞는 응답/요청 타입으로 교체 |
| 관리자 | `active`, `superAdmin` wire 이름 | frontend 내부 `isActive`, `isSuperAdmin`으로 정규화 |
| 개인 피드 | `read` wire 이름과 포함 게시글 목록 DTO | `isRead` 및 `PostSummary`를 함께 정규화 |
| 이모티콘 | 목록의 `images: null` | frontend 내부에서 항상 빈 배열로 정규화 |
| 게시글 좋아요 | backend 현재 좋아요 수 응답을 frontend가 `void`로 선언 | `number` 응답으로 수정 |

## 이미 일치

- frontend에서 호출하는 URL과 HTTP method는 backend controller와 모두 일치한다.
- 성공/실패 응답은 공통 `ApiResponse` envelope를 사용한다.
- 알림 API는 snake/camel alias와 페이지 정보를 기존 정규화 계층에서 처리하고 있다.
- 메시지, 신고, 광고, 출석, 뱃지, 파일, 포인트, 예약 게시글, 설정, 오류 로그의 현재 연결 경로에서는 추가적인 URL·method 불일치가 확인되지 않았다.

## 미연결 후속

아래 항목은 backend API가 존재하지만 현재 frontend 호출 경로가 없어 이번 변경에서 UI를 추가하지 않았다.

- 댓글 좋아요·취소
- 게시글 조회 기록 갱신 및 게시글 버전 조회
- 보드 관리자 신고 흐름
- 상점과 공통 코드 관리
- 뱃지·검색 등 운영 백필 API

신규 화면이나 제품 흐름이 확정될 때 별도 기능 계획으로 다룬다.

## 외부 Agent 접근 경로

- 외부 Agent는 frontend나 backend Agent API를 직접 호출하지 않고 MCP를 통해서만 접근한다.
- `/api/v1/agents/**`는 MCP와 backend 사이의 내부 연동 계약이며 frontend 연결 감사 대상이 아니다.
- frontend는 사용자 계정에서 Agent를 연결·조회·정지·재활성화·삭제하는 `/users/me/agents/**` 흐름만 담당한다.
- MCP 서버는 별도 repository가 소유하며, tool별 backend endpoint 매핑과 MCP–backend 계약 감사도 해당 repository에서 관리한다.

## 검증

- frontend type-check
- 실제 backend wire fixture 기반 frontend contract mapper 테스트
- 게시판·댓글 무한 스크롤 다음/마지막 페이지 테스트
- backend DTO 직렬화 계약 테스트
- 전체 frontend/backend 테스트 결과는 작업 완료 보고에 기록한다.
