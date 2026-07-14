import { mkdtemp, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import {
    buildPostOgMeta,
    createPostOgImageFilename,
    loadOgIcon,
    renderPostOgImage,
    resolvePostOgTitle,
    resolvePostOgImage,
} from '../og-image.mjs'
import { extractMetaContent, validatePng, validatePrerenderHtml } from '../prerender-output-validation.mjs'
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
    it('changes the cache filename when the public title or space changes', () => {
        const base = createPostOgImageFilename(post())

        expect(createPostOgImageFilename(post({ title: '수정된 제목' }))).not.toBe(base)
        expect(createPostOgImageFilename(post({ board: { boardName: '다른 스페이스' } }))).not.toBe(base)
        expect(base).toMatch(/^post-17-[a-f0-9]{12}\.png$/)
    })

    it('hides secret and blinded titles from alt text and cache identities', async () => {
        const secretPost = post({ title: '외부에 노출되면 안 되는 제목', isSecret: true })
        const blindedPost = post({ title: '신고로 차단된 제목', isBlinded: true })

        expect(resolvePostOgTitle(secretPost)).toBe('공개되지 않은 게시글입니다')
        expect(resolvePostOgTitle(blindedPost)).toBe('공개되지 않은 게시글입니다')
        expect(createPostOgImageFilename(secretPost)).toBe(createPostOgImageFilename({
            ...secretPost,
            title: '다른 비공개 제목',
        }))

        const dir = await mkdtemp(join(tmpdir(), 'noviis-og-private-'))
        tempDirs.push(dir)
        const image = await resolvePostOgImage(secretPost, { siteUrl: 'https://noviis.kr', distDir: dir })
        expect(image.alt).not.toContain(secretPost.title)
        expect(image.alt).toContain('공개되지 않은 게시글')
    }, 30_000)

    it('loads the favicon PNG as a stable data URL', async () => {
        await expect(loadOgIcon()).resolves.toMatch(/^data:image\/png;base64,/)
    })

    it('renders the icon, Korean long text, and special characters to a 1200x630 PNG', async () => {
        const png = await renderPostOgImage(post({
            title: '아주 긴 한글 제목과 <태그처럼 보이는 문자> & 따옴표 “테스트”를 포함해도 안전하게 세 줄 안에서 표현되는 공유 이미지 제목입니다',
        }))

        expect(() => validatePng(png)).not.toThrow()
    }, 30_000)

    it('always writes brand images even when the post has attachments', async () => {
        const dir = await mkdtemp(join(tmpdir(), 'noviis-og-'))
        tempDirs.push(dir)
        const image = await resolvePostOgImage(post({ imageUrls: ['/files/first.webp'] }), { siteUrl: 'https://noviis.kr', distDir: dir })
        const png = await readFile(image.outputPath)
        validatePng(png)

        expect(image.generated).toBe(true)
        expect(image.url).toContain('/img/og/')
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
        const renderData = buildPreRenderedSnippet(post({ contents: '<p>본문</p>' }), 'https://noviis.kr/board/free/post/17/', image)
        const html = injectIntoTemplate('<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', renderData)

        expect(extractMetaContent(html, 'og:image')).toBe(image.url)
        expect(extractMetaContent(html, 'og:image:alt')).toBe(image.alt)
        expect(extractMetaContent(html, 'twitter:image')).toBe(image.url)
        expect(extractMetaContent(html, 'twitter:card')).toBe('summary_large_image')
    })

    it('removes private post details from the complete pre-rendered HTML', () => {
        const privatePost = post({
            title: '숨겨야 하는 제목',
            contents: '<p>숨겨야 하는 본문</p>',
            author: { displayName: '숨겨야 하는 작성자' },
            createdAt: '2026-07-14T00:00:00Z',
            isBlinded: true,
        })
        const image = {
            url: 'https://noviis.kr/img/og/private.png',
            alt: '공개되지 않은 게시글입니다 공유 이미지',
            generated: true,
        }
        const renderData = buildPreRenderedSnippet(privatePost, 'https://noviis.kr/board/free/post/17/', image)
        const html = injectIntoTemplate('<html><head><title>NoviIs</title></head><body><div id="app"></div></body></html>', renderData)

        expect(html).toContain('공개되지 않은 게시글입니다')
        expect(html).not.toContain(privatePost.title)
        expect(html).not.toContain('숨겨야 하는 본문')
        expect(html).not.toContain('숨겨야 하는 작성자')
        expect(html).not.toContain(privatePost.createdAt)
    })
})
