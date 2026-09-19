import { describe, expect, it } from 'vitest'
import { escapeHtml, extractMetaContent } from '../html-meta.mjs'

describe('shared HTML metadata helpers', () => {
    it.each([
        '<meta property="og:image:alt" content="한글 &lt;제목&gt; &amp; &quot;공유&quot; &#39;이미지&#39;">',
        "<META content='한글 &lt;제목&gt; &amp; &quot;공유&quot; &#39;이미지&#39;' name='og:image:alt'>",
    ])('reads either attribute order and decodes escaped metadata', (html) => {
        expect(extractMetaContent(html, 'og:image:alt')).toBe('한글 <제목> & "공유" \'이미지\'')
    })

    it('matches metadata keys literally instead of treating them as regular expressions', () => {
        const html = '<meta name="ogXimage" content="wrong"><meta name="og.image" content="correct">'
        expect(extractMetaContent(html, 'og.image')).toBe('correct')
        expect(extractMetaContent(html, 'twitter:image')).toBe('')
    })

    it('escapes HTML text and attributes without allowing tag or quote injection', () => {
        const value = '<script title="x">\'한글\' &</script>'
        const escaped = escapeHtml(value)
        expect(escaped).toBe('&lt;script title=&quot;x&quot;&gt;&#39;한글&#39; &amp;&lt;/script&gt;')
        expect(extractMetaContent(`<meta name="og:title" content="${escaped}">`, 'og:title')).toBe(value)
        expect(escapeHtml(null)).toBe('')
        expect(escapeHtml(17)).toBe('17')
    })
})
