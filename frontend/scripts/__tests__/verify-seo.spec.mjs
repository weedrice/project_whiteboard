import { describe, expect, it, vi } from 'vitest'
import {
    assertAllowedSeoUrl,
    assertPostUrlsPresent,
    assertStableActiveReleaseSha,
    createImageMetadataFetcher,
    isPrivateOrReservedAddress,
    resolveAllowedRedirect,
    resolveActiveReleaseSha,
    selectRotatingPostUrls,
    sitemapSha256,
    validateReleaseManifest
} from '../verify-seo.mjs'

const commitSha = '0123456789abcdef0123456789abcdef01234567'
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset>
  <url><loc>https://noviis.kr/</loc></url>
  <url><loc>https://noviis.kr/board/general/post/1/</loc></url>
</urlset>
`
const allUrls = [
    'https://noviis.kr/',
    'https://noviis.kr/board/general/post/1/'
]
const postUrls = ['https://noviis.kr/board/general/post/1/']

function manifest(overrides = {}) {
    return JSON.stringify({
        commitSha,
        urlCount: allUrls.length,
        postUrlCount: postUrls.length,
        prerenderCount: postUrls.length,
        postUrlCapacity: 2000,
        sitemapProtocolMaxUrls: 50000,
        sitemapSha256: sitemapSha256(sitemap),
        ...overrides
    })
}

describe('runtime SEO release verification', () => {
    const allowedOrigins = new Set(['https://noviis.kr'])

    it.each([
        'http://noviis.kr/board/general/post/1',
        'https://user:password@noviis.kr/board/general/post/1',
        'https://noviis.kr/board/general/post/1#fragment',
        'https://evil.example/board/general/post/1',
        'https://169.254.169.254/latest/meta-data'
    ])('rejects an unsafe sitemap or metadata URL: %s', (url) => {
        expect(() => assertAllowedSeoUrl(url, allowedOrigins)).toThrow()
    })

    it('allows only same-origin redirect targets', () => {
        expect(resolveAllowedRedirect(
            new URL('https://noviis.kr/old'),
            '/new',
            allowedOrigins,
        ).toString()).toBe('https://noviis.kr/new')
        expect(() => resolveAllowedRedirect(
            new URL('https://noviis.kr/old'),
            'https://127.0.0.1/internal',
            allowedOrigins,
        )).toThrow()
    })

    it.each([
        '127.0.0.1',
        '10.0.0.1',
        '169.254.169.254',
        '::1',
        '[::1]',
        'fd00::1',
        'fe80::1',
        'ff02::1',
        '2001:db8::1',
        '::ffff:127.0.0.1',
        '::ffff:7f00:1',
    ])(
        'classifies private and reserved addresses: %s',
        (address) => expect(isPrivateOrReservedAddress(address)).toBe(true),
    )

    it('rejects a production sitemap without post URLs', () => {
        expect(() => assertPostUrlsPresent([], true)).toThrow('contains no post URLs')
        expect(() => assertPostUrlsPresent([], false)).not.toThrow()
    })

    it('accepts a manifest bound to the sitemap counts, digest, and release SHA', () => {
        expect(validateReleaseManifest({
            sitemapText: sitemap,
            allUrls,
            postUrls,
            manifestText: manifest(),
            expectedSha: commitSha
        })).toMatchObject({ commitSha, postUrlCount: 1 })
    })

    it('binds the active release endpoint to the expected or discovered SHA', () => {
        expect(resolveActiveReleaseSha(`${commitSha}\n`)).toBe(commitSha)
        expect(resolveActiveReleaseSha(commitSha, commitSha)).toBe(commitSha)
        expect(() => resolveActiveReleaseSha('f'.repeat(40), commitSha)).toThrow('active release mismatch')
        expect(() => resolveActiveReleaseSha('not-a-sha')).toThrow('not a full lowercase commit SHA')
        expect(assertStableActiveReleaseSha(commitSha, commitSha)).toBe(commitSha)
        expect(() => assertStableActiveReleaseSha(commitSha, 'f'.repeat(40))).toThrow('active release changed')
    })

    it.each([
        ['commit', { commitSha: 'f'.repeat(40) }],
        ['URL count', { urlCount: 99 }],
        ['post URL count', { postUrlCount: 99 }],
        ['prerender count', { prerenderCount: 99 }],
        ['post capacity', { postUrlCapacity: 0 }],
        ['sitemap capacity', { sitemapProtocolMaxUrls: 49_999 }],
        ['sitemap digest', { sitemapSha256: '0'.repeat(64) }]
    ])('rejects a stale or inconsistent %s', (_name, overrides) => {
        expect(() => validateReleaseManifest({
            sitemapText: sitemap,
            allUrls,
            postUrls,
            manifestText: manifest(overrides),
            expectedSha: commitSha
        })).toThrow()
    })

    it('selects a bounded, reproducible sample that rotates with the release SHA', () => {
        const urls = Array.from({ length: 100 }, (_, index) => `https://noviis.kr/board/general/post/${index + 1}/`)
        const first = selectRotatingPostUrls(urls, 10, `${'a'.repeat(40)}:run-1`)
        const repeated = selectRotatingPostUrls(urls, 10, `${'a'.repeat(40)}:run-1`)
        const next = selectRotatingPostUrls(urls, 10, `${'a'.repeat(40)}:run-2`)

        expect(first).toHaveLength(10)
        expect(first).toEqual(repeated)
        expect(first).toContain(urls[0])
        expect(first).toContain(urls.at(-1))
        expect(next).not.toEqual(first)
    })
})


describe('SEO image request reuse', () => {
    const imageUrl = 'https://noviis.kr/img/og/site.png'
    const success = { ok: true, status: 200, contentType: 'image/png' }

    it('fetches a shared image once while checking ten pages', async () => {
        const fetchMetadata = vi.fn().mockResolvedValue(success)
        const fetchImage = createImageMetadataFetcher(fetchMetadata)

        for (let page = 0; page < 10; page += 1) {
            await expect(fetchImage(imageUrl, 'googlebot')).resolves.toEqual(success)
        }
        expect(fetchMetadata).toHaveBeenCalledTimes(1)
        expect(fetchMetadata).toHaveBeenCalledWith(imageUrl, 'googlebot')
    })

    it('checks each distinct image URL and user agent separately', async () => {
        const fetchMetadata = vi.fn().mockResolvedValue(success)
        const fetchImage = createImageMetadataFetcher(fetchMetadata)
        const otherImage = 'https://noviis.kr/img/og/other.png'

        await fetchImage(imageUrl, 'googlebot')
        await fetchImage(imageUrl, 'otherbot')
        await fetchImage(otherImage, 'googlebot')
        await fetchImage(imageUrl, 'googlebot')

        expect(fetchMetadata.mock.calls).toEqual([
            [imageUrl, 'googlebot'],
            [imageUrl, 'otherbot'],
            [otherImage, 'googlebot'],
        ])
    })

    it.each([
        { ok: false, status: 503, contentType: 'image/png' },
        { ok: true, status: 200, contentType: 'text/html' },
        { ok: true, status: 200, contentType: '' },
    ])('does not reuse unsuccessful image verification: %j', async (failure) => {
        const fetchMetadata = vi.fn().mockResolvedValueOnce(failure).mockResolvedValue(success)
        const fetchImage = createImageMetadataFetcher(fetchMetadata)

        await expect(fetchImage(imageUrl, 'googlebot')).resolves.toEqual(failure)
        await expect(fetchImage(imageUrl, 'googlebot')).resolves.toEqual(success)
        await expect(fetchImage(imageUrl, 'googlebot')).resolves.toEqual(success)
        expect(fetchMetadata).toHaveBeenCalledTimes(2)
    })

    it('propagates request errors and allows a later request', async () => {
        const fetchMetadata = vi.fn()
            .mockRejectedValueOnce(new Error('blocked redirect'))
            .mockResolvedValue(success)
        const fetchImage = createImageMetadataFetcher(fetchMetadata)

        await expect(fetchImage(imageUrl, 'googlebot')).rejects.toThrow('blocked redirect')
        await expect(fetchImage(imageUrl, 'googlebot')).resolves.toEqual(success)
        expect(fetchMetadata).toHaveBeenCalledTimes(2)
    })

    it('does not reuse successful results across verification runs', async () => {
        const fetchMetadata = vi.fn().mockResolvedValue(success)
        const firstRun = createImageMetadataFetcher(fetchMetadata)
        const nextRun = createImageMetadataFetcher(fetchMetadata)

        await firstRun(imageUrl, 'googlebot')
        await nextRun(imageUrl, 'googlebot')
        expect(fetchMetadata).toHaveBeenCalledTimes(2)
    })
})
