import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
    buildPostOgMeta,
    loadOgIcon,
    renderSiteOgImage,
    resolveSiteOgImage,
} from '../og-image.mjs'
import { extractMetaContent, validatePng, validatePrerenderHtml } from '../prerender-output-validation.mjs'
import { buildPreRenderedListingSnippet, buildPreRenderedSnippet, injectIntoTemplate } from '../prerender-html.mjs'

const tempDirs = []

afterEach(async () => {
    await Promise.all(tempDirs.splice(0).map((path) => rm(path, { recursive: true, force: true })))
})

describe('static community OG images', () => {
    it('loads the favicon PNG as a stable data URL', async () => {
        await expect(loadOgIcon()).resolves.toMatch(/^data:image\/png;base64,/)
    })

    it('renders the brand image to a 1200x630 PNG', async () => {
        const png = await renderSiteOgImage()

        expect(() => validatePng(png)).not.toThrow()
    }, 30_000)

    it('writes one content-independent brand image', async () => {
        const dir = await mkdtemp(join(tmpdir(), 'noviis-og-'))
        tempDirs.push(dir)
        const image = await resolveSiteOgImage({ siteUrl: 'https://noviis.kr', distDir: dir })
        const png = await readFile(image.outputPath)
        validatePng(png)

        expect(image.generated).toBe(true)
        expect(image.url).toContain('/img/og/community-brand-v2.png')
        const html = buildPostOgMeta(image)
        expect(html).not.toContain('/files/first.webp')
        expect(validatePrerenderHtml(html)).toEqual(new URL(image.url))
        expect(extractMetaContent(html, 'og:image:alt')).toContain('공유 이미지')
        expect(extractMetaContent(html, 'twitter:card')).toBe('summary_large_image')
    }, 30_000)

    it('injects generated OG and Twitter metadata into pre-rendered post HTML', () => {
        const image = {
            url: 'https://noviis.kr/img/og/post-17-hash.png',
            alt: '한글 <제목> & 공유 이미지',
            generated: true,
        }
        const renderData = buildPreRenderedSnippet('https://noviis.kr/board/free/post/17/', image)
        const html = injectIntoTemplate('<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', renderData)

        expect(extractMetaContent(html, 'og:image')).toBe(image.url)
        expect(extractMetaContent(html, 'og:image:alt')).toBe(image.alt)
        expect(extractMetaContent(html, 'twitter:image')).toBe(image.url)
        expect(extractMetaContent(html, 'twitter:card')).toBe('summary_large_image')
    })

    it('injects canonical metadata and crawlable links into listing HTML', () => {
        const renderData = buildPreRenderedListingSnippet({
            isAllBoards: false,
            canonicalUrl: 'https://noviis.kr/board/free/',
            urls: ['https://noviis.kr/board/free/post/1/']
        })
        const html = injectIntoTemplate('<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', renderData)

        expect(html).toContain('<link rel="canonical" href="https://noviis.kr/board/free/">')
        expect(html).toContain('data-prerendered="true"')
        expect(html).toContain('href="https://noviis.kr/board/free/post/1/"')
        expect(html).toContain('CollectionPage')
    })

    it('uses only generic page metadata and never claims a static article author or date', () => {
        const image = {
            url: 'https://noviis.kr/img/og/community-brand-v2.png',
            alt: 'NoviIs 공유 이미지',
            generated: true,
        }
        const renderData = buildPreRenderedSnippet('https://noviis.kr/board/free/post/17/', image)
        const html = injectIntoTemplate('<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', renderData)
        const structuredData = JSON.parse(html.match(/<script type="application\/ld\+json">(.*?)<\/script>/s)[1])

        expect(structuredData['@type']).toBe('WebPage')
        expect(structuredData).not.toHaveProperty('author')
        expect(structuredData).not.toHaveProperty('datePublished')
        expect(html).toContain('현재 공개 상태')
    })
})
