import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * `boolean isXxx` 필드의 wire 이름을 백엔드 소스에서 직접 읽어 확인한다.
 *
 * A8에서 51개 필드가 `xxx`와 `isXxx` 두 키를 내보내던 것을 `isXxx` 하나로 줄였다. 프론트가
 * 두 이름을 섞어 읽어도 중복 덕분에 동작하던 상태였으므로, 정리 이후에는 **어느 쪽 이름을
 * 읽는지가 곧 동작 여부**가 된다. 목 데이터로 도는 단위 테스트는 이 어긋남을 잡지 못한다.
 *
 * 백엔드 DTO 소스를 읽어 실제 wire 이름을 계산하고, 프론트 타입이 같은 이름을 쓰는지 본다.
 */

// vitest는 frontend/를 루트로 실행하므로 저장소 루트 기준으로 한 단계만 올라간다.
const BACKEND_DTO_ROOT = resolve(process.cwd(), '../backend/src/main/java/com/weedrice/whiteboard/domain')

/**
 * 정리 대상이었던 DTO와, 그 wire 이름을 읽는 프론트 타입 선언 파일.
 * agent·ad 도메인은 소유 주체가 달라 계약 대상이 아니다.
 */
const CHECKED_DTOS: { dto: string; fields: string[]; readBy: string[] }[] = [
    { dto: 'post/dto/PostResponse.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret', 'isBlinded', 'isAdmin'], readBy: ['src/types/board.ts'] },
    // liked/scrapped는 내부 타입이 접두사 없는 이름을 쓰고 정규화 계층이 wire 이름을 흡수한다.
    { dto: 'post/dto/PostResponse.java', fields: ['isLiked', 'isScrapped'], readBy: ['src/api/postContract.ts'] },
    { dto: 'post/dto/PostSummary.java', fields: ['isSpoiler', 'isSecret', 'isBlinded'], readBy: ['src/types/board.ts'] },
    { dto: 'post/dto/DraftResponse.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret'], readBy: ['src/types/board.ts'] },
    { dto: 'feed/dto/FeedPostSummary.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret'], readBy: ['src/types/board.ts'] },
    { dto: 'comment/dto/CommentResponse.java', fields: ['isDeleted', 'isBlockedAuthor', 'isBlinded'], readBy: ['src/types/comment.ts'] },
    { dto: 'board/dto/BoardListResponse.java', fields: ['isSubscribed', 'isActive', 'isPublic'], readBy: ['src/types/board.ts'] },
    { dto: 'board/dto/AdminBoardResponse.java', fields: ['isActive', 'isPublic'], readBy: ['src/types/board.ts'] },
    { dto: 'board/dto/BoardDetailResponse.java', fields: ['isAdmin'], readBy: ['src/types/board.ts'] },
    { dto: 'board/dto/SubscriptionBoardResponse.java', fields: ['isSubscribed', 'isActive', 'isPublic'], readBy: ['src/types/board.ts'] },
    { dto: 'board/dto/CategoryResponse.java', fields: ['isDefault'], readBy: ['src/types/board.ts'] },
    { dto: 'auth/dto/VerifyCodeResponse.java', fields: ['isReregister'], readBy: ['src/api/auth.ts'] },
    { dto: 'user/dto/NotificationSettingResponse.java', fields: ['isEnabled'], readBy: ['src/api/userAccountApi.ts'] },
    { dto: 'post/scheduled/dto/ScheduledPostDetailResponse.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret'], readBy: ['src/api/post.ts'] },
]

/**
 * **요청** DTO. 프론트가 보내는 키 이름이 서버가 읽는 이름과 같아야 한다.
 *
 * 응답만 검사하면 안 된다는 것이 실제 사고로 드러났다. A8 1차 작업에서 직렬화는 정상인데
 * 역직렬화가 깨져 `isSecret`이 무시됐고, 응답 방향만 보던 검사는 전부 통과했다.
 * 서버 쪽 역직렬화 자체는 `BooleanWireRoundTripContractTest`가 고정한다.
 */
const CHECKED_REQUEST_DTOS: { dto: string; fields: string[]; sentBy: string[] }[] = [
    { dto: 'post/dto/PostCreateRequest.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret'], sentBy: ['src/api/post.ts'] },
    { dto: 'post/dto/PostUpdateRequest.java', fields: ['isNsfw', 'isSpoiler', 'isSecret'], sentBy: ['src/api/post.ts'] },
    { dto: 'post/dto/PostDraftRequest.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret'], sentBy: ['src/api/post.ts'] },
    { dto: 'post/scheduled/dto/ScheduledPostRequest.java', fields: ['isNotice', 'isNsfw', 'isSpoiler', 'isSecret'], sentBy: ['src/api/post.ts'] },
]

function readBackendSource(relativePath: string): string {
    const path = resolve(BACKEND_DTO_ROOT, relativePath)
    if (!existsSync(path)) {
        throw new Error(
            `백엔드 DTO를 찾을 수 없다: ${path}\n`
            + '이 테스트는 monorepo 체크아웃을 전제로 한다. 경로가 바뀌었다면 함께 고칠 것.',
        )
    }
    return readFileSync(path, 'utf-8')
}

/**
 * 필드가 어떤 wire 이름으로 나가는지 소스에서 판정한다.
 *
 * - 필드와 getter **양쪽**에 `@JsonProperty("isXxx")` -> `isXxx` 하나, 읽기·쓰기 모두 됨
 * - getter에만 -> `isXxx` 하나지만 **읽기 전용**(요청 값이 무시된다)
 * - 필드에만 -> `xxx`와 `isXxx` 둘 다 (A8이 없앤 패턴)
 * - 어노테이션 없음 -> `xxx` 하나
 */
function wireNamesFor(source: string, field: string): string[] {
    const bare = field.slice(2, 3).toLowerCase() + field.slice(3)
    const declaration = new RegExp(
        `(?:^[ \\t]*(?:@[^\\n]*\\n)*?)?[ \\t]*private (?:final )?(?:boolean|Boolean) ${field}\\b`,
        'm',
    )
    if (!declaration.test(source)) {
        throw new Error(`${field} 선언을 찾지 못했다. 필드가 사라졌거나 이름이 바뀌었다.`)
    }

    const onGetter = new RegExp(`@Getter\\(onMethod_ = @JsonProperty\\("${field}"\\)\\)\\s*\\n\\s*private`).test(source)
        || new RegExp(`@JsonProperty\\("${field}"\\)\\s*\\n\\s*public (?:boolean|Boolean) ${field}\\(`).test(source)
    if (onGetter) return [field]

    const onField = new RegExp(`@JsonProperty\\("${field}"\\)\\s*\\n\\s*private`).test(source)
    if (onField) return [bare, field]

    return [bare]
}

describe('boolean wire 이름이 백엔드와 프론트에서 일치한다', () => {
    it.each(CHECKED_DTOS)('$dto', ({ dto, fields, readBy }) => {
        const source = readBackendSource(dto)
        const frontendSources = readBy.map((path) => readFileSync(resolve(process.cwd(), path), 'utf-8')).join('\n')

        for (const field of fields) {
            const names = wireNamesFor(source, field)

            // A8 정리 이후 두 키를 내보내는 필드는 없어야 한다.
            expect(names, `${dto}#${field}가 키를 두 개 내보낸다. A8 정리가 되돌아갔다`).toHaveLength(1)
            expect(names[0], `${dto}#${field}의 wire 이름이 접두사 없는 쪽으로 바뀌었다`).toBe(field)

            // 프론트 타입이 그 이름을 실제로 선언하고 있어야 한다.
            expect(
                new RegExp(`\\b${field}\\??:`).test(frontendSources),
                `${dto}#${field}는 wire에서 '${field}'로 나가는데 ${readBy.join(', ')}에 그 이름이 없다`,
            ).toBe(true)
        }
    })

    it.each(CHECKED_REQUEST_DTOS)('$dto (요청)', ({ dto, fields, sentBy }) => {
        const source = readBackendSource(dto)
        const frontendSources = sentBy.map((path) => readFileSync(resolve(process.cwd(), path), 'utf-8')).join('\n')

        for (const field of fields) {
            const names = wireNamesFor(source, field)

            expect(names, `${dto}#${field}가 키를 두 개 받는다`).toHaveLength(1)
            expect(names[0], `${dto}#${field}의 wire 이름이 접두사 없는 쪽으로 바뀌었다`).toBe(field)

            // 프론트 페이로드 타입이 그 이름으로 보내야 한다. 이름이 어긋나면 서버는
            // 그 필드를 못 읽고 기본값(false)으로 처리한다.
            expect(
                new RegExp(`\\b${field}\\??:`).test(frontendSources),
                `${dto}#${field}는 서버가 '${field}'로 읽는데 ${sentBy.join(', ')}에 그 이름이 없다`,
            ).toBe(true)
        }
    })

    it('접두사가 떨어진 legacy 필드는 이번 정리 대상이 아니므로 그대로다', () => {
        // PostSummary의 이 필드들은 어노테이션이 없어 예전부터 bare 이름으로 나간다.
        // 함께 바꿔 버리면 프론트 정규화 계층이 조용히 false로 떨어진다.
        const source = readBackendSource('post/dto/PostSummary.java')

        for (const field of ['isNotice', 'isNsfw', 'isLiked', 'isScrapped', 'isSubscribed']) {
            const bare = field.slice(2, 3).toLowerCase() + field.slice(3)
            expect(wireNamesFor(source, field), `${field}의 wire 이름이 바뀌었다`).toEqual([bare])
        }
    })
})
