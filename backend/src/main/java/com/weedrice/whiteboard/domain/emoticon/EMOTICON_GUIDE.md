# Emoticon 도메인 가이드

`emoticon` 도메인은 사용자 등록 커스텀 이모티콘을 관리합니다. 마스터/슬레이브 구조로 설계되었습니다.

## 1. 주요 기능 및 로직
- **마스터(emoticon_masters)**: 이모티콘 정보(이름, 썸네일, 태그, 등록자)
- **슬레이브(emoticon_images)**: 이모티콘에 포함된 개별 이미지들
- 태그는 PostgreSQL 배열(`TEXT[]`) 타입으로 저장

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-- | :--- |
| `GET` | `/api/emoticons` | 이모티콘 목록 |
| `GET` | `/api/emoticons/{id}` | 이모티콘 상세 (이미지 포함) |
| `GET` | `/api/emoticons/search?keyword=` | 키워드 검색 |
| `GET` | `/api/emoticons/search/tag?tag=` | 태그 검색 |
| `GET` | `/api/emoticons/my` | 내 이모티콘 |
| `POST` | `/api/emoticons` | 이모티콘 등록 |
| `PUT` | `/api/emoticons/{id}` | 이모티콘 수정 |
| `DELETE` | `/api/emoticons/{id}` | 이모티콘 삭제 |
| `POST` | `/api/emoticons/{id}/images` | 이미지 추가 |
| `DELETE` | `/api/emoticons/images/{imageId}` | 이미지 삭제 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :------- | :----- | :--- |
| `emoticon_masters` | `EmoticonMaster` | 이모티콘 마스터 |
| `emoticon_images` | `EmoticonImage` | 이모티콘 이미지 |
