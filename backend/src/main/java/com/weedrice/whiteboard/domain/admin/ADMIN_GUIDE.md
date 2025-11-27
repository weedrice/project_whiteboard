# Admin 도메인 가이드

`admin` 도메인은 서비스 운영 및 관리를 위한 기능들을 담당합니다. (관리자 권한 부여, IP 차단 등)

## 1. 주요 기능 및 로직

`AdminService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **관리자 생성 (`createAdmin`):**
  - 특정 사용자에게 관리자 권한을 부여합니다.
  - `role`에 따라 `SUPER`(전체 관리자), `BOARD_ADMIN`(게시판 관리자) 등으로 구분됩니다.
  - `admins` 테이블에 새로운 관리자 정보를 저장합니다.

- **관리자 비활성화/활성화 (`deactivateAdmin`, `activateAdmin`):**
  - 특정 관리자의 권한을 일시적으로 비활성화하거나 다시 활성화합니다.

- **IP 차단/해제 (`blockIp`, `unblockIp`):**
  - 특정 IP 주소의 서비스 접근을 차단하거나 해제합니다.
  - `ip_blocks` 테이블에 차단 정보를 기록/삭제합니다.
  - `IpBlockInterceptor`를 통해 모든 요청에 대해 차단된 IP인지 검사합니다.

## 2. API Endpoints

`AdminController`에서 다음 API를 제공합니다. 모든 엔드포인트는 관리자 권한(`ADMIN` 또는 `SUPER`)이 필요합니다.

| Method | URI                                  | 설명                     | 권한          |
| :----- | :----------------------------------- | :----------------------- | :------------ |
| `POST`   | `/api/v1/admin/admins`               | 신규 관리자 등록         | `SUPER`       |
| `GET`    | `/api/v1/admin/admins`               | 전체 관리자 목록 조회    | `SUPER`       |
| `PUT`    | `/api/v1/admin/admins/{adminId}/deactivate` | 관리자 비활성화          | `SUPER`       |
| `PUT`    | `/api/v1/admin/admins/{adminId}/activate`   | 관리자 활성화            | `SUPER`       |
| `POST`   | `/api/v1/admin/ip-blocks`            | IP 주소 차단             | `SUPER`       |
| `DELETE` | `/api/v1/admin/ip-blocks/{ipAddress}`| IP 주소 차단 해제        | `SUPER`       |
| `GET`    | `/api/v1/admin/ip-blocks`            | 차단된 IP 목록 조회      | `SUPER`       |

## 3. 관련 DB 테이블

`admin` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명      | 엔티티      | 설명             |
| :------------ | :---------- | :--------------- |
| `admins`      | `Admin`     | 관리자 정보      |
| `ip_blocks`   | `IpBlock`   | 차단된 IP 정보   |

이 문서는 `admin` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
