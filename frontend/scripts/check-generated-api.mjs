#!/usr/bin/env node
/**
 * 커밋된 생성 타입이 스펙 스냅샷과 일치하는지 확인한다.
 *
 * 생성 타입을 커밋해 두면 개발자가 매번 생성기를 돌리지 않아도 되지만, 스펙만 갱신하고
 * 타입을 잊는 순간 둘이 조용히 어긋난다. CI에서 이 스크립트를 돌려 그 상태를 막는다.
 *
 * 백엔드 쪽 짝은 `OpenApiSpecSnapshotTest`다. 그쪽은 "스펙 스냅샷이 실제 컨트롤러와
 * 일치하는가", 이쪽은 "생성 타입이 스펙 스냅샷과 일치하는가"를 본다. 둘이 모두 돌아야
 * 컨트롤러 → 스펙 → 타입 사슬이 이어진다.
 */

import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const SPEC = resolve(process.cwd(), '../docs/api/openapi-frontend.json')
const COMMITTED = resolve(process.cwd(), 'src/types/generated/api.ts')

function fail(message) {
    console.error(`[api:check] ${message}`)
    process.exit(1)
}

let committed
try {
    committed = readFileSync(COMMITTED, 'utf-8')
} catch {
    fail(`생성 타입이 없다: ${COMMITTED}\n  npm run api:generate 로 만들 것.`)
}

const workDir = mkdtempSync(join(tmpdir(), 'api-check-'))
const regenerated = join(workDir, 'api.ts')

try {
    execFileSync('npx', ['openapi-typescript', SPEC, '-o', regenerated], { stdio: 'pipe' })

    const fresh = readFileSync(regenerated, 'utf-8')
    if (fresh !== committed) {
        fail(
            '커밋된 생성 타입이 스펙 스냅샷과 다르다.\n'
            + '  스펙을 바꿨다면 npm run api:generate 결과도 함께 커밋할 것.\n'
            + '  스펙을 바꾼 적이 없다면 openapi-typescript 버전이 올라갔을 수 있다.',
        )
    }
    console.log('[api:check] 생성 타입이 스펙 스냅샷과 일치한다.')
} finally {
    rmSync(workDir, { recursive: true, force: true })
}
