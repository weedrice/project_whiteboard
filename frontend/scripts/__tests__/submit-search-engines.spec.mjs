import { describe, expect, it } from 'vitest'
import {
    httpFailure,
    isPrivateAddress,
    parseAllowedOrigins,
    validateCustomSubmitUrl
} from '../submit-search-engines.mjs'

describe('search engine submit endpoint validation', () => {
    it('reports stable HTTP metadata without response bodies', () => {
        const error = httpFailure('custom-submit', {
            status: 503,
            statusText: 'Unavailable',
            text: async () => 'response-payload'
        })
        expect(error.message).toBe('custom-submit failed: HTTP 503 Unavailable')
        expect(error.message).not.toContain('response-payload')
    })
    it.each(['127.0.0.1', '10.1.2.3', '169.254.1.1', '192.168.1.2', '::1', 'fd00::1', 'fe80::1'])(
        'rejects private address %s',
        (address) => expect(isPrivateAddress(address)).toBe(true)
    )

    it('normalizes an exact origin allowlist', () => {
        expect([...parseAllowedOrigins('https://submit.example/path, https://other.example')]).toEqual([
            'https://submit.example',
            'https://other.example'
        ])
    })

    it('accepts an allowlisted HTTPS endpoint resolving only to public addresses', async () => {
        const validated = await validateCustomSubmitUrl(
            'https://submit.example/ping?sitemap={sitemapRaw}',
            'https://submit.example',
            async () => [{ address: '203.0.113.10', family: 4 }]
        )
        expect(validated).toContain('https://submit.example/ping')
    })

    it.each([
        ['http://submit.example/ping', 'https://submit.example'],
        ['https://user:secret@submit.example/ping', 'https://submit.example'],
        ['https://evil.example/ping', 'https://submit.example']
    ])('rejects unsafe or non-allowlisted endpoint %s', async (endpoint, allowlist) => {
        await expect(validateCustomSubmitUrl(endpoint, allowlist, async () => [{ address: '203.0.113.10' }]))
            .rejects.toThrow()
    })

    it('rejects an allowlisted hostname when any DNS answer is private', async () => {
        await expect(validateCustomSubmitUrl(
            'https://submit.example/ping',
            'https://submit.example',
            async () => [{ address: '203.0.113.10' }, { address: '127.0.0.1' }]
        )).rejects.toThrow('private or invalid')
    })
})
