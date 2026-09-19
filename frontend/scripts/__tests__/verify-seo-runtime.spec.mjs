import { execFile } from 'node:child_process'
import { copyFile, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { promisify } from 'node:util'
import { afterEach, expect, it } from 'vitest'

const run = promisify(execFile)
const fixtures = []

afterEach(async () => {
    // Only directories returned by this fixture's mkdtemp calls are removed.
    await Promise.all(fixtures.splice(0).map((fixture) => rm(fixture, { recursive: true, force: true })))
})

it('loads the SEO monitor with repository scripts and Node only', async () => {
    const fixture = await mkdtemp(join(tmpdir(), 'noviis-seo-runtime-'))
    fixtures.push(fixture)
    await Promise.all(['verify-seo.mjs', 'html-meta.mjs'].map((name) => (
        copyFile(resolve('scripts', name), join(fixture, name))
    )))

    const { stdout } = await run(process.execPath, [
        '--input-type=module', '-e',
        'const verifier = await import(process.argv[1]); console.log(verifier.assertPostUrlsPresent([], false))',
        pathToFileURL(join(fixture, 'verify-seo.mjs')).href,
    ], { cwd: fixture })

    expect(stdout.trim()).toBe('undefined')
})
