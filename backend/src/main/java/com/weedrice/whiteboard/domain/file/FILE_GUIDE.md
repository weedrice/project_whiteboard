# File 도메인 가이드

`file` 도메인은 업로드 파일의 저장, 메타데이터 관리, 다운로드, 임시 파일 정리를 담당한다.

## 1. 주요 기능과 로직

- 파일 업로드: 10MB 이하 파일을 검증하고 `FileStorageService`가 저장소에 보관한다.
- 메타데이터 저장: 원본 파일명, 저장 경로, 크기, MIME type, 연결 대상 정보를 `files` 테이블에 기록한다.
- 단순 업로드 응답: `/upload` 엔드포인트는 업로드 후 바로 접근 가능한 URL과 fileId를 반환한다.
- 파일 다운로드: 저장소에서 파일을 스트리밍하고 Content-Type, 다운로드 파일명 헤더를 설정한다.
- 엔티티 연결: 게시글/사용자 등 연결 대상은 `related_id`, `related_type`으로 관리한다.
- 임시 파일 정리: 일정 시간 동안 연결 대상이 없는 pending 파일을 저장소와 DB에서 정리한다.
- 이미지 variant: 저장 전에 `PENDING_UPLOAD` intent를 만들고 저장 성공 후 `ACTIVE`로 전환한다. 원본 width/height, 기대 variant 수와 reconciliation version을 기록해 정상적인 0/1/2개 variant 구성을 재처리하지 않는다. 오래된 pending intent는 hourly cleanup이 저장소 객체와 DB 행을 함께 정리한다.
- Legacy 다운로드: `/files/{fileId}` 경로는 기존 공개 파일 URL 호환을 위해 유지한다.

## 2. API Endpoints

| Method | URI | 설명 |
| :----- | :-- | :-- |
| `POST` | `/api/v1/files` | 파일 업로드 후 메타데이터 반환 |
| `POST` | `/api/v1/files/upload` | 업로드 후 공개 URL과 fileId 반환 |
| `GET` | `/api/v1/files/{fileId}` | API v1 파일 다운로드 |
| `GET` | `/files/{fileId}` | legacy 파일 다운로드 |

## 3. 관련 DB 테이블

| 테이블명 | 엔티티 | 설명 |
| :-- | :-- | :-- |
| `files` | `File` | 업로드 파일 메타데이터와 연결 정보 |
| `file_variants` | `FileVariant` | 원본 이미지의 파생 크기와 업로드 상태 |

## 4. 주의 사항

- 업로드 제한, 저장 경로, 공개 URL 정책은 `FileStorageService`와 설정 값을 기준으로 한다.
- legacy 경로는 외부에 배포된 기존 URL 호환을 위한 것이므로 제거 전 사용처 확인이 필요하다.
