import { execFile } from 'node:child_process'
import { copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { promisify } from 'node:util'
import { pathToFileURL } from 'node:url'
import { afterEach, expect, it } from 'vitest'

const run = promisify(execFile)
const tempDirs = []
const servers = []
const indexHtml = '<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>'
const sitemap = (locations) => `<urlset>${locations.map((loc) => `<url><loc>${loc}</loc></url>`).join('')}</urlset>`

async function extractPaths(xml) {
    const scriptUrl = pathToFileURL(resolve('scripts/prerender-posts.mjs')).href
    const { stdout } = await run(process.execPath, [
        '--input-type=module',
        '--eval',
        `const { extractPathsFromSitemap } = await import(${JSON.stringify(scriptUrl)}); console.log(JSON.stringify(extractPathsFromSitemap(process.argv[1])))`,
        xml,
    ])
    return JSON.parse(stdout)
}

afterEach(async () => {
    await Promise.all(servers.splice(0).map((server) => new Promise((done) => server.close(done))))
    // Only test-owned directories returned by mkdtemp are removed.
    await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })))
})

it('keeps sitemap order, post deduplication, listing duplicates, and decoded paths', async () => {
    const xml = sitemap([
        'https://noviis.kr/board/free/post/20',
        'not a URL',
        'https://noviis.kr/boards/',
        'https://noviis.kr/board/free/',
        'https://noviis.kr/board/free/post/20/',
        'https://noviis.kr/board/%ED%95%9C%EA%B8%80/post/7/',
        'https://noviis.kr/board/free/',
        'https://noviis.kr/unrelated/',
    ])
    expect(await extractPaths(xml)).toEqual({
        postPaths: [
            { boardUrl: 'free', postId: '20', path: '/board/free/post/20/' },
            { boardUrl: '한글', postId: '7', path: '/board/한글/post/7/' },
        ],
        listingPaths: [
            { path: '/boards/', boardUrl: null },
            { path: '/board/free/', boardUrl: 'free' },
            { path: '/board/free/', boardUrl: 'free' },
        ],
    })
})

it('still rejects malformed percent encoding even in a non-target URL', async () => {
    await expect(extractPaths(sitemap([
        'https://noviis.kr/board/free/post/20/',
        'https://noviis.kr/unrelated/%E0%A4%A/',
    ]))).rejects.toThrow('URIError')
})

it.each(['success', 'individual failure', 'total failure'])(
    'preserves listing output and missing parent fallbacks on %s',
    async (scenario) => {
        const dir = await mkdtemp(join(tmpdir(), 'noviis-prerender-fallback-'))
        tempDirs.push(dir)
        const requests = []
        const server = createServer((request, response) => {
            requests.push(request.url)
            response.setHeader('Content-Type', 'application/json')
            if (request.url === '/boards' && scenario === 'total failure') {
                response.statusCode = 503
                response.end('{}')
                return
            }
            const data = request.url.startsWith('/posts/')
                ? {}
                : scenario === 'individual failure' ? [] : [{ boardUrl: 'free' }]
            response.end(JSON.stringify({ success: true, data }))
        })
        servers.push(server)
        await new Promise((done) => server.listen(0, '127.0.0.1', done))
        await mkdir(join(dir, 'dist/board/free/post'), { recursive: true })
        await mkdir(join(dir, 'public'), { recursive: true })
        await mkdir(join(dir, 'scripts/assets/fonts'), { recursive: true })
        await copyFile(resolve('public/favicon.ico'), join(dir, 'public/favicon.ico'))
        await copyFile(resolve('scripts/assets/fonts/NotoSansKR-Regular.otf'), join(dir, 'scripts/assets/fonts/NotoSansKR-Regular.otf'))
        await writeFile(join(dir, 'dist/index.html'), indexHtml, 'utf8')
        await writeFile(join(dir, 'dist/board/free/post/index.html'), 'existing parent', 'utf8')
        await writeFile(join(dir, 'public/sitemap.xml'), sitemap([
            'https://noviis.kr/boards/',
            'https://noviis.kr/board/free/',
            'https://noviis.kr/board/free/post/17/',
        ]), 'utf8')

        const result = await run(process.execPath, [resolve('scripts/prerender-posts.mjs')], {
            cwd: dir,
            env: {
                ...process.env,
                SEO_STRICT: 'true',
                PRERENDER_SITE_URL: 'https://noviis.kr',
                PRERENDER_API_BASE_URL: `http://127.0.0.1:${server.address().port}`,
                PRERENDER_FETCH_RETRIES: '0',
            },
            timeout: 30_000,
        }).catch((error) => error)

        expect(result.code ?? 0).toBe(scenario === 'success' ? 0 : 1)
        expect(requests).toEqual(['/posts/17?incrementView=false', '/boards'])
        expect(await readFile(join(dir, 'dist/board/index.html'), 'utf8')).toBe(indexHtml)
        expect(await readFile(join(dir, 'dist/board/free/post/index.html'), 'utf8')).toBe('existing parent')
        expect(await readFile(join(dir, 'dist/board/free/post/17/index.html'), 'utf8')).toContain('data-prerendered="true"')
        const listingHtml = await readFile(join(dir, 'dist/board/free/index.html'), 'utf8')
        if (scenario === 'success') {
            expect(listingHtml).toContain('data-prerendered="true"')
            expect(listingHtml).toContain('https://noviis.kr/board/free/post/17/')
            expect(result.stdout).toContain('wrote 1 SPA fallback index files')
        } else {
            expect(listingHtml).toBe(indexHtml)
            expect(result.stderr).toContain(scenario === 'total failure'
                ? 'HTTP 503'
                : 'board metadata not found: free')
        }
        if (scenario !== 'total failure') {
            expect(await readFile(join(dir, 'dist/boards/index.html'), 'utf8')).toContain('data-prerendered="true"')
        }
    },
    45_000,
)
