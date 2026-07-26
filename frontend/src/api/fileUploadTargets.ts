/**
 * 업로드 대상. 백엔드 `FileUploadTarget` enum과 1:1로 대응하며 대상별 크기·해상도 상한을 고른다.
 * 생략하면 서버는 `GENERIC`으로 처리해 기존 동작을 유지한다.
 *
 * API 클라이언트(`@/api/file`)와 분리해 둔다. 그쪽은 여러 테스트가 모킹하므로,
 * 상수를 함께 두면 모든 모킹에 이 값을 다시 넣어야 한다.
 */
export const FILE_UPLOAD_TARGETS = {
    POST_CONTENT: 'POST_CONTENT',
    BOARD_ICON: 'BOARD_ICON',
    PROFILE_IMAGE: 'PROFILE_IMAGE',
    EMOTICON: 'EMOTICON',
} as const

export type FileUploadTarget = (typeof FILE_UPLOAD_TARGETS)[keyof typeof FILE_UPLOAD_TARGETS]
