import { execFile } from 'node:child_process'
import { mkdir, mkdtemp, readdir, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { promisify } from 'node:util'
import { afterEach, describe, expect, it } from 'vitest'

const run = promisify(execFile)
const scriptPath = resolve('scripts/check-generated-api.mjs')
const fixtures = []

afterEach(async () => {
    // Only directories returned by this fixture's mkdtemp calls are removed.
    await Promise.all(fixtures.splice(0).map((fixture) => rm(fixture, { recursive: true, force: true })))
})

async function checkFixture({ committed, generated = 'export type Value = string\n', generatorFails = false }) {
    const fixture = await mkdtemp(join(tmpdir(), 'noviis-api-check-test-'))
    fixtures.push(fixture)
    const cwd = join(fixture, 'frontend')
    const tempRoot = join(fixture, 'temp')
    await mkdir(join(cwd, 'src/types/generated'), { recursive: true })
    await mkdir(tempRoot)
    if (committed !== undefined) {
        await writeFile(join(cwd, 'src/types/generated/api.ts'), committed, 'utf8')
    }

    // Keep the real CLI, filesystem and process exit behavior; replace only the external generator.
    const preload = join(fixture, 'generator-fixture.mjs')
    await writeFile(preload, `
import childProcess from 'node:child_process'
import os from 'node:os'
import { writeFileSync } from 'node:fs'
import { syncBuiltinESMExports } from 'node:module'
os.tmpdir = () => ${JSON.stringify(tempRoot)}
childProcess.execFileSync = (command, args) => {
    if (command !== 'npx' || args[0] !== '--no-install' || args[1] !== 'openapi-typescript' || args[3] !== '-o') {
        throw new Error('Unexpected generator command')
    }
    writeFileSync(args[4], ${JSON.stringify(generated)}, 'utf8')
    if (${generatorFails}) throw new Error('fixture generator failed')
}
syncBuiltinESMExports()
`, 'utf8')

    let result
    try {
        result = { ...await run(process.execPath, ['--import', pathToFileURL(preload).href, scriptPath], { cwd }), code: 0 }
    } catch (error) {
        result = error
    }
    return { ...result, temporaryEntries: await readdir(tempRoot) }
}

describe('generated API CLI cleanup', () => {
    it('accepts CRLF/LF differences and removes generated temporary files', async () => {
        const result = await checkFixture({ committed: 'export type Value = string\r\n' })
        expect(result.code).toBe(0)
        expect(result.stdout).toContain('생성 타입이 스펙 스냅샷과 일치한다.')
        expect(result.temporaryEntries).toEqual([])
    })

    it('fails a real mismatch after cleaning up temporary generation output', async () => {
        const result = await checkFixture({ committed: 'export type Value = number\n' })
        expect(result.code).toBe(1)
        expect(result.stderr).toContain('커밋된 생성 타입이 스펙 스냅샷과 다르다.')
        expect(result.stdout).not.toContain('생성 타입이 스펙 스냅샷과 일치한다.')
        expect(result.temporaryEntries).toEqual([])
    })

    it('fails a missing committed file without creating a temporary directory', async () => {
        const result = await checkFixture({})
        expect(result.code).toBe(1)
        expect(result.stderr).toContain('생성 타입이 없다:')
        expect(result.temporaryEntries).toEqual([])
    })

    it('cleans up partial generation output when the generator fails', async () => {
        const result = await checkFixture({ committed: 'export type Value = string\n', generatorFails: true })
        expect(result.code).toBe(1)
        expect(result.stderr).toContain('fixture generator failed')
        expect(result.stdout).not.toContain('생성 타입이 스펙 스냅샷과 일치한다.')
        expect(result.temporaryEntries).toEqual([])
    })
})
