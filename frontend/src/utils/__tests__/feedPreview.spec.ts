import { describe, expect, it } from 'vitest'
import { buildPostDetailPath, getFeedBodyHtml, getFeedMediaPreview, isFeedSpoiler } from '../feedPreview'

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
            firstMediaUrl: 'https://video',
            thumbnailUrl: 'thumb',
        })).toEqual({
            showFirstVideo: true,
            imageUrl: 'thumb',
        })

        expect(getFeedMediaPreview({
            firstMediaType: 'image',
            firstMediaUrl: 'https://image',
            thumbnailUrl: 'thumb',
        })).toEqual({
            showFirstVideo: false,
            imageUrl: 'https://image',
        })
    })

    it('derives spoiler state and post detail path', () => {
        expect(isFeedSpoiler({ isSpoiler: true })).toBe(true)
        expect(isFeedSpoiler({ isSpoiler: undefined as unknown as boolean, spoiler: true })).toBe(true)
        expect(buildPostDetailPath('free', 42)).toBe('/board/free/post/42')
        expect(buildPostDetailPath('free', 42, '#comments')).toBe('/board/free/post/42#comments')
    })
})
