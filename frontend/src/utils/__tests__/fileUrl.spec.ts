import { describe, expect, it } from 'vitest'
import { decodeSandboxedPostHtml, encodeSandboxedPostHtml } from '../postHtmlSandbox'
import {
    normalizeEditorFileImagePreviewSources,
    normalizeEditorFileImageUrls,
    normalizeFileUrl,
    normalizeLegacyFileUrls,
} from '../fileUrl'

describe('fileUrl', () => {
    it('does not treat foreign namespace templates as HTML template fragments', () => {
        const content = '<svg><template></template></svg><math><template></template></math>'
            + '<template data-noviis-layer="viewport"><img src="blob:preview" data-file-id="157" data-server-src="/files/157"></template>'
        const normalized = normalizeEditorFileImagePreviewSources(content)
        expect(normalized).toContain('src="/api/v1/files/157"')
        expect(normalized).not.toContain('blob:preview')
    })

    it('normalizes images inside viewport templates and preserved documents without losing head styles', () => {
        const source = '<!doctype html><html><head><style>.spark{color:red}</style></head><body>'
            + '<template data-noviis-layer="viewport"><img src="blob:preview" data-file-id="157" data-server-src="/files/157"></template>'
            + '<p>Body</p></body></html>'
        const normalized = normalizeEditorFileImagePreviewSources(encodeSandboxedPostHtml(source))
        const decoded = decodeSandboxedPostHtml(normalized)

        expect(decoded).toContain('<style>.spark{color:red}</style>')
        expect(decoded).toContain('<template data-noviis-layer="viewport">')
        expect(decoded).toMatch(/^<!DOCTYPE html><html>/)
        expect(decoded).toContain('src="/api/v1/files/157"')
        expect(decoded).toContain('data-file-id="157"')
        expect(decoded).not.toContain('blob:preview')
    })

    it.each([
        '<!-- example: <html> -->',
        '<style>.x::before{content:"<html>"}</style>',
        '<textarea><html></textarea>',
        '<p data-caption="<html>">Example</p>',
    ])('does not wrap a fragment when document-like text occurs in %s', (prefix) => {
        const source = `${prefix}<img src="blob:preview" data-file-id="157" data-server-src="/files/157">`
        const result = normalizeEditorFileImagePreviewSources(source)
        expect(result).not.toMatch(/^<!DOCTYPE|^<html>/)
        expect(result).toContain('src="/api/v1/files/157"')
    })

    it('does not normalize inactive generic or nested template contents', () => {
        const source = '<template><img src="blob:generic" data-server-src="/files/1"></template>'
            + '<template data-noviis-layer="viewport"><template><img src="blob:nested" data-server-src="/files/2"></template></template>'
        expect(normalizeEditorFileImagePreviewSources(source)).toBe(source)
    })

    it('normalizes legacy direct file URLs', () => {
        expect(normalizeFileUrl('/files/123')).toBe('/api/v1/files/123')
        expect(normalizeFileUrl('/files/123?size=sm')).toBe('/api/v1/files/123?size=sm')
        expect(normalizeFileUrl('/assets/a.png')).toBe('/assets/a.png')
    })

    it('normalizes legacy markdown and html file URLs in stored content', () => {
        const content = [
            '![emoticon](/files/10)',
            '<img src="/files/11?download=true">',
            '<a href="/files/12">file</a>',
        ].join('\n')

        expect(normalizeLegacyFileUrls(content)).toContain('![emoticon](/api/v1/files/10)')
        expect(normalizeLegacyFileUrls(content)).toContain('src="/api/v1/files/11?download=true"')
        expect(normalizeLegacyFileUrls(content)).toContain('href="/api/v1/files/12"')
    })

    it('replaces editor local preview image URLs with persisted file URLs', () => {
        const content = '<p><img src="blob:https://noviis.kr/local" data-file-id="157" data-server-src="/api/v1/files/157"></p>'

        const normalized = normalizeEditorFileImageUrls(content)

        expect(normalized).toContain('src="/api/v1/files/157"')
        expect(normalized).not.toContain('blob:https://noviis.kr/local')
        expect(normalized).not.toContain('data-file-id')
        expect(normalized).not.toContain('data-server-src')
    })

    it('replaces editor preview image sources without removing editor tracking attributes', () => {
        const content = '<p><img src="blob:https://noviis.kr/local" data-file-id="157" data-server-src="/files/157"></p>'

        const normalized = normalizeEditorFileImagePreviewSources(content)

        expect(normalized).toContain('src="/api/v1/files/157"')
        expect(normalized).toContain('data-file-id="157"')
        expect(normalized).toContain('data-server-src="/files/157"')
        expect(normalized).not.toContain('blob:https://noviis.kr/local')
    })

    it('does not trust non-local editor server image URLs', () => {
        const content = '<p><img src="blob:https://noviis.kr/local" data-file-id="157" data-server-src="https://evil.example/files/157"></p>'

        const normalized = normalizeEditorFileImageUrls(content)

        expect(normalized).toContain('src="blob:https://noviis.kr/local"')
        expect(normalized).not.toContain('data-file-id')
        expect(normalized).not.toContain('data-server-src')
        expect(normalized).not.toContain('evil.example')
    })

    it('does not replace editor image URLs when the tracked file id mismatches', () => {
        const content = '<p><img src="blob:https://noviis.kr/local" data-file-id="158" data-server-src="/api/v1/files/157"></p>'

        const normalized = normalizeEditorFileImageUrls(content)

        expect(normalized).toContain('src="blob:https://noviis.kr/local"')
        expect(normalized).not.toContain('src="/api/v1/files/157"')
    })
})
