import { execFile } from 'node:child_process'
import { copyFile, mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { promisify } from 'node:util'
import { afterEach, expect, it } from 'vitest'
import { validatePng } from '../prerender-output-validation.mjs'

const run = promisify(execFile)
const tempDirs = []
const servers = []

afterEach(async () => {
    await Promise.all(servers.splice(0).map((server) => new Promise((done) => server.close(done))))
    // Only test-owned directories returned by mkdtemp are removed.
    await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })))
})

it('keeps public post and board content out of every static release file before visibility changes', async () => {
    const dir = await mkdtemp(join(tmpdir(), 'noviis-prerender-privacy-'))
    tempDirs.push(dir)
    const board = { boardUrl: 'free', boardName: 'PRIVATE_BOARD_NAME', description: 'PRIVATE_BOARD_DESCRIPTION' }
    const post = {
        postId: 17, title: 'PRIVATE_POST_TITLE', contents: '<p>PRIVATE_POST_BODY</p>',
        author: { displayName: 'PRIVATE_AUTHOR' }, board,
        createdAt: '2026-07-14T12:34:56Z', isSecret: false, isBlinded: false,
    }
    const server = createServer((request, response) => {
        response.setHeader('Content-Type', 'application/json')
        response.end(JSON.stringify({ success: true, data: request.url.startsWith('/posts/') ? post : [board] }))
    })
    servers.push(server)
    await new Promise((done) => server.listen(0, '127.0.0.1', done))
    await mkdir(join(dir, 'dist'), { recursive: true })
    await mkdir(join(dir, 'public'), { recursive: true })
    await mkdir(join(dir, 'scripts/assets/fonts'), { recursive: true })
    await copyFile(resolve('public/favicon.ico'), join(dir, 'public/favicon.ico'))
    await copyFile(resolve('scripts/assets/fonts/NotoSansKR-Regular.otf'), join(dir, 'scripts/assets/fonts/NotoSansKR-Regular.otf'))
    await writeFile(join(dir, 'dist/index.html'), '<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', 'utf8')
    const paths = ['/boards/', '/board/free/', '/board/free/post/17/']
    await writeFile(join(dir, 'public/sitemap.xml'), `<urlset>${paths.map((path) => `<url><loc>https://noviis.kr${path}</loc></url>`).join('')}</urlset>`, 'utf8')
    const options = {
        cwd: dir,
        env: {
            ...process.env, SEO_STRICT: 'true', SEO_RELEASE_SHA: 'a'.repeat(40),
            PRERENDER_SITE_URL: 'https://noviis.kr',
            PRERENDER_API_BASE_URL: `http://127.0.0.1:${server.address().port}`,
            PRERENDER_FETCH_RETRIES: '0',
        },
        timeout: 30_000,
    }
    await run(process.execPath, [resolve('scripts/prerender-posts.mjs')], options)
    await run(process.execPath, [resolve('scripts/write-seo-release-manifest.mjs')], options)
    await run(process.execPath, [resolve('scripts/verify-prerender-output.mjs')], options)

    // These files are identical after deletion/privatization: no rebuild is needed to hide content.
    post.isSecret = true
    const entries = await readdir(join(dir, 'dist'), { recursive: true, withFileTypes: true })
    for (const entry of entries.filter((entry) => entry.isFile())) {
        const contents = await readFile(join(entry.parentPath, entry.name))
        expect(contents.toString('utf8'), entry.name).not.toContain('PRIVATE_')
        expect(contents.toString('utf8'), entry.name).not.toContain(post.createdAt)
        if (entry.name.endsWith('.png')) validatePng(contents)
    }
    const postHtml = await readFile(join(dir, 'dist/board/free/post/17/index.html'), 'utf8')
    expect(postHtml).toContain('community-brand-v2.png')
    expect(postHtml).toContain('data-prerendered="true"')
    const manifest = JSON.parse(await readFile(join(dir, 'dist/.noviis-seo-release.json'), 'utf8'))
    expect(manifest).toMatchObject({ postUrlCount: 1, prerenderCount: 1, commitSha: 'a'.repeat(40) })
}, 45_000)
