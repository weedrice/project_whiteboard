import { describe, expect, it } from 'vitest'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '@/features/comments/commentContent'

describe('commentContent', () => {
    it('returns empty output for undefined or empty input', () => {
        expect(renderCommentContentHtml(undefined)).toBe('')
        expect(renderCommentContentHtml('')).toBe('')
        expect(isEmoticonOnlyContent(undefined)).toBe(false)
        expect(isEmoticonOnlyContent('')).toBe(false)
    })

    it('sanitizes unsafe html', () => {
        const html = renderCommentContentHtml('<img src=x onerror=alert(1)><script>alert(1)</script>hello')

        expect(html).not.toContain('onerror=')
        expect(html).not.toContain('<script')
        expect(html).toContain('hello')
    })

    it('renders only safe emoticon URLs', () => {
        const safeHtml = renderCommentContentHtml('![emoticon](https://example.com/a.png)')
        const blockedHtml = renderCommentContentHtml('![emoticon](javascript:alert(1))')

        expect(safeHtml).toContain('<img')
        expect(safeHtml).toContain('https://example.com/a.png')
        expect(blockedHtml).not.toContain('javascript:')
        expect(blockedHtml).not.toContain('<img')
    })

    it('supports relative emoticon URLs and strips blank emoticon URLs', () => {
        const relativeHtml = renderCommentContentHtml('![emoticon](/assets/a.png)', 'emoji')
        const blankHtml = renderCommentContentHtml('![emoticon](   )')

        expect(relativeHtml).toContain('<img')
        expect(relativeHtml).toContain('/assets/a.png')
        expect(relativeHtml).toContain('class="emoji"')
        expect(blankHtml).not.toContain('<img')
    })

    it('normalizes legacy file emoticon URLs', () => {
        const html = renderCommentContentHtml('![emoticon](/files/123?size=sm)', 'emoji')

        expect(html).toContain('/api/v1/files/123?size=sm')
        expect(html).not.toContain('src="/files/123')
    })

    it('preserves line breaks in comment text', () => {
        const html = renderCommentContentHtml('first line\nsecond line')

        expect(html).toContain('first line')
        expect(html).toContain('<br')
        expect(html).toContain('second line')
    })

    it('preserves line breaks around emoticons', () => {
        const html = renderCommentContentHtml('hello\n![emoticon](https://example.com/a.png)\nworld')

        expect(html).toContain('hello')
        expect(html).toContain('<img')
        expect(html).toContain('world')
        expect((html.match(/<br/g) || [])).toHaveLength(2)
    })

    it('detects emoticon-only comments', () => {
        expect(isEmoticonOnlyContent('![emoticon](https://example.com/a.png)')).toBe(true)
        expect(isEmoticonOnlyContent('text ![emoticon](https://example.com/a.png)')).toBe(false)
    })
})
