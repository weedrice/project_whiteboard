import { describe, expect, it } from 'vitest'
import { normalizeFileUrl, normalizeLegacyFileUrls } from '../fileUrl'

describe('fileUrl', () => {
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
})
