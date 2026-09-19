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

    it('renders comparison signs and ampersands as their original visible characters', () => {
        const content = '1 < 2 and 3 > 2 & "quoted"'
        const html = renderCommentContentHtml(content)
        const document = new DOMParser().parseFromString(html, 'text/html')

        expect(document.body.textContent).toBe(content)
        expect(html).not.toContain('&amp;lt;')
        expect(html).not.toContain('&amp;gt;')
    })

    it('keeps legacy HTML text while stripping tags and executable content', () => {
        const html = renderCommentContentHtml('<strong>A &amp; B</strong><script>alert(1)</script><img src=x onerror=alert(2)>')
        const document = new DOMParser().parseFromString(html, 'text/html')

        expect(document.body.textContent).toBe('A & B')
        expect(document.body.children).toHaveLength(0)
    })

    it('shows encoded HTML as inert text without turning it into executable markup', () => {
        const html = renderCommentContentHtml('&lt;img src=x onerror=alert(1)&gt;')
        const document = new DOMParser().parseFromString(html, 'text/html')

        expect(document.body.textContent).toBe('<img src=x onerror=alert(1)>')
        expect(document.querySelector('img')).toBeNull()
    })

    it('preserves mention links for display names containing ampersands', () => {
        const html = renderCommentContentHtml('Hello @A&B: 1 < 2', 'comment-emoticon', [{ userId: 7, displayName: 'A&B' }])
        const document = new DOMParser().parseFromString(html, 'text/html')
        const mention = document.querySelector('[data-mention-user-id="7"]')

        expect(document.body.textContent).toBe('Hello @A&B: 1 < 2')
        expect(mention?.textContent).toBe('@A&B')
        expect(mention?.getAttribute('role')).toBe('link')
        expect(mention?.getAttribute('tabindex')).toBe('0')
    })

    it('does not preserve forged mention markup from comment text', () => {
        const html = renderCommentContentHtml('<span data-mention-user-id="99" onclick="alert(1)">pretend mention</span>')
        const document = new DOMParser().parseFromString(html, 'text/html')

        expect(document.body.textContent).toBe('pretend mention')
        expect(document.body.children).toHaveLength(0)
    })
})
