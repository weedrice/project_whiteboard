import { describe, expect, it, vi } from 'vitest'
import { STATIC_ASSET_CACHE_NAME, cacheableStaticAssetResponse, purgeInvalidStaticAssetCache } from '@/utils/staticAssetCache'

describe('static asset cache', () => {
  it.each(['text/html', 'text/html; charset=utf-8', ' TEXT/HTML ; charset=UTF-8', 'application/xhtml+xml'])(
    'rejects an HTML fallback even when its status is successful: %s', (contentType) => {
      const response = new Response('<html>app shell</html>', { headers: { 'Content-Type': contentType } })
      expect(cacheableStaticAssetResponse(response)).toBeNull()
    },
  )

  it.each(['application/javascript', 'text/javascript', 'text/css', 'image/svg+xml', 'font/woff2', 'application/octet-stream'])(
    'preserves a successful static asset: %s', (contentType) => {
      const response = new Response('asset', { headers: { 'Content-Type': contentType } })
      expect(cacheableStaticAssetResponse(response)).toBe(response)
    },
  )

  it.each([404, 500])('does not cache an HTTP %s response', (status) => {
    expect(cacheableStaticAssetResponse(new Response('missing', { status }))).toBeNull()
  })

  it('treats an absent cache entry as a miss', () => {
    expect(cacheableStaticAssetResponse(undefined)).toBeNull()
  })

  it('removes old HTML/error entries while preserving healthy offline assets', async () => {
    const html = new Request('https://noviis.kr/js/old-AbCdEfGh.js')
    const missing = new Request('https://noviis.kr/img/missing-AbCdEfGh.png')
    const script = new Request('https://noviis.kr/js/valid-AbCdEfGh.js')
    const entries = new Map([
      [html, new Response('<html>fallback</html>', { headers: { 'Content-Type': 'text/html' } })],
      [missing, new Response('missing', { status: 404 })],
      [script, new Response('valid', { headers: { 'Content-Type': 'application/javascript' } })],
    ])
    const remove = vi.fn(async (request: Request) => entries.delete(request))
    const cache = {
      keys: vi.fn(async () => [...entries.keys()]),
      match: vi.fn(async (request: Request) => entries.get(request)),
      delete: remove,
    } as unknown as Cache
    const open = vi.fn(async () => cache)
    const storage = { has: vi.fn(async () => true), open } as unknown as CacheStorage

    await purgeInvalidStaticAssetCache(storage)

    expect(open).toHaveBeenCalledExactlyOnceWith(STATIC_ASSET_CACHE_NAME)
    expect(remove).toHaveBeenCalledTimes(2)
    expect(entries.has(html)).toBe(false)
    expect(entries.has(missing)).toBe(false)
    expect(entries.get(script)).toBeDefined()
  })

  it('does not create a runtime cache when no legacy cache exists', async () => {
    const open = vi.fn()
    const storage = { has: vi.fn(async () => false), open } as unknown as CacheStorage

    await purgeInvalidStaticAssetCache(storage)

    expect(open).not.toHaveBeenCalled()
  })
})
