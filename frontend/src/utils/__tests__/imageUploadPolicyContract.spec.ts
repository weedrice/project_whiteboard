import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
    BOARD_ICON_UPLOAD_POLICY,
    POST_EDITOR_IMAGE_UPLOAD_POLICY,
    PROFILE_IMAGE_UPLOAD_POLICY,
} from '@/utils/imageUploadPolicy'
import {
    EMOTICON_IMAGE_UPLOAD_MAX_DIMENSION,
    EMOTICON_THUMBNAIL_UPLOAD_MAX_DIMENSION,
    MAX_EMOTICON_GIF_SIZE_BYTES,
} from '@/utils/emoticonImage'

/**
 * 업로드 제한은 양쪽에 각각 정의되어 있다. 프론트 값은 사용자에게 미리 알려 주기 위한 것이고
 * 실제 강제는 서버가 한다. 두 값이 어긋나면 UI가 허용한 파일이 서버에서 거부되므로
 * 백엔드 enum을 직접 읽어 상한이 모순되지 않는지 확인한다.
 */
const BACKEND_TARGET_SOURCE = resolve(
    process.cwd(),
    '../backend/src/main/java/com/weedrice/whiteboard/domain/file/service/FileUploadTarget.java',
)

interface BackendLimit {
    maxSizeBytes: number
    maxWidth: number
    maxHeight: number
}

function readBackendLimits(): Map<string, BackendLimit> {
    if (!existsSync(BACKEND_TARGET_SOURCE)) {
        throw new Error(
            `백엔드 업로드 대상 enum을 찾을 수 없다: ${BACKEND_TARGET_SOURCE}\n`
            + '이 테스트는 monorepo 체크아웃을 전제로 한다. 경로가 바뀌었다면 함께 고칠 것.',
        )
    }

    const source = readFileSync(BACKEND_TARGET_SOURCE, 'utf-8')
    const limits = new Map<string, BackendLimit>()

    // 예: BOARD_ICON(2L * 1024 * 1024, 0, 0),
    // 줄바꿈과 들여쓰기 변화, 후행 주석을 허용한다. 한 줄 형태만 보면 포매터가 줄을 접는 순간
    // 해당 상수가 조용히 빠지고 양쪽 집합이 함께 비어 테스트가 통과해 버린다.
    const pattern = /^[ \t]*([A-Z_]+)\(([^)]*?)\)\s*[,;]/gms
    for (const match of source.matchAll(pattern)) {
        const [size, width, height] = match[2].split(',').map((part) => evaluateJavaLiteral(part))
        limits.set(match[1], { maxSizeBytes: size, maxWidth: width, maxHeight: height })
    }

    if (limits.size === 0) {
        throw new Error('백엔드 업로드 대상을 하나도 읽지 못했다. enum 선언 형식이 바뀌었는지 확인할 것.')
    }
    return limits
}

/** `2L * 1024 * 1024`, `10_000_000`, `512` 형태를 다룬다. 해석할 수 없으면 실패시킨다. */
function evaluateJavaLiteral(expression: string): number {
    const value = expression
        .trim()
        .split('*')
        .map((factor) => {
            const literal = factor.trim().replace(/[_]/g, '').replace(/L$/i, '')
            const parsed = Number(literal)
            if (!Number.isFinite(parsed)) {
                throw new Error(`백엔드 enum의 숫자 리터럴을 해석할 수 없다: ${factor.trim()}`)
            }
            return parsed
        })
        .reduce((product, factor) => product * factor, 1)
    return value
}

/**
 * 백엔드가 지켜야 할 상한. 프론트 값과 비교만 하면 서버 쪽을 느슨하게 되돌려도 통과하므로
 * 기대값을 직접 못박는다.
 */
const EXPECTED_BACKEND_LIMITS: Record<string, BackendLimit> = {
    GENERIC: { maxSizeBytes: 10 * 1024 * 1024, maxWidth: 0, maxHeight: 0 },
    POST_CONTENT: { maxSizeBytes: 10 * 1024 * 1024, maxWidth: 0, maxHeight: 0 },
    BOARD_ICON: { maxSizeBytes: 2 * 1024 * 1024, maxWidth: 0, maxHeight: 0 },
    PROFILE_IMAGE: { maxSizeBytes: 2 * 1024 * 1024, maxWidth: 512, maxHeight: 512 },
    EMOTICON: { maxSizeBytes: 3 * 1024 * 1024, maxWidth: 2048, maxHeight: 2048 },
}

describe('업로드 정책 계약', () => {
    const limits = readBackendLimits()

    it('백엔드 상한이 기대값과 정확히 일치한다', () => {
        expect(Object.fromEntries(limits)).toEqual(EXPECTED_BACKEND_LIMITS)
    })

    it('백엔드가 프론트에서 쓰는 대상을 모두 정의한다', () => {
        expect([...limits.keys()]).toEqual(
            expect.arrayContaining(['GENERIC', 'POST_CONTENT', 'BOARD_ICON', 'PROFILE_IMAGE', 'EMOTICON']),
        )
    })

    it('원본을 그대로 올리는 대상은 프론트 상한이 서버 상한을 넘지 않는다', () => {
        // 게시글 이미지와 스페이스 아이콘은 사용자가 고른 파일을 그대로 올린다.
        // 프론트가 더 크게 허용하면 UI를 통과한 파일이 서버에서 거부된다.
        expect(POST_EDITOR_IMAGE_UPLOAD_POLICY.maxSizeBytes)
            .toBeLessThanOrEqual(limits.get('POST_CONTENT')!.maxSizeBytes)
        expect(BOARD_ICON_UPLOAD_POLICY.maxSizeBytes)
            .toBeLessThanOrEqual(limits.get('BOARD_ICON')!.maxSizeBytes)
    })

    it('리사이즈 후 올리는 대상은 축소 결과가 서버 해상도 상한 안에 든다', () => {
        // 프로필 이미지와 이모티콘은 소스 파일을 검사한 뒤 축소해서 올린다.
        // 따라서 소스 크기 제한(10MiB 등)은 업로드 크기의 상한이 아니며,
        // 서버 상한과 비교해야 하는 값은 축소 목표 해상도다.
        const profile = limits.get('PROFILE_IMAGE')!
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxWidth!).toBeLessThanOrEqual(profile.maxWidth)
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxHeight!).toBeLessThanOrEqual(profile.maxHeight)

        const emoticon = limits.get('EMOTICON')!
        expect(EMOTICON_IMAGE_UPLOAD_MAX_DIMENSION).toBeLessThanOrEqual(emoticon.maxWidth)
        expect(EMOTICON_THUMBNAIL_UPLOAD_MAX_DIMENSION).toBeLessThanOrEqual(emoticon.maxHeight)
    })

    it('프론트가 해상도 검사를 유지한다', () => {
        // ?? 0 으로 넘기면 프론트가 해상도 검사를 아예 없애도 통과하므로 존재부터 확인한다.
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxWidth).toBeGreaterThan(0)
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxHeight).toBeGreaterThan(0)
    })

    it('GIF는 축소하지 않으므로 프론트 상한이 서버 상한 안에 든다', () => {
        // GIF는 애니메이션을 잃지 않도록 축소 없이 원본을 올린다.
        expect(MAX_EMOTICON_GIF_SIZE_BYTES).toBeLessThanOrEqual(limits.get('EMOTICON')!.maxSizeBytes)
    })
})
