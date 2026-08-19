# Moderation 도메인 가이드

`moderation` 도메인은 사용자 또는 시스템이 수행한 관리 작업을 감사 로그로 남기고, 슈퍼 관리자와
스페이스 관리자가 권한 범위에 맞게 조회하도록 한다.

## 1. 주요 기능과 로직

- 사용자 작업 기록: 게시글 수동 블라인드, 신고 처리, 사용자 상태·슈퍼 관리자 권한 변경,
  스페이스 비활성화, 상점 아이템 판매 중지·재개 같은 작업을 `actorType: USER`로 저장한다.
- 시스템 작업 기록: 신고 누적에 따른 게시글·댓글 자동 블라인드와 해제를 `actorType: SYSTEM`으로
  저장한다.
- 트랜잭션 결합: 기록 메서드는 기존 트랜잭션을 필수로 요구하므로 대상 관리 작업과 감사 로그가
  함께 커밋되거나 롤백된다.
- 전역 조회: 슈퍼 관리자는 작업·수행자 유형·스페이스 ID/URL/이름·수행 사용자 ID/이름·날짜
  범위로 전체 로그를 검색할 수 있다. 이름은 대소문자 구분 없는 부분 일치이고 나머지 문자열
  조건은 정확 일치다.
- 스페이스 조회: 스페이스 관리자는 `boardUrl`에 해당하는 관리 권한을 검증받은 뒤 그 스페이스의
  로그만 조회할 수 있다.
- 날짜 범위: `startDate`는 해당 날짜 00:00부터, `endDate`는 다음 날 00:00 미만까지 포함한다.
  시작일이 종료일보다 늦으면 요청을 거부한다.
- 페이징과 정렬: 기본값은 0페이지, 20건, `createdAt DESC`, `auditId DESC`이며 크기는 최대
  100건이다. 허용 정렬 필드는 `auditId`, `createdAt`, `actorType`, `adminId`, `action`,
  `targetType`, `targetId`다.

## 2. API Endpoints

| Method | URI | 권한 | 설명 |
| :-- | :-- | :-- | :-- |
| `GET` | `/api/v1/admin/moderation-audits` | 슈퍼 관리자 | 전체 moderation 감사 로그 조회 |
| `GET` | `/api/v1/boards/{boardUrl}/manager/audits` | 해당 스페이스 관리자 | 스페이스 범위 감사 로그 조회 |

전역 조회는 `action`, `actorType`, `boardId`, `boardUrl`, `boardName`, `actorUserId`,
`actorName`, `startDate`, `endDate`를 선택 필터로 받는다. 스페이스 조회는 `action`,
`actorType`, `actorUserId`, `startDate`, `endDate`를 받으며 스페이스 조건은 경로에서 고정된다.

## 3. 작업과 대상 값

- 작업: `POST_BLIND`, `POST_UNBLIND`, `POST_AUTO_BLIND`, `POST_AUTO_UNBLIND`,
  `COMMENT_AUTO_BLIND`, `COMMENT_AUTO_UNBLIND`, `REPORT_RESOLVE`, `REPORT_REJECT`,
  `USER_ACTIVATE`, `USER_SUSPEND`, `SUPER_ADMIN_GRANT`, `SUPER_ADMIN_REVOKE`,
  `BOARD_DEACTIVATE`, `SHOP_ITEM_SALE_SUSPEND`, `SHOP_ITEM_SALE_RESUME`
- 대상 유형: `POST`, `COMMENT`, `REPORT`, `USER`, `BOARD`, `SHOP_ITEM`

응답은 수행 사용자와 스페이스가 존재할 때 각각 표시 이름과 스페이스 이름·URL을 함께 반환한다.
시스템 작업은 `actorUserId`와 `actorDisplayName`이 `null`이다.

## 4. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :-- | :-- | :-- |
| `moderation_audit_logs` | `ModerationAuditLog` | 수행 주체, 작업, 대상, 스페이스, 사유와 생성 시각 |

`admin_id`는 이전 관리자 식별자를 보존하기 위한 nullable 필드다. 현재 사용자 작업은
`actor_user_id`로 수행자를 기록한다.
