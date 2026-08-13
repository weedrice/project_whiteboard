# Badge 도메인 가이드

`badge` 도메인은 뱃지 정의, 사용자 획득 기록, 대표 뱃지와 기존 사용자 콘텐츠 뱃지 보정을
관리한다.

## 1. 주요 기능과 로직

- 뱃지 목록: 정렬 순서와 코드 순으로 모든 뱃지 정의를 반환한다. 사용자가 획득하지 않은 뱃지도
  `acquired: false`로 포함한다.
- 자동 수여: 게시글·댓글 작성 수, 연속 출석, 게시글 좋아요 수가 기준에 도달하면 미보유 뱃지를
  수여하고 `BADGE` 알림을 발행한다.
- 중복 방지: `(user_id, badge_code)` 유일 키와 충돌 무시 삽입을 함께 사용해 같은 뱃지를 한 번만
  수여한다.
- 대표 뱃지: 본인이 획득한 뱃지만 대표로 지정할 수 있다. `badgeCode`가 `null` 또는 공백이면
  대표 뱃지를 해제하고 공개 작성자 projection 캐시를 무효화한다.
- 일괄 보정: 슈퍼 관리자만 활성 사용자를 ID 오름차순으로 200명씩 조회해 게시글·댓글 수 기반
  미보유 뱃지를 동기적으로 보정한다. 출석·인기 게시글 뱃지는 이 작업의 대상이 아니다.

## 2. 수여 기준

| 기준 | 뱃지 코드 |
| :-- | :-- |
| 게시글 1·10·100개 | `FIRST_POST`, `POSTS_10`, `POSTS_100` |
| 댓글 1·10·100개 | `FIRST_COMMENT`, `COMMENTS_10`, `COMMENTS_100` |
| 연속 출석 7·30일 | `ATTENDANCE_7`, `ATTENDANCE_30` |
| 단일 게시글 좋아요 10·50개 | `POPULAR_POST_10`, `POPULAR_POST_50` |

## 3. API Endpoints

| Method | URI | 설명 |
| :-- | :-- | :-- |
| `GET` | `/api/v1/users/{userId}/badges` | 활성 사용자의 전체 뱃지 획득 상태 조회 |
| `GET` | `/api/v1/users/me/badges` | 내 전체 뱃지 획득 상태 조회 |
| `PUT` | `/api/v1/users/me/badges/representative` | 대표 뱃지 지정 또는 해제 |
| `POST` | `/api/v1/admin/badges/backfill` | 기존 활성 사용자의 콘텐츠 뱃지 일괄 보정 |

대표 뱃지 요청은 `{ "badgeCode": "POSTS_10" }` 형태이며 최대 50자다. 일괄 보정 응답은
`scannedUsers`와 새로 수여된 `awardedBadges`를 반환한다.

## 4. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :-- | :-- | :-- |
| `badges` | `Badge` | 코드, 이름, 설명, 아이콘, 등급과 정렬 순서 |
| `user_badges` | `UserBadge` | 사용자별 뱃지 획득 시각 |
| `users` | `User` | `representative_badge_code`에 대표 뱃지 코드 저장 |

뱃지 등급은 `BRONZE`, `SILVER`, `GOLD` 중 하나다.
