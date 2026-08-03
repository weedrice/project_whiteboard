import { describe, expect, it } from 'vitest'
import { buildPostDetailPath, getFeedBodyHtml, getFeedMediaPreview, isFeedSpoiler, toEmbeddableVideoUrl } from '../feedPreview'

describe('feedPreview', () => {
    it('sanitizes excerpt html and strips media tags', () => {
        const html = getFeedBodyHtml({
            contentsExcerpt: '<p>Hello</p><img src="x" /><iframe src="x"></iframe><script>alert(1)</script>',
        })

        expect(html).toContain('<p>Hello</p>')
        expect(html).not.toContain('<img')
        expect(html).not.toContain('<iframe')
        expect(html).not.toContain('<script')
    })

    it('keeps blockquote markup in feed body previews', () => {
        const html = getFeedBodyHtml({
            contentsExcerpt: '<blockquote><p>Quoted</p></blockquote>',
        })

        expect(html).toContain('<blockquote>')
        expect(html).toContain('<p>Quoted</p>')
    })

    it('wraps plain text previews with paragraph and line break markup', () => {
        const html = getFeedBodyHtml({
            contentsExcerpt: undefined,
            summary: 'First line\nSecond line\n\nNext paragraph',
        })

        expect(html).toBe('<p>First line<br>Second line</p><p>Next paragraph</p>')
    })

    it('returns media preview metadata for image and video posts', () => {
        expect(getFeedMediaPreview({
            firstMediaType: 'video',
            firstMediaUrl: 'https://www.youtube.com/watch?v=abc123',
            thumbnailUrl: 'thumb',
        })).toEqual({
            showFirstVideo: true,
            videoUrl: 'https://www.youtube-nocookie.com/embed/abc123',
            imageUrl: 'thumb',
        })

        expect(getFeedMediaPreview({
            firstMediaType: 'image',
            firstMediaUrl: 'https://image',
            thumbnailUrl: 'thumb',
        })).toEqual({
            showFirstVideo: false,
            videoUrl: null,
            imageUrl: 'https://image',
        })
    })

    it('normalizes only embeddable video URLs', () => {
        expect(toEmbeddableVideoUrl('https://www.youtube.com/watch?v=abc123')).toBe('https://www.youtube-nocookie.com/embed/abc123')
        expect(toEmbeddableVideoUrl('https://youtu.be/abc123')).toBe('https://www.youtube-nocookie.com/embed/abc123')
        expect(toEmbeddableVideoUrl('https://vimeo.com/123456')).toBe('https://player.vimeo.com/video/123456')
        expect(toEmbeddableVideoUrl('javascript:alert(1)')).toBeNull()
        expect(toEmbeddableVideoUrl('https://example.com/video')).toBeNull()
        expect(toEmbeddableVideoUrl('https://attacker.example/embed/abc123')).toBeNull()
        expect(toEmbeddableVideoUrl('https://evil-youtube.com/watch?v=abc123')).toBeNull()
        expect(toEmbeddableVideoUrl('https://youtube.com.evil.example/watch?v=abc123')).toBeNull()
        expect(toEmbeddableVideoUrl('https://www.youtube.com/watch?v=')).toBeNull()
        expect(toEmbeddableVideoUrl('https://player.vimeo.com/video/not-a-number')).toBeNull()
    })

    it('derives spoiler state and post detail path', () => {
        expect(isFeedSpoiler({ isSpoiler: true })).toBe(true)
        expect(isFeedSpoiler({ isSpoiler: undefined as unknown as boolean, spoiler: true })).toBe(true)
        expect(buildPostDetailPath('free', 42)).toBe('/board/free/post/42/')
        expect(buildPostDetailPath('free', 42, '#comments')).toBe('/board/free/post/42/#comments')
        expect(buildPostDetailPath('free board', 'a/b')).toBe('/board/free%20board/post/a%2Fb/')
    })
})
