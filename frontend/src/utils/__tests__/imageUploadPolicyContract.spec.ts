import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import {
    BOARD_ICON_UPLOAD_POLICY,
    POST_EDITOR_IMAGE_UPLOAD_POLICY,
    PROFILE_IMAGE_UPLOAD_POLICY,
} from '@/utils/imageUploadPolicy'

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
    const pattern = /^\s{4}([A-Z_]+)\(([^)]+)\)[,;]$/gm
    for (const match of source.matchAll(pattern)) {
        const [size, width, height] = match[2].split(',').map((part) => evaluateJavaLiteral(part))
        limits.set(match[1], { maxSizeBytes: size, maxWidth: width, maxHeight: height })
    }

    if (limits.size === 0) {
        throw new Error('백엔드 업로드 대상을 하나도 읽지 못했다. enum 선언 형식이 바뀌었는지 확인할 것.')
    }
    return limits
}

/** `2L * 1024 * 1024` 형태의 곱셈 리터럴만 다룬다. */
function evaluateJavaLiteral(expression: string): number {
    return expression
        .trim()
        .split('*')
        .map((factor) => Number(factor.trim().replace(/L$/i, '')))
        .reduce((product, factor) => product * factor, 1)
}

describe('업로드 정책 계약', () => {
    const limits = readBackendLimits()

    it('백엔드가 프론트에서 쓰는 대상을 모두 정의한다', () => {
        expect([...limits.keys()]).toEqual(
            expect.arrayContaining(['GENERIC', 'POST_CONTENT', 'BOARD_ICON', 'PROFILE_IMAGE', 'EMOTICON']),
        )
    })

    it('프론트 크기 상한이 서버 상한을 넘지 않는다', () => {
        // 프론트가 더 크게 허용하면 UI를 통과한 파일이 서버에서 거부된다.
        expect(POST_EDITOR_IMAGE_UPLOAD_POLICY.maxSizeBytes)
            .toBeLessThanOrEqual(limits.get('POST_CONTENT')!.maxSizeBytes)
        expect(BOARD_ICON_UPLOAD_POLICY.maxSizeBytes)
            .toBeLessThanOrEqual(limits.get('BOARD_ICON')!.maxSizeBytes)
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxSizeBytes)
            .toBeLessThanOrEqual(limits.get('PROFILE_IMAGE')!.maxSizeBytes)
    })

    it('프론트 해상도 상한이 서버 상한을 넘지 않는다', () => {
        const profile = limits.get('PROFILE_IMAGE')!

        expect(profile.maxWidth).toBeGreaterThan(0)
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxWidth ?? 0).toBeLessThanOrEqual(profile.maxWidth)
        expect(PROFILE_IMAGE_UPLOAD_POLICY.maxHeight ?? 0).toBeLessThanOrEqual(profile.maxHeight)
    })
})
