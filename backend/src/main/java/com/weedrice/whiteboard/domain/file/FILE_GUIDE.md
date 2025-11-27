# File 도메인 가이드

`file` 도메인은 이미지 및 첨부파일의 업로드, 다운로드, 관리를 담당합니다.

## 1. 주요 기능 및 로직

`FileService`와 `FileStorageService`를 통해 다음과 같은 핵심 비즈니스 로직을 처리합니다.

- **파일 업로드 (`uploadFile`):**
  - 클라이언트로부터 `MultipartFile`을 받아 유효성을 검사합니다. (파일 크기, 형식 등)
  - `FileStorageService`를 통해 실제 파일을 서버의 지정된 경로(로컬 또는 S3)에 저장합니다.
  - 저장된 파일의 메타데이터(원본 이름, 저장된 이름, 크기, 타입 등)를 `files` 테이블에 기록합니다.
  - **중요:** 최초 업로드 시, 파일은 '임시(temporary)' 상태로 간주됩니다. (`related_id`가 `null`)

- **파일과 엔티티 연결 (`associateFileWithEntity`):**
  - 게시글, 댓글 등이 최종적으로 저장될 때 호출됩니다.
  - 임시 상태였던 파일의 `related_id`와 `related_type`을 업데이트하여 '영구(permanent)' 상태로 전환합니다.

- **임시 파일 정리 (`cleanUpTemporaryFiles`):**
  - `@Scheduled` 어노테이션을 통해 주기적으로 실행되는 배치 작업입니다.
  - 생성된 지 24시간이 지났지만, 여전히 '임시' 상태인 파일들을 찾아냅니다.
  - `FileStorageService`를 통해 실제 파일을 삭제하고, `files` 테이블의 해당 레코드도 함께 삭제합니다.

- **파일 다운로드 (`getFile`):**
  - `fileId`를 통해 파일 정보를 조회하고, `FileStorageService`를 통해 실제 파일을 찾아 클라이언트에게 전송합니다.

## 2. API Endpoints

`FileController`에서 다음 API를 제공합니다.

| Method | URI                     | 설명           |
| :----- | :---------------------- | :------------- |
| `POST`   | `/api/v1/files`         | 파일 업로드    |
| `GET`    | `/api/v1/files/{fileId}`| 파일 다운로드  |

## 3. 관련 DB 테이블

`file` 도메인은 주로 다음 테이블과 상호작용합니다.

| 테이블명 | 엔티티 | 설명             |
| :------- | :----- | :--------------- |
| `files`  | `File` | 업로드된 파일 정보 |

이 문서는 `file` 도메인의 기능을 이해하고 개발하는 데 도움을 주기 위해 작성되었습니다.
