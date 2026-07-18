export const SITEMAP_PROTOCOL_MAX_URLS = 50_000
export const DEFAULT_SEO_POST_URL_CAPACITY = 2_000

export function assertSeoPostUrlCountWithinCapacity(count, capacity) {
    if (!Number.isSafeInteger(count) || count < 0) {
        throw new Error('SEO post URL count must be a non-negative integer')
    }
    if (count > capacity) {
        throw new Error(`SEO post URL capacity exceeded: ${count}/${capacity}`)
    }
}

export function readSeoPostUrlCapacity(env = process.env) {
    const raw = env.SEO_POST_URL_CAPACITY
    if (raw === undefined) return DEFAULT_SEO_POST_URL_CAPACITY
    const normalized = String(raw).trim()
    if (!/^[1-9][0-9]*$/.test(normalized)) {
        throw new Error('SEO_POST_URL_CAPACITY must be a positive integer')
    }
    const value = Number(normalized)
    if (!Number.isSafeInteger(value)) {
        throw new Error('SEO_POST_URL_CAPACITY must be a safe integer')
    }
    if (value >= SITEMAP_PROTOCOL_MAX_URLS) {
        throw new Error(`SEO_POST_URL_CAPACITY must be below ${SITEMAP_PROTOCOL_MAX_URLS}`)
    }
    return value
}
