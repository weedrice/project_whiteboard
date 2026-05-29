# Sanction 도메인 가이드

`sanction` 도메인은 사용자 제재 기록, 조회, 제재 효과 적용, 작성 가능 여부 검증을 담당한다.

## 1. 주요 기능과 로직

- 제재 생성: 슈퍼 관리자 권한으로 대상 사용자에게 `WARNING`, `MUTE`, `BAN` 유형과 기간, 비고, 관련 콘텐츠 정보를 등록한다.
- BAN 적용: 영구 BAN은 등록 시 즉시 사용자 계정을 정지 상태로 전환한다.
- BAN 검증: 로그인, OAuth 계정 해석, Agent 소유자 검증, 신고, 쪽지, 상점, 포인트 등 사용자 활성 상태가 필요한 흐름에서 `validateNotBanned()`를 사용한다.
- MUTE 검증: 게시글 작성/수정, 임시글 작성, 댓글/대댓글 작성, 쪽지 발송 흐름에서 `validateNotMuted()`를 사용해 현재 유효한 MUTE 제재를 차단한다.
- WARNING 기록: 사용자 상태를 직접 변경하지 않고 제재 이력으로 보관한다.
- 제재 조회: 전체 또는 특정 사용자 제재 이력을 페이지 단위로 조회한다.
- 상태 확인: `isUserBanned()`, `isUserMuted()`로 현재 유효한 제재 존재 여부를 확인한다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-- | :-- |
| `POST` | `/api/v1/admin/sanctions` | 사용자 제재 등록 |
| `GET` | `/api/v1/admin/sanctions` | 제재 이력 조회 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :-- | :-- | :-- |
| `sanctions` | `Sanction` | 제재 유형, 기간, 처리자, 대상 사용자, 관련 콘텐츠 |

## 4. 주의 사항

- `processor_user_id`는 현재 제재 처리자 FK이며, 기존 `admin_id`는 nullable legacy 참조다.
- 기간이 있는 제재는 현재 시각 기준 활성 여부를 판단한다.
- 새 쓰기 기능을 추가할 때는 BAN/MUTE 정책 적용 여부를 `SanctionPolicyService` 기준으로 확인한다.
