import { describe, expect, it } from 'vitest'
import {
    assertAllowedSeoUrl,
    assertPostUrlsPresent,
    isPrivateOrReservedAddress,
    resolveAllowedRedirect,
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

    it.each([
        ['commit', { commitSha: 'f'.repeat(40) }],
        ['URL count', { urlCount: 99 }],
        ['post URL count', { postUrlCount: 99 }],
        ['prerender count', { prerenderCount: 99 }],
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
})
