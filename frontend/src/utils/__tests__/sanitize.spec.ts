import { describe, expect, it } from 'vitest'
import { sanitizeHtml, sanitizeQuillHtml } from '../sanitize'

describe('sanitize', () => {
    it('sanitizeHtml removes script tags and event handlers', () => {
        const dirty = '<p onclick="evil()">hello</p><script>alert(1)</script>'
        const clean = sanitizeHtml(dirty)

        expect(clean).toContain('<p>hello</p>')
        expect(clean).not.toContain('onclick=')
        expect(clean).not.toContain('<script')
    })

    it('sanitizeHtml merges custom options', () => {
        const html = '<mark data-value="1">tag</mark>'
        const clean = sanitizeHtml(html, {
            ALLOWED_TAGS: ['mark'],
            ALLOWED_ATTR: ['data-value'],
            ALLOW_DATA_ATTR: true,
        })

        expect(clean).toContain('<mark data-value="1">tag</mark>')
    })

    it('sanitizeQuillHtml allows quill/tiptap tags and strips dangerous attrs', () => {
        const html = '<video src="/a.mp4" onerror="x()"></video><mark data-color="#fff">m</mark>'
        const clean = sanitizeQuillHtml(html)

        expect(clean).toContain('<video src="/a.mp4"></video>')
        expect(clean).toContain('<mark data-color="#fff">m</mark>')
        expect(clean).not.toContain('onerror=')
    })
})
