import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import { Resvg } from '@resvg/resvg-js'
import {
    buildPostOgMeta,
    loadOgIcon,
    renderSiteOgImage,
    resolveSiteOgImage,
} from '../og-image.mjs'
import { extractMetaContent } from '../html-meta.mjs'
import { validatePng, validatePrerenderHtml } from '../prerender-output-validation.mjs'
import { buildPreRenderedListingSnippet, buildPreRenderedSnippet, injectIntoTemplate } from '../prerender-html.mjs'
import { OG_TEXT_SIZE } from '../static-typography-tokens.mjs'

const tempDirs = []

afterEach(async () => {
    await Promise.all(tempDirs.splice(0).map((path) => rm(path, { recursive: true, force: true })))
})

describe('static community OG images', () => {
    it('keeps generated-image typography in one named scale', () => {
        expect(OG_TEXT_SIZE).toEqual({
            brand: 30,
            boardName: 22,
            title: 56,
            titleCompact: 46,
            tagline: 21,
            siteUrl: 22,
        })
    })

    it('loads the favicon PNG as a stable data URL', async () => {
        await expect(loadOgIcon()).resolves.toMatch(/^data:image\/png;base64,/)
    })

    it('renders the brand image to a 1200x630 PNG', async () => {
        const png = await renderSiteOgImage()

        expect(() => validatePng(png)).not.toThrow()
        // Decode the real production PNG rather than only checking its dimensions:
        // layout engine changes can move the footer below a valid 1200x630 canvas.
        const pixels = new Resvg(`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630"><image width="1200" height="630" href="data:image/png;base64,${png.toString('base64')}"/></svg>`).render().pixels
        const darkPixels = (left, top, right, bottom) => {
            let count = 0
            for (let y = top; y < bottom; y++) {
                for (let x = left; x < right; x++) {
                    const offset = (y * 1200 + x) * 4
                    if (pixels[offset + 3] > 0 && pixels[offset] < 160 && pixels[offset + 1] < 160 && pixels[offset + 2] < 160) count++
                }
            }
            return count
        }
        expect(darkPixels(70, 525, 450, 580), 'Korean footer must remain inside the image').toBeGreaterThan(100)
        expect(darkPixels(1000, 525, 1130, 580), 'site domain must remain inside the image').toBeGreaterThan(50)
        expect(darkPixels(0, 590, 1200, 630), 'footer must keep its bottom margin').toBe(0)
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
        expect(html).toContain('class="text-3xl font-bold"')
        expect(html).not.toContain('font-size:')
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
