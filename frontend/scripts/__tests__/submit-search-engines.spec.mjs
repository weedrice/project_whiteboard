import { describe, expect, it } from 'vitest'
import {
    httpFailure,
    isPrivateAddress,
    parseAllowedOrigins,
    resolveCustomSubmitTarget,
    submitPinnedHttps,
    validateSubmissionConfiguration,
    validateGoogleCredentialConfiguration,
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
    it.each(['127.0.0.1', '10.1.2.3', '169.254.1.1', '192.168.1.2', '203.0.113.10', '::1', 'fd00::1', 'fe80::1', '2001:db8::1'])(
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
            async () => [{ address: '93.184.216.34', family: 4 }]
        )
        expect(validated).toContain('https://submit.example/ping')
    })

    it('pins a validated custom endpoint to the public DNS answer', async () => {
        const target = await resolveCustomSubmitTarget(
            'https://submit.example/ping',
            'https://submit.example',
            async () => [{ address: '93.184.216.34', family: 4 }]
        )
        expect(target).toEqual({
            url: 'https://submit.example/ping',
            address: '93.184.216.34',
            family: 4
        })
    })

    it('rejects partial Google refresh credentials', () => {
        expect(() => validateGoogleCredentialConfiguration({
            accessToken: '',
            clientId: 'client',
            clientSecret: '',
            refreshToken: 'refresh'
        })).toThrow('all-or-none')
    })

    it('accepts either an access token or a complete refresh credential set', () => {
        expect(validateGoogleCredentialConfiguration({ accessToken: 'token' })).toBe(true)
        expect(validateGoogleCredentialConfiguration({
            clientId: 'client', clientSecret: 'secret', refreshToken: 'refresh'
        })).toBe(true)
    })

    it('fails required preflight without a provider and validates an allowlisted custom provider', () => {
        expect(() => validateSubmissionConfiguration({ SEO_SUBMIT_REQUIRED: 'true' })).toThrow('at least one')
        expect(validateSubmissionConfiguration({
            SEO_SUBMIT_REQUIRED: 'true',
            CUSTOM_SITEMAP_SUBMIT_URL: 'https://submit.example/ping',
            CUSTOM_SITEMAP_SUBMIT_ALLOWED_ORIGINS: 'https://submit.example'
        })).toEqual({ googleConfigured: false, customConfigured: true })
    })

    it.each([
        ['http://submit.example/ping', 'https://submit.example'],
        ['https://user:secret@submit.example/ping', 'https://submit.example'],
        ['https://evil.example/ping', 'https://submit.example']
    ])('rejects unsafe or non-allowlisted endpoint %s', async (endpoint, allowlist) => {
        await expect(validateCustomSubmitUrl(endpoint, allowlist, async () => [{ address: '93.184.216.34' }]))
            .rejects.toThrow()
    })

    it('rejects an allowlisted hostname when any DNS answer is private', async () => {
        await expect(validateCustomSubmitUrl(
            'https://submit.example/ping',
            'https://submit.example',
            async () => [{ address: '93.184.216.34' }, { address: '127.0.0.1' }]
        )).rejects.toThrow('private or invalid')
    })
})

describe('pinned HTTPS transport', () => {
    const target = {
        name: 'custom-submit',
        pinned: { address: '93.184.216.34', family: 4 },
        request: { method: 'POST', url: 'https://submit.example/ping' }
    }

    it('preserves the TLS hostname and supports scalar and all DNS lookup contracts', async () => {
        let requestOptions
        const requestFactory = (_url, options, onResponse) => {
            requestOptions = options
            queueMicrotask(() => onResponse({ statusCode: 204, statusMessage: '', resume() {} }))
            return {
                setTimeout() {},
                on() {},
                end() {}
            }
        }

        await submitPinnedHttps(target, requestFactory, 10)
        expect(requestOptions.servername).toBe('submit.example')
        expect(requestOptions.headers.Host).toBe('submit.example')
        await expect(new Promise((resolve, reject) => requestOptions.lookup('submit.example', {}, (error, address, family) => {
            if (error) reject(error)
            else resolve({ address, family })
        }))).resolves.toEqual({ address: '93.184.216.34', family: 4 })
        await expect(new Promise((resolve, reject) => requestOptions.lookup('submit.example', { all: true }, (error, addresses) => {
            if (error) reject(error)
            else resolve(addresses)
        }))).resolves.toEqual([{ address: '93.184.216.34', family: 4 }])
    })

    it('destroys a timed-out request and rejects redirects', async () => {
        let timeoutHandler
        let errorHandler
        const timeoutFactory = () => ({
            setTimeout(_timeout, handler) { timeoutHandler = handler },
            on(event, handler) { if (event === 'error') errorHandler = handler },
            end() {},
            destroy(error) { errorHandler(error) }
        })
        const timeoutPromise = submitPinnedHttps(target, timeoutFactory, 1)
        timeoutHandler()
        await expect(timeoutPromise).rejects.toThrow('timeout')

        const redirectFactory = (_url, _options, onResponse) => {
            queueMicrotask(() => onResponse({ statusCode: 302, statusMessage: 'Found', resume() {} }))
            return { setTimeout() {}, on() {}, end() {} }
        }
        await expect(submitPinnedHttps(target, redirectFactory, 10)).rejects.toThrow('HTTP 302 Found')
    })
})
