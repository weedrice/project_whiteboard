import { describe, expect, it } from 'vitest'
import {
    buildPostFormPayload,
    extractFileIdFromPostImageSrc,
    extractPostFileIdsFromContent,
    resolvePostFormFileIds,
    toEmbedPostVideoUrl,
    toSafePostLinkUrl,
} from '../postForm'
import { decodeSandboxedPostHtml } from '../postHtmlSandbox'

const baseForm = {
    title: 'Title',
    content: '<p>Hello</p>',
    categoryId: '3',
    tags: ['a'],
    isNsfw: true,
    isSpoiler: true,
    isNotice: true,
    isSecret: true,
}

describe('postForm', () => {
    it('extracts local persisted file ids from image sources and data attributes', () => {
        expect(extractFileIdFromPostImageSrc('/api/v1/files/12', 'https://noviis.kr')).toBe(12)
        expect(extractFileIdFromPostImageSrc('/files/13', 'https://noviis.kr')).toBe(13)
        expect(extractFileIdFromPostImageSrc('https://external.test/files/14', 'https://noviis.kr')).toBeNull()

        const content = [
            '<img src="blob:https://noviis.kr/local" data-file-id="15">',
            '<img src="/api/v1/files/16">',
            '<img src="https://external.test/files/17">',
        ].join('')

        expect(extractPostFileIdsFromContent(content)).toEqual([15, 16])
    })

    it('filters draft payload file ids to files tracked by the draft', () => {
        const content = '<img src="/api/v1/files/10"><img src="/api/v1/files/11">'

        expect(resolvePostFormFileIds(content, [11], 'content')).toEqual([10, 11])
        expect(resolvePostFormFileIds(content, [11], 'draft')).toEqual([11])
    })

    it('builds the same post payload shape used by create and update flows', () => {
        expect(buildPostFormPayload({
            form: {
                ...baseForm,
                title: '  Title  ',
            },
            mode: 'create',
            showNotice: true,
            canShowNsfw: false,
            fileIds: [1, 2],
        })).toEqual({
            title: 'Title',
            categoryId: 3,
            tags: ['a'],
            contents: '<p>Hello</p>',
            isNsfw: false,
            isSpoiler: true,
            isSecret: true,
            isNotice: true,
            fileIds: [1, 2],
        })

        expect(buildPostFormPayload({
            form: baseForm,
            mode: 'edit',
            hideCategory: true,
            hideTags: true,
            hideSpoiler: true,
            hideSecret: true,
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })).toEqual({
            title: 'Title',
            tags: [],
            contents: '<p>Hello</p>',
            isNsfw: true,
            isSpoiler: false,
            isSecret: false,
            fileIds: [],
        })
    })

    it('encodes script-style html widgets before submit', () => {
        const rawWidget = '<style>.cl{display:grid}</style><button onclick="toggle()">여권</button><script>function toggle(){}</script>'
        const payload = buildPostFormPayload({
            form: {
                ...baseForm,
                content: rawWidget,
            },
            mode: 'create',
            showNotice: true,
            canShowNsfw: true,
            fileIds: [],
        })

        expect(payload.contents).toContain('noviis-sandboxed-post-html')
        expect(payload.contents).not.toContain('<script>')
        expect(payload.contents).not.toContain('onclick=')
        expect(decodeSandboxedPostHtml(payload.contents)).toBe(rawWidget)
    })

    it('normalizes supported video URLs to backend-allowed embed URLs', () => {
        expect(toEmbedPostVideoUrl('https://youtu.be/abc_123')).toBe('https://www.youtube.com/embed/abc_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://www.youtube.com/watch?v=watch_123')).toBe('https://www.youtube.com/embed/watch_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://youtube.com/shorts/short_123')).toBe('https://www.youtube.com/embed/short_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://www.youtube.com/embed/embed_123?start=10')).toBe('https://www.youtube.com/embed/embed_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://www.youtube-nocookie.com/embed/private_123')).toBe('https://www.youtube-nocookie.com/embed/private_123?showinfo=0')
        expect(toEmbedPostVideoUrl('https://vimeo.com/12345')).toBe('https://player.vimeo.com/video/12345')
        expect(toEmbedPostVideoUrl('https://player.vimeo.com/video/67890?autoplay=1')).toBe('https://player.vimeo.com/video/67890')
    })

    it('rejects unsupported or unsafe video URLs', () => {
        expect(toEmbedPostVideoUrl('https://example.com/video')).toBe('')
        expect(toEmbedPostVideoUrl('javascript:alert(1)')).toBe('')
        expect(toEmbedPostVideoUrl('data:text/html,video')).toBe('')
        expect(toEmbedPostVideoUrl('https://youtube.com/watch')).toBe('')
        expect(toEmbedPostVideoUrl('https://player.vimeo.com/channels/staffpicks/12345')).toBe('')
    })

    it('normalizes only safe post link URLs', () => {
        expect(toSafePostLinkUrl('https://example.com/path')).toBe('https://example.com/path')
        expect(toSafePostLinkUrl('http://example.com')).toBe('http://example.com/')
        expect(toSafePostLinkUrl('example.com/docs')).toBe('https://example.com/docs')
        expect(toSafePostLinkUrl('javascript:alert(1)')).toBe('')
        expect(toSafePostLinkUrl('data:text/html,link')).toBe('')
        expect(toSafePostLinkUrl('ftp://example.com/file')).toBe('')
        expect(toSafePostLinkUrl('https://user:pass@example.com')).toBe('')
        expect(toSafePostLinkUrl('http://')).toBe('')
    })
})
