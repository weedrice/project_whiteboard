# Emoticon 도메인 가이드

`emoticon` 도메인은 사용자 등록 커스텀 이모티콘 팩, 이미지, 구매 여부를 관리합니다.

## 1. 주요 기능 및 로직

- 이모티콘 마스터: 이름, 썸네일, 태그, 등록자, 활성 상태, 구매 수를 저장합니다.
- 이모티콘 이미지: 팩에 포함된 개별 이미지와 정렬 순서를 저장합니다.
- 검색: 통합 검색의 검색 유형으로 이름, 등록자, 태그, 전체 검색을 제공하며 인기 이모티콘을 별도로 조회합니다.
- 소유/구매: 내가 만든 이모티콘, 구매한 이모티콘, 특정 이모티콘 구매 여부를 조회합니다.
- 등록/수정/삭제: 소유자 권한을 검증하고 이미지 추가/삭제 및 공개 여부 변경을 처리합니다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :----------------------------------------------- | :---------------- |
| `GET` | `/api/v1/emoticons/popular` | 인기 이모티콘 |
| `GET` | `/api/v1/emoticons/search/all` | 이모티콘 목록 및 통합 검색 |
| `GET` | `/api/v1/emoticons/my` | 내가 만든 이모티콘 |
| `GET` | `/api/v1/emoticons/{emoticonId}` | 이모티콘 상세 |
| `POST` | `/api/v1/emoticons` | 이모티콘 등록 |
| `PUT` | `/api/v1/emoticons/{emoticonId}` | 이모티콘 수정 |
| `PATCH` | `/api/v1/emoticons/{emoticonId}/visibility` | 공개 여부 변경 |
| `DELETE` | `/api/v1/emoticons/{emoticonId}` | 이모티콘 삭제 |
| `POST` | `/api/v1/emoticons/{emoticonId}/purchase` | 이모티콘 구매 |
| `GET` | `/api/v1/emoticons/purchased` | 구매한 이모티콘 |
| `GET` | `/api/v1/emoticons/{emoticonId}/purchased` | 구매 여부 조회 |

삭제 API는 HTTP `200 OK`와 공통 `ApiResponse<Void>` 성공 envelope를 반환합니다. 응답 본문이 없는 `204 No Content` 계약은 사용하지 않습니다.

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `emoticon_masters` | `EmoticonMaster` | 이모티콘 마스터 |
| `emoticon_images` | `EmoticonImage` | 이모티콘 이미지 |
| `emoticon_purchases` | `EmoticonPurchase` | 사용자별 이모티콘 구매 |
