# Admin 도메인 가이드

`admin` 도메인은 스페이스 관리자/슈퍼관리자 권한 관리, IP 차단, 대시보드 통계를 제공합니다.

## 1. 주요 기능 및 로직
- 슈퍼관리자 관리: 목록 조회, 권한 부여/해제, 중복 방지 검증.
- 스페이스 관리자 관리: 특정 스페이스 담당 관리자 생성, 전체 목록 조회, 활성/비활성 전환.
- 스페이스 관리자 조회/변경: 특정 스페이스의 현재 관리자 조회 및 교체.
- IP 차단: 관리자 계정으로 IP 차단 등록·해제·목록 조회.
- 대시보드 통계: 전체 사용자 수, 게시글 수, 보류 중 신고 건수, 최근 24시간 활성 사용자 수 제공.
- 심화 통계: 기간별 가입·활동·콘텐츠 지표를 관리자 대시보드에 제공.
- 스페이스 정렬: 활성·비활성 스페이스 전체 ID를 검증한 뒤 순서를 원자적으로 변경.
- 문의 관리: 문의 게시글 목록과 상세를 조회합니다.
- IP 차단 여부 검사: 요청 IP가 차단 상태인지 글로벌 인터셉터에서 활용.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-------------------------------------- | :---------------------------- |
| `GET` | `/api/v1/admin/super` | 슈퍼관리자 목록 |
| `PUT` | `/api/v1/admin/super/active` | 슈퍼관리자 권한 부여 |
| `PUT` | `/api/v1/admin/super/deactive` | 슈퍼관리자 권한 회수 |
| `POST` | `/api/v1/admin/admins` | 스페이스 관리자 생성 |
| `GET` | `/api/v1/admin/admins` | 스페이스 관리자 전체 조회 |
| `PUT` | `/api/v1/admin/admins/{adminId}/deactivate` | 관리자 비활성화 |
| `PUT` | `/api/v1/admin/admins/{adminId}/activate` | 관리자 활성화 |
| `GET` | `/api/v1/admin/boards/{boardId}/manager` | 스페이스 관리자 조회 |
| `PUT` | `/api/v1/admin/boards/{boardId}/manager` | 스페이스 관리자 변경 |
| `POST` | `/api/v1/admin/ip-blocks` | IP 차단 등록 |
| `DELETE` | `/api/v1/admin/ip-blocks/{ipAddress}` | IP 차단 해제 |
| `GET` | `/api/v1/admin/ip-blocks` | 차단 IP 목록 조회 |
| `GET` | `/api/v1/admin/stats` | 대시보드 요약 통계 조회 |
| `GET` | `/api/v1/admin/stats/deep` | 기간별 심화 통계 조회 |
| `PUT` | `/api/v1/admin/boards/order` | 전체 스페이스 순서 원자적 변경 |
| `GET` | `/api/v1/admin/inquiries` | 문의 게시글 목록 |
| `GET` | `/api/v1/admin/inquiries/{postId}` | 문의 게시글 상세 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `admins` | `Admin` | 스페이스 관리자/역할 매핑 |
| `ip_blocks` | `IpBlock` | 차단 IP 및 만료 정보 |
| `users` | `User` | 관리자/슈퍼관리자 대상 회원 |
| `boards` | `Board` | 관리자 할당 대상 스페이스 |
| `posts` | `Post` | 게시글 집계용 |
| `reports` | `Report` | 신고 집계용 |
