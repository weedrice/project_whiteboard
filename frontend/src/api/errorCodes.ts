/**
 * 백엔드 `ErrorCode` enum의 코드 중 프론트가 분기에 사용하는 것만 모은 단일 출처.
 *
 * 값은 백엔드 `com.weedrice.whiteboard.global.exception.ErrorCode`와 1:1로 대응한다.
 * 새 코드로 분기해야 할 때는 파일 안에 상수를 추가하고 그 상수를 참조한다.
 * 화면·기능 모듈에 문자열 리터럴을 직접 두지 않는다.
 *
 * 테스트는 예외다. 테스트가 이 모듈을 참조하면 상수 값을 잘못 고쳐도 함께 따라가
 * 회귀를 놓치므로, 테스트에서는 기대하는 코드를 리터럴로 적는다.
 */
export const API_ERROR_CODES = {
    /** C006 NOT_FOUND — 공통 404 */
    NOT_FOUND: 'C006',
    /** C012 CONCURRENT_MODIFICATION — 낙관적 락 충돌 */
    CONCURRENT_MODIFICATION: 'C012',
    /** U009 BLOCKED_BY_USER — 상대가 나를 차단 */
    BLOCKED_BY_USER: 'U009',
    /** A009 USER_DELETED — 탈퇴 계정 */
    USER_DELETED: 'A009',
    /** P004 DRAFT_OUTDATED — 임시저장본이 서버 최신본보다 오래됨 */
    DRAFT_OUTDATED: 'P004',
    /** P005 DRAFT_PROTECTED — 예약 발행이 임시저장본을 보호 중 */
    DRAFT_PROTECTED: 'P005',
    /** P006 SCHEDULED_POST_LIMIT_EXCEEDED — 보호 상태 예약 게시글 상한 초과 */
    SCHEDULED_POST_LIMIT_EXCEEDED: 'P006',
    /** P007 DRAFT_NOT_FOUND — 초안 자체가 존재하지 않음 */
    DRAFT_NOT_FOUND: 'P007',
    /** EM006 EMOTICON_IMAGE_LIMIT_EXCEEDED — 이모티콘 이미지 개수 초과 */
    EMOTICON_IMAGE_LIMIT_EXCEEDED: 'EM006',
} as const
