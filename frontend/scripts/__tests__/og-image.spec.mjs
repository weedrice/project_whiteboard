import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
    buildPostOgMeta,
    createPostOgImageFilename,
    renderPostOgImage,
    resolvePostOgImage,
    selectPostOgImage,
} from '../og-image.mjs'
import { extractMetaContent, validatePng, validatePrerenderHtml } from '../verify-prerender-output.mjs'
import { buildPreRenderedSnippet, injectIntoTemplate } from '../prerender-html.mjs'

const tempDirs = []

afterEach(async () => {
    await Promise.all(tempDirs.splice(0).map((path) => rm(path, { recursive: true, force: true })))
})

const post = (overrides = {}) => ({
    postId: 17,
    title: '한글 제목과 특수문자 <NoviIs> & 친구들',
    board: { boardName: '창작 스페이스' },
    imageUrls: [],
    isNsfw: false,
    isSpoiler: false,
    isSecret: false,
    isBlinded: false,
    ...overrides,
})

describe('post OG images', () => {
    it('changes the cache filename when the title or space changes', () => {
        const base = createPostOgImageFilename(post())

        expect(createPostOgImageFilename(post({ title: '수정된 제목' }))).not.toBe(base)
        expect(createPostOgImageFilename(post({ board: { boardName: '다른 스페이스' } }))).not.toBe(base)
        expect(base).toMatch(/^post-17-[a-f0-9]{12}\.png$/)
    })

    it('uses the first public attachment but protects sensitive posts', () => {
        const withImages = post({ imageUrls: ['/files/first.webp', '/files/second.webp'] })

        expect(selectPostOgImage(withImages, 'https://noviis.kr')).toBe('https://noviis.kr/files/first.webp')
        expect(selectPostOgImage({ ...withImages, isNsfw: true }, 'https://noviis.kr')).toBeNull()
        expect(selectPostOgImage({ ...withImages, isSpoiler: true }, 'https://noviis.kr')).toBeNull()
        expect(selectPostOgImage({ ...withImages, isSecret: true }, 'https://noviis.kr')).toBeNull()
        expect(selectPostOgImage(post({ imageUrls: ['data:image/png;base64,AAAA'] }), 'https://noviis.kr')).toBeNull()
    })

    it('renders Korean, long text, and special characters to a 1200x630 PNG', async () => {
        const png = await renderPostOgImage(post({
            title: '아주 긴 한글 제목과 <태그처럼 보이는 문자> & 따옴표 “테스트”를 포함해도 안전하게 세 줄 안에서 표현되는 공유 이미지 제목입니다',
        }))

        expect(() => validatePng(png)).not.toThrow()
    }, 30_000)

    it('writes brand images and emits complete absolute share metadata', async () => {
        const dir = await mkdtemp(join(tmpdir(), 'noviis-og-'))
        tempDirs.push(dir)
        const image = await resolvePostOgImage(post(), { siteUrl: 'https://noviis.kr', distDir: dir })
        const png = await readFile(image.outputPath)
        validatePng(png)

        const html = buildPostOgMeta(image)
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
        const renderData = buildPreRenderedSnippet(post({ contents: '<p>본문</p>' }), 'https://noviis.kr/board/free/post/17/', image)
        const html = injectIntoTemplate('<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', renderData)

        expect(extractMetaContent(html, 'og:image')).toBe(image.url)
        expect(extractMetaContent(html, 'og:image:alt')).toBe(image.alt)
        expect(extractMetaContent(html, 'twitter:image')).toBe(image.url)
        expect(extractMetaContent(html, 'twitter:card')).toBe('summary_large_image')
    })
})
