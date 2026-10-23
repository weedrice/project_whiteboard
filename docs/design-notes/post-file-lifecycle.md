# 게시글·초안·예약발행과 파일의 생명주기

## 문서 상태와 범위

이 문서는 현재 코드에 적용된 파일 연결·예약 보호와 게시글·초안 생명주기를 설명한다. 두 도메인이 공유하는 구현 근거이며 운영 배포 완료를 의미하지 않는다. 대표 회귀 테스트와 PostgreSQL 검증 한계는 마지막 절에서 구분한다.

## 책임과 참조 관계

파일의 `relatedId`/`relatedType`은 현재 연결 대상을 나타낸다. 예약발행의 `ScheduledPostFile`은 별도 예약 참조다. 예약을 만들었다고 파일의 현재 연결 대상이나 업로더가 바뀌는 것은 아니다.

| 구현 | 책임 |
| --- | --- |
| [FileAssociationService](../../backend/src/main/java/com/weedrice/whiteboard/domain/file/service/FileAssociationService.java) | 공통 연결·이동·동기화, 예약 검사, 삭제 대기 표시 |
| [FileRepository](../../backend/src/main/java/com/weedrice/whiteboard/domain/file/repository/FileRepository.java) | 정렬된 파일 잠금, 예약 조회, 조건부 연결 UPDATE, 임시파일 정리의 참조 제외 |
| [PostDraftService](../../backend/src/main/java/com/weedrice/whiteboard/domain/post/service/PostDraftService.java) | 초안 저장·복구와 유효한 파일 참조 선별 |
| [PostDraftPublicationService](../../backend/src/main/java/com/weedrice/whiteboard/domain/post/service/PostDraftPublicationService.java) | 발행 원본 초안 잠금·검증, 승격 후 남은 초안파일 정리와 초안 삭제 |
| [PostCreateSideEffectService](../../backend/src/main/java/com/weedrice/whiteboard/domain/post/service/PostCreateSideEffectService.java) | 게시글 생성 흐름의 파일 연결과 기존 발행 부수 효과 |
| [ScheduledPostFileService](../../backend/src/main/java/com/weedrice/whiteboard/domain/post/scheduled/service/ScheduledPostFileService.java) | 예약 참조 교체·제거와 파일 잠금 |
| [ScheduledPostPublishWorker](../../backend/src/main/java/com/weedrice/whiteboard/domain/post/scheduled/service/ScheduledPostPublishWorker.java) | 발행 lease에 따른 생성·완료·실패 처리 |

## 일반 업로드와 초안

연결되지 않은 활성 파일은 소유자가 허용된 대상에 연결할 수 있다. 공통 연결에서는 소유자와 활성 저장 상태뿐 아니라 기존 연결과 예약 참조를 확인한다. 같은 사용자의 파일이라는 이유만으로 다른 게시글이나 프로필에 다시 연결할 수는 없다.

초안 저장은 유효한 파일만 남긴 뒤 `syncDraftFiles`를 호출한다. 다른 예약발행이 선점한 파일처럼 사용할 수 없는 참조는 선별 단계에서 제외되며, 관련 본문 파일 참조를 정리하고 `staleReferencesReset`으로 알린다. 기존 게시글을 수정하는 초안에서는 기존 게시글 첨부파일을 초안 소유로 옮기지 않는다.

선별은 최종 쓰기 권한 판정이 아니다. 선별 이후 새 예약이 생길 수 있으므로 연결 단계에서 잠금과 예약 검사를 다시 수행한다. 이 재검사에서 충돌하면 요청은 실패할 수 있다.

초안·게시글 파일 동기화는 요청된 파일과 현재 대상에 연결된 파일을 함께 잠근다. 요청 파일의 유효성을 확인하고 연결한 뒤, 게시글 동기화는 목록에서 빠진 기존 파일에 삭제 대기를 표시한다. 초안 동기화는 실행 취소로 다시 사용할 수 있도록 빠진 파일도 해당 초안에 활성 상태로 보관한다. 파일 표시 순서는 요청 순서를 유지하며 잠금 순서와 구분한다.

### 편집 중 이미지 실행 취소

적용: 작성기는 본문에서 제거·교체한 미연결 업로드를 편집 중에 폐기하지 않는다. 작성기 종료·대상 전환 시 마지막으로 저장에 성공한 로컬 복구본에 필요한 파일을 넘기고 나머지를 폐기 요청한다. 최신 복구본 저장이 실패했다면 이전 복구본이 참조하는 파일도 보존한다. 오프라인·브라우저 종료로 요청이 실패하면 기존 임시파일 만료 정리가 처리한다.

자동 저장에서 빠진 초안 파일은 동일 초안 소유로 보존하므로 실행 취소 후 다시 저장할 수 있다. 요청·응답의 fileIds와 20개 첨부 제한은 현재 본문 파일에만 적용하며 이전 이미지까지 발행하지 않는다. 발행 시 현재 첨부를 승격한 다음 남은 초안 파일 전체를 정리하고, 초안 삭제·개수 제한·90일 만료도 현재 fileIds와 관계없이 연결 파일 전체를 정리한다. 따라서 사용하지 않는 이미지 버전은 초안 생명주기가 끝날 때까지 저장 공간을 차지할 수 있다. 파일 접근·소유자·활성 상태·예약 검증은 기존 정책을 유지한다.

### 본문 이모티콘 참조와 소유 첨부파일

이모티콘 이미지는 본문에서 참조하더라도 초안·게시글 소유 파일로 이동하지 않는다. [postForm.ts](../../frontend/src/utils/postForm.ts)는 에디터가 설정한 `:emoticon:` 표식으로 업로드 첨부 목록과 구분한다. 서버가 거부한 참조를 본문에서 제거하는 용도의 전체 파일 추출은 이모티콘도 포함한다. 따라서 서버 정규화 응답을 반영할 때 유효한 이모티콘은 보존하고 거부된 참조는 제거할 수 있다.

서버는 표식에 의존하지 않고 `FileRepository.findUsableEmoticonImageFileIds`에서 활성 저장 상태, 예약 참조 부재, 제작자·구매자 권한을 확인한다. `EMOTICON_IMAGE` 팩 연결이 있거나, 아직 연결되지 않은 레거시 파일이 실제 이모티콘 이미지 URL에 등록되어 있어야 한다. 다른 게시글·초안·썸네일 연결은 이 예외로 재분류하지 않는다. 팩 편집에서 선택 목록에서 제거했어도 기존 이미지의 활성 파일과 팩 연결이 남아 있으면 보존한다.

초안은 이 참조를 본문 허용 목록에 포함하고 소유 `fileIds`에서는 제외한다. 게시글 생성·수정·예약 저장도 구형 클라이언트가 보낸 이모티콘 ID를 일반 첨부 목록에서 제외하여 원본 소유권과 팩 연결을 유지한다. 파일 다운로드 접근 정책은 그대로 따르며, 이 분류는 임의 HTML 전체의 사용 권한을 검증하는 기능을 추가한 것은 아니다.

## 예약 생성·변경과 초안 보호

[ScheduledPostService](../../backend/src/main/java/com/weedrice/whiteboard/domain/post/scheduled/service/ScheduledPostService.java)는 원본 초안이 있으면 소유자와 게시판을 확인하고 잠근다. 기존 게시글을 수정하는 초안은 새 글의 예약발행 원본으로 사용할 수 없다.

`replaceReferences`는 호출자의 트랜잭션 안에서 요청 파일을 ID 오름차순으로 잠근다. 각 파일은 같은 업로더의 활성 파일이어야 하고, 아직 연결되지 않았거나 해당 원본 초안에 연결되어 있어야 한다. 다른 예약의 참조가 있으면 거부한다. 검증 후 기존 참조를 교체하고 표시 순서를 저장하며 flush한다.

원본 초안은 `SCHEDULED`, `PUBLISHING`, `FAILED` 상태에서 보호된다. 저장·수동 삭제·복구·오래된 초안 정리가 이 보호를 우회해서는 안 된다. 예약파일이 다른 초안의 선별 단계에서 제외되는 것과, 예약 원본 초안 자체의 변경을 거부하는 것은 별도 계약이다.

## 발행과 취소

예약발행 worker는 `PUBLISHING` 상태와 `processingStartedAt`이 일치하는 작업을 처리한다. 내부 `publishingScheduledPostId`가 게시글 생성에서 파일 연결까지 전달된다. 이 값은 클라이언트 요청 DTO가 제공하는 권한이 아니다.

- 파일에 예약 참조가 있으면 일치하는 예약발행만 연결할 수 있다. 다른 예약 ID나 일반 연결 경로는 거부된다.
- 원본 초안이 있으면 소유자·게시판·원본 게시글 ID를 검증하고, 현재 발행 중인 예약이 그 초안을 사용하는지 확인한다.
- 게시글에 필요한 파일을 승격한 다음 초안에 남은 파일을 삭제 대기로 표시하고 초안을 삭제한다.
- 발행 트랜잭션은 생성 후 예약 참조를 제거하고, 같은 lease의 완료 상태를 조건부로 갱신한다. 갱신 건수가 1이 아니면 실패한다.
- 발행 실패 상태 기록은 별도 트랜잭션이며 예약 참조를 제거하지 않는다. 실패 상태도 원본 초안을 보호한다.

같은 파일을 같은 대상으로 다시 연결하는 경우에도 예약 검사가 먼저다. 예약 참조가 남아 있을 때는 일치하는 내부 발행 문맥이 필요하며, 참조가 제거된 후에는 기존 같은 대상 재호출 규칙을 따른다.

취소는 소유한 `SCHEDULED` 또는 `FAILED` 작업에 대해 조건부로 수행되며 원본 초안 연결과 파일 예약 참조를 해제한다. 취소만으로 파일을 물리 삭제하지 않는다. 이후 파일은 남아 있는 실제 연결 대상에 따라 일반 연결·초안 복구·정리 규칙을 따른다.

## 잠금과 저장 상태

예약 생성과 일반 연결은 같은 파일 행 잠금을 사용한다. 공통 연결 경로는 다음 순서를 유지한다.

1. 요청 파일을 잠근다. 동기화 작업은 기존 대상 파일까지 합쳐 ID 오름차순으로 잠근다.
2. 이미 영속성 컨텍스트에 있던 파일도 `refresh`하여 현재 저장 상태와 연결 상태를 확인한다. 잠금 조회만으로 JPA 1차 캐시가 최신 상태가 된다고 가정하지 않는다.
3. 활성 상태와 소유자를 검사한 다음, 잠금 획득 뒤의 별도 쿼리로 다른 예약 참조를 확인한다.
4. 기존 연결/원본 초안 이동 조건과 예약 제외 조건이 있는 UPDATE를 실행한다. 실패 시 갱신된 상태를 재확인한다.

별도 예약 조회는 잠금 대기 전에 시작한 하나의 UPDATE 문장에만 예약 부재 검사를 맡기지 않기 위한 경계다. PostgreSQL의 문장 스냅샷과 잠금 대기를 고려한 구조이므로, 변경 시 flush 시점과 refresh가 덮어쓸 수 있는 미반영 변경도 함께 검토한다.

## 정리와 파일 접근

임시파일 폐기·만료 정리의 저장소 조건은 예약 참조가 있는 파일을 제외한다. 초안 정리는 보호된 예약 원본을 제외한다. 이 조건을 일반 연결 검사와 별개로 유지해야 예약 대기 중인 파일이 정리 대상이 되지 않는다.

[FileDeletionWorker](../../backend/src/main/java/com/weedrice/whiteboard/domain/file/service/FileDeletionWorker.java)는 삭제 대기 메타데이터를 처리한다. 데이터베이스에서 삭제 작업을 claim하고 변형 이미지와 원본 저장 객체를 삭제한 뒤, claim 당시 상태와 일치하는지 확인하여 메타데이터를 정리한다. 프로세스 내부 중복 방지 집합은 여러 노드 사이의 동시성 제어를 대체하지 않는다. 기존 이모티콘 참조 보호와 실패 재시도도 이 흐름의 일부다.

파일 연결 가능 여부와 다운로드 가능 여부는 다르다. [FileAccessService](../../backend/src/main/java/com/weedrice/whiteboard/domain/file/service/FileAccessService.java)는 연결된 게시글·문의 등의 접근 정책과 캐시 범위를 판단하며, 미연결 파일의 이모티콘 참조 예외도 처리한다. 예약 보호를 추가했다고 파일을 공개하거나 기존 접근 검사를 생략하지 않는다.

## API 영향과 회귀 검증

일반 게시글·프로필 등 직접 연결에서 예약 충돌은 기존 `FILE_ALREADY_ASSOCIATED` (`F007`) 계약을 사용한다. 다른 초안의 선별 단계에서는 위의 `staleReferencesReset` 계약을 사용한다. 기존 URL, DTO 형태, 응답 envelope를 유지하며, 내부 예약발행 문맥을 HTTP 입력으로 추가하지 않는다. 관련 의미가 바뀌면 [API명세서](../../backend/API명세서.md), [기능명세서](../../backend/기능명세서.md), [DATABASE.md](../../backend/DATABASE.md)와 해당 프런트 소비자를 함께 확인한다.

| 대표 테스트 | 검증 범위 |
| --- | --- |
| [FileAssociationReservationTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/file/service/FileAssociationReservationTest.java) | 실제 저장소의 예약 충돌, 일치/불일치 발행 문맥, 취소 후 연결, 같은 대상 재호출, 초안 선별, 여러 파일 중 충돌, 오류 우선순위, 잠금 정렬, 1차 캐시 refresh |
| [FileRepositoryTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/file/repository/FileRepositoryTest.java) | 연결 UPDATE와 파일 상태 조건 |
| [EmoticonContentFileReferenceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/file/repository/EmoticonContentFileReferenceTest.java) | 실제 H2 저장소의 제작자·구매자 권한, 레거시/보존 이미지, 예약·삭제 대기·일반 첨부 제외 |
| [PostDraftFileReferenceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/file/service/PostDraftFileReferenceTest.java) | 원본 초안에서 게시글로의 파일 이동과 참조 규칙 |
| [PostServiceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/post/service/PostServiceTest.java) | 일반 생성·수정과 발행 부수 효과 |
| [ScheduledPostServiceTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/post/scheduled/service/ScheduledPostServiceTest.java) | 예약 생성·변경·취소와 원본 초안 검증 |
| [ScheduledPostPublishWorkerTest](../../backend/src/test/java/com/weedrice/whiteboard/domain/post/scheduled/service/ScheduledPostPublishWorkerTest.java) | 발행 문맥 전달, lease 완료·실패와 예약 참조 처리 |

`FileAssociationReservationTest`는 H2에서 실제 저장소 조건과 서비스 연계를 검증한다. 실제 PostgreSQL의 동시 트랜잭션·잠금 대기·MVCC와 운영 스케줄러 실행은 위 회귀 테스트의 검증 범위에 포함되지 않는다. 이 표는 검증 경로를 안내하며 문서 수정 시 테스트를 다시 실행했다는 뜻은 아니다. 변경별 실행 범위·결과·DB 종류는 [backend 검증 규칙](../../backend/AGENTS.md)에 따라 보고한다.
