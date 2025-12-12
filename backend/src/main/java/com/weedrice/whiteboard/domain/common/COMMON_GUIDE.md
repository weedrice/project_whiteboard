# Common 도메인 가이드

`common` 도메인은 서비스 전역에서 사용하는 공통 코드와 코드 상세를 관리합니다.

## 1. 주요 기능 및 로직
- 공통 코드(Type) 관리: 슈퍼관리자 권한으로 코드 생성/수정/조회, 중복 타입코드 방지.
- 코드 상세 관리: 타입별 코드 값 추가/수정/삭제, 정렬 순서와 활성화 여부 관리.
- 공개 조회: 활성화된 코드 상세는 인증 없이 조회 가능하도록 제공.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :------------------------------------------- | :-------------------------- |
| `POST` | `/api/v1/common-codes` | 공통 코드 생성 (SUPER_ADMIN) |
| `GET` | `/api/v1/common-codes` | 공통 코드 전체 조회 |
| `GET` | `/api/v1/common-codes/{typeCode}` | 공통 코드 상세 조회 |
| `PUT` | `/api/v1/common-codes/{typeCode}` | 공통 코드 수정 |
| `POST` | `/api/v1/common-codes/{typeCode}/details` | 코드 상세 생성 |
| `GET` | `/api/v1/common-codes/{typeCode}/details` | 활성 코드 상세 목록 조회 |
| `PUT` | `/api/v1/common-codes/details/{detailId}` | 코드 상세 수정 |
| `DELETE` | `/api/v1/common-codes/details/{detailId}` | 코드 상세 삭제 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `common_codes` | `CommonCode` | 코드 타입 정의 |
| `common_code_details` | `CommonCodeDetail` | 타입별 코드 값/정렬/활성 여부 |
