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

    it('sanitizeQuillHtml allows editor tags and strips dangerous attrs', () => {
        const html = '<iframe src="https://www.youtube.com/embed/abc" onerror="x()"></iframe><mark data-evil="1">m</mark><hr>'
        const clean = sanitizeQuillHtml(html)

        expect(clean).toContain('<iframe')
        expect(clean).toContain('src="https://www.youtube.com/embed/abc"')
        expect(clean).toContain('loading="lazy"')
        expect(clean).toContain('referrerpolicy="strict-origin-when-cross-origin"')
        expect(clean).toContain('sandbox="allow-scripts allow-same-origin allow-presentation"')
        expect(clean).toContain('allow="encrypted-media; picture-in-picture"')
        expect(clean).toContain('<mark>m</mark>')
        expect(clean).toContain('<hr>')
        expect(clean).not.toContain('onerror=')
        expect(clean).not.toContain('data-evil')
    })

    it('sanitizeQuillHtml removes disallowed media tags and iframe sources', () => {
        const html = [
            '<video src="/a.mp4"></video>',
            '<iframe src="javascript:alert(1)"></iframe>',
            '<iframe src="https://evil.example/embed/abc"></iframe>',
            '<iframe src="http://www.youtube.com/embed/abc"></iframe>',
            '<iframe src="https://www.youtube.com/embed/abc/extra"></iframe>',
            '<iframe src="https://player.vimeo.com/video/not-a-number"></iframe>',
        ].join('')
        const clean = sanitizeQuillHtml(html)

        expect(clean).not.toContain('<video')
        expect(clean).not.toContain('javascript:')
        expect(clean).not.toContain('evil.example')
        expect(clean).not.toContain('http://www.youtube.com')
        expect(clean).not.toContain('abc/extra')
        expect(clean).not.toContain('not-a-number')
    })

    it('sanitizeQuillHtml keeps exact https video embed paths', () => {
        const html = [
            '<iframe src="https://www.youtube.com/embed/abc"></iframe>',
            '<iframe src="https://player.vimeo.com/video/123"></iframe>',
            '<iframe src="https://player.vimeo.com/video/456/"></iframe>',
        ].join('')
        const clean = sanitizeQuillHtml(html)

        expect(clean).toContain('https://www.youtube.com/embed/abc')
        expect(clean).toContain('https://player.vimeo.com/video/123')
        expect(clean).toContain('https://player.vimeo.com/video/456/')
    })

    it('sanitizeQuillHtml keeps only safe editor inline styles', () => {
        const html = '<p style="color: red; position: fixed; background-image: url(javascript:alert(1)); text-align: center; line-height: 1.5">hello</p>'
        const clean = sanitizeQuillHtml(html)

        expect(clean).toContain('style="color: red; text-align: center; line-height: 1.5"')
        expect(clean).not.toContain('position')
        expect(clean).not.toContain('background-image')
        expect(clean).not.toContain('javascript:')
    })
})
