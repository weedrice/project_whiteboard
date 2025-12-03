# Common Code 도메인 가이드

`common` 도메인은 시스템 전반에서 사용되는 **공통 코드(Common Code)**를 관리하는 기능을 담당합니다. (예: 게시글 카테고리, 신고 사유, 은행 코드 등)

## 1. 주요 기능 및 로직

`CommonCodeService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **코드 그룹 관리 (`CommonCode`):**
  - 공통 코드를 그룹핑하는 마스터 코드입니다. (예: `REPORT_REASON`, `BANK_CODE`)
  - `typeCode`를 PK로 사용하여 유일성을 보장합니다.

- **상세 코드 관리 (`CommonCodeDetail`):**
  - 각 그룹에 속하는 상세 코드 값입니다. (예: `SPAM`, `ABUSE` / `KB`, `SHINHAN`)
  - `sortOrder`를 통해 정렬 순서를 제어할 수 있습니다.
  - `isActive` 플래그를 통해 사용 여부를 제어할 수 있습니다.

- **코드 조회:**
  - 특정 그룹의 활성화된 상세 코드 목록을 정렬 순서대로 조회합니다.

## 2. API Endpoints

`CommonCodeController`에서 다음 API를 제공합니다.

| Method | URI                                      | 설명                     | 권한          |
| :----- | :--------------------------------------- | :----------------------- | :------------ |
| `POST`   | `/api/v1/common-codes`                   | 코드 그룹 생성           | `SUPER`       |
| `GET`    | `/api/v1/common-codes`                   | 전체 코드 그룹 조회      | `SUPER`       |
| `GET`    | `/api/v1/common-codes/{typeCode}`        | 특정 코드 그룹 조회      | `SUPER`       |
| `PUT`    | `/api/v1/common-codes/{typeCode}`        | 코드 그룹 수정           | `SUPER`       |
| `POST`   | `/api/v1/common-codes/{typeCode}/details`| 상세 코드 생성           | `SUPER`       |
| `GET`    | `/api/v1/common-codes/{typeCode}/details`| 상세 코드 목록 조회      | 누구나        |
| `PUT`    | `/api/v1/common-codes/details/{detailId}`| 상세 코드 수정           | `SUPER`       |
| `DELETE` | `/api/v1/common-codes/details/{detailId}`| 상세 코드 삭제           | `SUPER`       |

## 3. 관련 DB 테이블

`common` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명              | 엔티티              | 설명                   |
| :-------------------- | :------------------ | :--------------------- |
| `common_codes`        | `CommonCode`        | 공통 코드 그룹 마스터  |
| `common_code_details` | `CommonCodeDetail`  | 상세 코드 정보         |

이 문서는 `common` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
