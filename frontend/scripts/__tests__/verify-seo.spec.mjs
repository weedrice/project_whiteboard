import { describe, expect, it } from 'vitest'
import {
    assertPostUrlsPresent,
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
