import { describe, expect, it } from 'vitest'
import {
    assertSeoPostUrlCountWithinCapacity,
    DEFAULT_SEO_POST_URL_CAPACITY,
    readSeoPostUrlCapacity,
    SITEMAP_PROTOCOL_MAX_URLS,
} from '../seo-capacity.mjs'

describe('SEO capacity contract', () => {
    it('uses one shared production default', () => {
        expect(readSeoPostUrlCapacity({})).toBe(DEFAULT_SEO_POST_URL_CAPACITY)
        expect(DEFAULT_SEO_POST_URL_CAPACITY).toBe(2000)
    })

    it.each(['2000', '2001'])('accepts an explicit bounded capacity: %s', (capacity) => {
        expect(readSeoPostUrlCapacity({ SEO_POST_URL_CAPACITY: capacity })).toBe(Number(capacity))
    })

    it('rejects a capacity that can overflow one sitemap', () => {
        expect(() => readSeoPostUrlCapacity({
            SEO_POST_URL_CAPACITY: String(SITEMAP_PROTOCOL_MAX_URLS),
        })).toThrow('must be below')
    })

    it.each(['', '0', '-1', 'abc', '2000junk'])(
        'rejects a malformed configured capacity: %s',
        (capacity) => {
            expect(() => readSeoPostUrlCapacity({ SEO_POST_URL_CAPACITY: capacity }))
                .toThrow('must be a positive integer')
        },
    )

    it('fails closed when prerender input exceeds the shared capacity', () => {
        expect(() => assertSeoPostUrlCountWithinCapacity(
            DEFAULT_SEO_POST_URL_CAPACITY + 1,
            DEFAULT_SEO_POST_URL_CAPACITY,
        )).toThrow('capacity exceeded')
    })
})
