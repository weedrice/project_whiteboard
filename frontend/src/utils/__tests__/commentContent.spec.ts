import { describe, expect, it } from 'vitest'
import { isEmoticonOnlyContent, renderCommentContentHtml } from '../commentContent'

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

    it('detects emoticon-only comments', () => {
        expect(isEmoticonOnlyContent('![emoticon](https://example.com/a.png)')).toBe(true)
        expect(isEmoticonOnlyContent('text ![emoticon](https://example.com/a.png)')).toBe(false)
    })
})
