# Report 도메인 가이드

`report` 도메인은 사용자가 부적절한 콘텐츠(게시글, 댓글 등)를 신고하고, 관리자가 이를 처리하는 기능을 담당합니다.

## 1. 주요 기능 및 로직

`ReportService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **신고 접수 (`createReport`):**
  - 사용자가 특정 대상(`targetType`, `targetId`)을 지정하여 신고를 접수합니다.
  - `USER`, `POST`, `COMMENT` 등 대상별로 간편 신고 API도 지원합니다.
  - 동일한 사용자가 동일한 대상을 중복으로 신고할 수 없습니다.
  - 접수된 신고는 `PENDING` 상태로 `reports` 테이블에 저장됩니다.

- **내 신고 목록 조회 (`getMyReports`):**
  - 사용자가 자신이 접수한 신고 목록을 페이징하여 조회합니다.

- **신고 목록 조회 (`getReports`):**
  - 관리자가 신고 목록을 페이징하여 조회합니다.
  - 처리 상태(`status`) 또는 대상 타입(`targetType`)으로 필터링할 수 있습니다.

- **신고 처리 (`processReport`):**
  - 관리자가 접수된 신고를 확인하고, `RESOLVED`(처리 완료) 또는 `REJECTED`(반려) 상태로 변경합니다.
  - 처리한 관리자의 정보와 처리 내용(`remark`)이 함께 기록됩니다.

## 2. API Endpoints

`ReportController` (사용자용) 및 `AdminReportController` (관리자용)에서 다음 API를 제공합니다.

| Method | URI                               | 설명               | 권한          |
| :----- | :-------------------------------- | :----------------- | :------------ |
| `POST`   | `/api/v1/reports`                 | 일반 신고 접수     | 인증된 사용자 |
| `POST`   | `/api/v1/reports/users`           | 사용자 신고 접수   | 인증된 사용자 |
| `POST`   | `/api/v1/reports/posts`           | 게시글 신고 접수   | 인증된 사용자 |
| `POST`   | `/api/v1/reports/comments`        | 댓글 신고 접수     | 인증된 사용자 |
| `GET`    | `/api/v1/reports/me`              | 내 신고 목록 조회  | 인증된 사용자 |
| `GET`    | `/api/v1/admin/reports`           | 신고 목록 조회     | `SUPER`/`ADMIN` |
| `PUT`    | `/api/v1/admin/reports/{reportId}`| 신고 처리          | `SUPER`/`ADMIN` |

## 3. 관련 DB 테이블

`report` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명   | 엔티티   | 설명         |
| :--------- | :------- | :----------- |
| `reports`  | `Report` | 신고 정보    |
| `admins`   | `Admin`  | 신고 처리자 정보 |

이 문서는 `report` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
