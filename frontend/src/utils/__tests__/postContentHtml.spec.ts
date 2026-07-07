import { describe, expect, it } from 'vitest'
import { renderPostContentHtml } from '../postContentHtml'

describe('renderPostContentHtml', () => {
    it('sanitizes unsafe post html and preserves safe content', () => {
        const result = renderPostContentHtml(
            '<p>Hello</p><a href="javascript:alert(1)">bad</a><img src="/files/11" onerror="alert(1)"><script>alert(2)</script>'
        )

        expect(result).toContain('<p>Hello</p>')
        expect(result).toContain('src="/api/v1/files/11"')
        expect(result).toContain('loading="lazy"')
        expect(result).not.toContain('javascript:')
        expect(result).not.toContain('onerror')
        expect(result).not.toContain('<script')
    })

    it('keeps intentional empty paragraphs visible', () => {
        expect(renderPostContentHtml('<p> </p>')).toContain('<p><br></p>')
    })

    it('adds code block language labels and copy controls', () => {
        const result = renderPostContentHtml('<pre><code class="language-js">const value = 1</code></pre>', {
            copy: '복사',
            copyAriaLabel: '코드 복사',
        })

        expect(result).toContain('class="nv-code-block"')
        expect(result).toContain('class="nv-code-block-language"')
        expect(result).toContain('js')
        expect(result).toContain('data-code-copy-button')
        expect(result).toContain('복사')
    })
})
