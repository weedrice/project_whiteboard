import { describe, expect, it } from 'vitest'
import { renderPostContentHtml } from '../postContentHtml'
import { highlightCodeBlocksInDocument } from '../codeHighlighting'

describe('renderPostContentHtml', () => {
    it.each(['<script>hello</script>', '</style><img src=x onerror=alert(1)>', '설명 & "quotes" 🖼️', 'caption-0'])('preserves literal caption text after sanitization: %s', (caption) => {
        const image = document.createElement('img')
        image.setAttribute('src', '/api/v1/files/10')
        image.setAttribute('data-caption', caption)
        const doc = new DOMParser().parseFromString(renderPostContentHtml(image.outerHTML), 'text/html')
        expect(doc.querySelector('.nv-image-caption')?.textContent).toBe(caption)
        expect(doc.querySelectorAll('img')).toHaveLength(1)
        expect(doc.querySelector('script, style, [onerror]')).toBeNull()
    })
    it('renders captions as text and retains image alignment, width and alt text', () => {
        const result = renderPostContentHtml('<p><img src="/api/v1/files/10" alt="Diagram" class="tiptap-image-align-center" style="width:50%" data-caption="&lt;img src=x onerror=alert(1)&gt;"></p>')
        const doc = new DOMParser().parseFromString(result, 'text/html')
        expect(doc.querySelector('.nv-image-caption')?.textContent).toBe('<img src=x onerror=alert(1)>')
        expect(doc.querySelectorAll('img')).toHaveLength(1)
        expect(doc.querySelector('script')).toBeNull()
        expect(doc.querySelector<HTMLImageElement>('img')?.alt).toBe('Diagram')
        expect(doc.querySelector<HTMLElement>('.nv-image-with-caption')?.style.width).toBe('50%')
        expect(doc.querySelector('.nv-image-with-caption')?.classList.contains('tiptap-image-align-center')).toBe(true)
        expect(doc.querySelector('p > .nv-image-with-caption')).not.toBeNull()
    })
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
            copy: 'Copy',
            copyAriaLabel: 'Copy code',
        }, highlightCodeBlocksInDocument)

        expect(result).toContain('class="nv-code-block"')
        expect(result).toContain('class="nv-code-block-language"')
        expect(result).toContain('js')
        expect(result).toContain('data-code-copy-button')
        expect(result).toContain('Copy')
    })

    it('applies rich content transformers after sanitizing', () => {
        const result = renderPostContentHtml(
            '<p><span data-mention-user-id="7">@Novi</span></p><pre><code>const value = 1</code></pre><img src="/files/22">',
            undefined,
            highlightCodeBlocksInDocument
        )

        expect(result).toContain('data-mention-user-id="7"')
        expect(result).toContain('role="link"')
        expect(result).toContain('tabindex="0"')
        expect(result).toContain('class="nv-code-block"')
        expect(result).toContain('loading="lazy"')
        expect(result).toContain('src="/api/v1/files/22"')
    })
})
