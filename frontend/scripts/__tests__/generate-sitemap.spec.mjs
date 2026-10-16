import { execFile } from 'node:child_process'
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { createServer } from 'node:http'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { promisify } from 'node:util'
import { afterEach, expect, it } from 'vitest'

const run = promisify(execFile)
const tempDirs = []
const servers = []
const originalSitemap = '<existing-sitemap />'

afterEach(async () => {
    await Promise.all(servers.splice(0).map((server) => new Promise((done) => {
        server.closeAllConnections()
        server.close(done)
    })))
    // Only test-owned directories returned by mkdtemp are removed.
    await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })))
})

function sendJson(response, data) {
    response.setHeader('Content-Type', 'application/json')
    response.end(JSON.stringify({ success: true, data }))
}

function sendSuccess(request, response) {
    if (request.url === '/boards') {
        sendJson(response, [{ boardUrl: 'free', isActive: true }])
        return
    }
    const page = Number(new URL(request.url, 'http://localhost').searchParams.get('page'))
    sendJson(response, {
        content: [{ postId: page + 1, createdAt: '2026-10-01T09:00:00+09:00' }],
        totalPages: 2,
        totalElements: 2,
    })
}

async function generate(handler, overrides = {}) {
    const dir = await mkdtemp(join(tmpdir(), 'noviis-sitemap-retry-'))
    tempDirs.push(dir)
    await mkdir(join(dir, 'public'))
    await writeFile(join(dir, 'public/sitemap.xml'), originalSitemap, 'utf8')
    const requests = []
    const server = createServer((request, response) => {
        requests.push(request.url)
        handler(request, response, requests)
    })
    servers.push(server)
    await new Promise((done) => server.listen(0, '127.0.0.1', done))
    const result = await run(process.execPath, [resolve('scripts/generate-sitemap.mjs')], {
        cwd: dir,
        env: {
            ...process.env,
            SEO_STRICT: 'true',
            SEO_POST_URL_CAPACITY: '10',
            SITEMAP_SITE_URL: 'https://noviis.kr',
            SITEMAP_API_BASE_URL: `http://127.0.0.1:${server.address().port}`,
            SITEMAP_FETCH_RETRIES: '2',
            SITEMAP_FETCH_RETRY_DELAY_MS: '1',
            SITEMAP_REQUEST_TIMEOUT_MS: '2000',
            ...overrides,
        },
        timeout: 15_000,
    }).catch((error) => error)
    return { result, requests, xml: await readFile(join(dir, 'public/sitemap.xml'), 'utf8') }
}

it('retries transient board and paginated post failures without omitting or duplicating URLs', async () => {
    const attempts = new Map()
    const { result, requests, xml } = await generate((request, response) => {
        const attempt = (attempts.get(request.url) ?? 0) + 1
        attempts.set(request.url, attempt)
        if (attempt === 1) {
            response.statusCode = request.url === '/boards' ? 502 : 503
            response.end('temporarily unavailable')
            return
        }
        sendSuccess(request, response)
    })
    expect(result.code ?? 0).toBe(0)
    expect(requests).toHaveLength(6)
    expect([...attempts.values()]).toEqual([2, 2, 2])
    expect(xml.match(/<loc>/g)).toHaveLength(5)
    expect(xml).toContain('https://noviis.kr/board/free/post/1/')
    expect(xml).toContain('https://noviis.kr/board/free/post/2/')
    expect(result.stderr).toContain('retrying /boards after HttpError: HTTP 502')
    expect(result.stderr).toContain('page=1')
})

it.each([0, 2])('fails closed after %i configured retries are exhausted', async (retries) => {
    const { result, requests, xml } = await generate((_request, response) => {
        response.statusCode = 502
        response.end('unavailable')
    }, { SITEMAP_FETCH_RETRIES: String(retries) })
    expect(result.code).toBe(1)
    expect(requests).toHaveLength(retries + 1)
    expect(result.stderr).toContain('HTTP 502 while fetching /boards')
    expect(xml).toBe(originalSitemap)
})

it.each(['401', '404', 'invalid JSON', 'success=false'])(
    'does not retry a permanent %s failure',
    async (scenario) => {
        const { result, requests, xml } = await generate((_request, response) => {
            response.statusCode = /^\d+$/.test(scenario) ? Number(scenario) : 200
            response.end(scenario === 'success=false' ? '{"success":false}' : 'not JSON')
        })
        expect(result.code).toBe(1)
        expect(requests).toEqual(['/boards'])
        expect(result.stderr).not.toContain('retrying')
        expect(xml).toBe(originalSitemap)
    },
)

it.each(['network error', 'timeout'])('recovers from a transient %s', async (scenario) => {
    const { result, requests, xml } = await generate((request, response, received) => {
        if (received.length === 1) {
            if (scenario === 'network error') request.socket.destroy()
            // Leave the first response pending to exercise the actual AbortSignal timeout.
            return
        }
        sendSuccess(request, response)
    }, { SITEMAP_REQUEST_TIMEOUT_MS: '1000' })
    expect(result.code ?? 0).toBe(0)
    expect(requests.filter((path) => path === '/boards')).toHaveLength(2)
    expect(result.stderr).toContain('retrying /boards')
    expect(xml).toContain('https://noviis.kr/board/free/post/2/')
})

it('still rejects an empty strict sitemap after a successful response', async () => {
    const { result, requests, xml } = await generate((_request, response) => sendJson(response, []))
    expect(result.code).toBe(1)
    expect(requests).toEqual(['/boards'])
    expect(result.stderr).toContain('strict production sitemap contains no post URLs')
    expect(xml).toBe(originalSitemap)
})
