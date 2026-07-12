# NoviIs Mention Notification Policy

작성일: 2026-07-07

## 게시글

- 게시글 생성 시 저장 HTML의 `data-mention-user-id`를 기준으로 멘션 알림을 발송한다.
- 게시글 수정 시에는 멘션 알림을 재발송하지 않는다.
- 수정으로 새 멘션이 추가되더라도 반복 알림과 악용 가능성을 피하기 위해 별도 알림을 만들지 않는다.

## 댓글

- 댓글 생성 시 `mentionedUserIds` 또는 본문 HTML의 `data-mention-user-id`를 기준으로 멘션 알림을 발송한다.
- 댓글 생성 시 선택된 멘션 사용자는 `comment_mentions`에 저장하고 댓글 응답의 `mentions`로 내려준다.
- 댓글 수정 시에는 `comment_mentions` 표시 메타데이터만 교체하고 멘션 알림은 재발송하지 않는다.

## 후속 검토

- 수정 시 새로 추가된 멘션만 알릴 필요가 생기면, 기존 멘션 스냅샷과 신규 멘션의 차집합만 발송하는 정책으로 별도 설계한다.
