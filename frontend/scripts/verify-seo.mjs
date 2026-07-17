#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { lookup } from 'node:dns/promises'
import { request as httpsRequest } from 'node:https'
import { isIP } from 'node:net'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const siteUrl = normalizeBaseUrl(process.env.SEO_SITE_URL ?? 'https://noviis.kr')
const sitemapUrl = process.env.SEO_SITEMAP_URL ?? `${siteUrl}/sitemap.xml`
const requestTimeoutMs = parsePositiveInt(process.env.SEO_VERIFY_TIMEOUT_MS, 15000)
const maxResponseBytes = parsePositiveInt(process.env.SEO_VERIFY_MAX_RESPONSE_BYTES, 5 * 1024 * 1024)
const maxUrlChecks = parsePositiveInt(process.env.SEO_VERIFY_MAX_URLS, 10)
const requirePostUrls = process.env.SEO_REQUIRE_POST_URLS === 'true'
const requireReleaseManifest = process.env.SEO_REQUIRE_RELEASE_MANIFEST === 'true'
const releaseManifestUrl = process.env.SEO_RELEASE_MANIFEST_URL ?? `${siteUrl}/.noviis-seo-release.json`
const expectedReleaseSha = String(process.env.SEO_EXPECTED_RELEASE_SHA ?? '').trim()
const siteOrigin = new URL(siteUrl).origin
const pageOrigins = new Set([siteOrigin])
const imageOrigins = new Set([
    siteOrigin,
    ...String(process.env.SEO_IMAGE_ALLOWED_ORIGINS ?? '')
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean)
        .map((value) => new URL(value).origin)
])
const userAgents = [
    { name: 'googlebot', value: process.env.SEO_GOOGLEBOT_UA ?? 'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)' }
]

function normalizeBaseUrl(url) {
    return String(url).replace(/\/+$/, '')
}

function parsePositiveInt(value, fallback) {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

export function isPrivateOrReservedAddress(address) {
    const normalized = String(address)
        .toLowerCase()
        .replace(/^\[|\]$/g, '')
        .split('%', 1)[0]
    if (isIP(normalized) === 4) {
        const [a, b] = normalized.split('.').map(Number)
        return a === 0
            || a === 10
            || a === 127
            || (a === 100 && b >= 64 && b <= 127)
            || (a === 169 && b === 254)
            || (a === 172 && b >= 16 && b <= 31)
            || (a === 192 && (b === 0 || b === 168))
            || (a === 198 && (b === 18 || b === 19 || b === 51))
            || (a === 203 && b === 0)
            || a >= 224
    }
    if (isIP(normalized) === 6) {
        if (normalized === '::' || normalized === '::1') return true
        if (normalized.startsWith('fc') || normalized.startsWith('fd') || /^fe[89a-f]/.test(normalized)) return true
        if (normalized.startsWith('ff') || normalized.startsWith('2001:db8:') || normalized === '2001:db8::') return true
        const mapped = normalized.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/)?.[1]
        if (mapped) return isPrivateOrReservedAddress(mapped)
        const mappedHex = normalized.match(/^::(?:ffff:)?([0-9a-f]{1,4}):([0-9a-f]{1,4})$/)
        if (mappedHex) {
            const high = Number.parseInt(mappedHex[1], 16)
            const low = Number.parseInt(mappedHex[2], 16)
            return isPrivateOrReservedAddress([
                high >>> 8,
                high & 0xff,
                low >>> 8,
                low & 0xff,
            ].join('.'))
        }
        if (normalized.startsWith('::ffff:')) return true
        return !/^[23][0-9a-f]{3}:/.test(normalized)
    }
    return true
}

export function assertAllowedSeoUrl(value, allowedOrigins, label = 'SEO URL') {
    let parsed
    try {
        parsed = new URL(String(value))
    } catch {
        throw new Error(`${label} is not an absolute URL`)
    }
    if (parsed.protocol !== 'https:') throw new Error(`${label} must use HTTPS`)
    if (parsed.username || parsed.password) throw new Error(`${label} must not contain credentials`)
    if (parsed.hash) throw new Error(`${label} must not contain a fragment`)
    if (!allowedOrigins.has(parsed.origin)) throw new Error(`${label} origin is not allowed: ${parsed.origin}`)
    if (isIP(parsed.hostname) && isPrivateOrReservedAddress(parsed.hostname)) {
        throw new Error(`${label} resolves to a private or reserved address`)
    }
    return parsed
}

async function resolvePublicAddresses(url, label) {
    const literalFamily = isIP(url.hostname)
    const addresses = literalFamily
        ? [{ address: url.hostname, family: literalFamily }]
        : await lookup(url.hostname, { all: true, verbatim: true })
    if (addresses.length === 0 || addresses.some(({ address }) => isPrivateOrReservedAddress(address))) {
        throw new Error(`${label} resolves to a private or reserved address`)
    }
    return addresses
}

export function resolveAllowedRedirect(currentUrl, location, allowedOrigins, label = 'SEO redirect') {
    if (!location) throw new Error(`${label} is missing Location`)
    return assertAllowedSeoUrl(new URL(location, currentUrl).toString(), allowedOrigins, label)
}

async function requestPinned(url, userAgent, label, includeBody) {
    const startedAt = Date.now()
    let dnsDeadline
    const addresses = await Promise.race([
        resolvePublicAddresses(url, label),
        new Promise((_, reject) => {
            dnsDeadline = setTimeout(
                () => reject(new Error(`${label} exceeded the DNS resolution deadline`)),
                requestTimeoutMs,
            )
        }),
    ]).finally(() => clearTimeout(dnsDeadline))
    const selected = addresses[0]
    const remainingMs = Math.max(1, requestTimeoutMs - (Date.now() - startedAt))
    return new Promise((resolveRequest, rejectRequest) => {
        let settled = false
        const deadline = setTimeout(() => {
            request.destroy(new Error(`${label} exceeded the total request deadline`))
        }, remainingMs)
        const resolveOnce = (value) => {
            if (settled) return
            settled = true
            clearTimeout(deadline)
            resolveRequest(value)
        }
        const rejectOnce = (error) => {
            if (settled) return
            settled = true
            clearTimeout(deadline)
            rejectRequest(error)
        }
        const toResponse = (response, text = '') => ({
            ok: (response.statusCode ?? 0) >= 200 && (response.statusCode ?? 0) < 300,
            status: response.statusCode ?? 0,
            location: Array.isArray(response.headers.location)
                ? response.headers.location[0]
                : response.headers.location ?? null,
            contentType: Array.isArray(response.headers['content-type'])
                ? response.headers['content-type'][0] ?? ''
                : response.headers['content-type'] ?? '',
            text,
        })
        const request = httpsRequest({
            protocol: 'https:',
            hostname: url.hostname,
            port: url.port || 443,
            path: `${url.pathname}${url.search}`,
            method: 'GET',
            servername: url.hostname,
            headers: userAgent ? { 'User-Agent': userAgent } : undefined,
            lookup: (_hostname, options, callback) => {
                if (options?.all) callback(null, [selected])
                else callback(null, selected.address, selected.family)
            },
        }, (response) => {
            response.on('error', rejectOnce)
            if (!includeBody) {
                resolveOnce(toResponse(response))
                response.destroy()
                return
            }

            const chunks = []
            let receivedBytes = 0
            response.on('data', (chunk) => {
                receivedBytes += chunk.length
                if (receivedBytes > maxResponseBytes) {
                    const error = new Error(`${label} exceeds the response size limit`)
                    rejectOnce(error)
                    response.destroy(error)
                    return
                }
                chunks.push(chunk)
            })
            response.on('end', () => resolveOnce(toResponse(
                response,
                Buffer.concat(chunks).toString('utf8'),
            )))
        })
        request.setTimeout(remainingMs, () => request.destroy(new Error(`${label} timed out`)))
        request.on('error', rejectOnce)
        request.end()
    })
}

async function fetchAllowed(url, userAgent, allowedOrigins, label, includeBody) {
    let current = assertAllowedSeoUrl(url, allowedOrigins, label)
    for (let redirects = 0; redirects <= 5; redirects += 1) {
        const response = await requestPinned(current, userAgent, label, includeBody)
        if (response.status < 300 || response.status >= 400) return response
        if (redirects === 5) throw new Error(`${label} exceeded the redirect limit`)
        current = resolveAllowedRedirect(current, response.location, allowedOrigins, label)
    }
    throw new Error(`${label} redirect handling failed`)
}

async function fetchText(url, userAgent) {
    const response = await fetchAllowed(url, userAgent, pageOrigins, 'SEO page URL', true)
    return { ok: response.ok, status: response.status, text: response.text }
}

async function fetchImageMetadata(url, userAgent) {
    const response = await fetchAllowed(url, userAgent, imageOrigins, 'SEO image URL', false)
    return {
        ok: response.ok,
        status: response.status,
        contentType: response.contentType
    }
}

function parseSitemapUrls(xmlText) {
    return [...xmlText.matchAll(/<loc>(.*?)<\/loc>/g)]
        .map((match) => match[1].trim())
        .filter(Boolean)
}

function findPostUrls(urls) {
    return urls.filter((url) => {
        try {
            const pathname = new URL(url).pathname
            return /^\/board\/[^/]+\/post\/\d+\/?$/.test(pathname)
        } catch {
            return false
        }
    })
}

function assertContains(text, pattern, message, failures) {
    if (!pattern.test(text)) {
        failures.push(message)
    }
}

export function sitemapSha256(xmlText) {
    return createHash('sha256').update(xmlText, 'utf8').digest('hex')
}

export function assertPostUrlsPresent(postUrls, required) {
    if (required && postUrls.length === 0) {
        throw new Error('production sitemap contains no post URLs')
    }
}

function requireNonNegativeInteger(value, field) {
    if (!Number.isSafeInteger(value) || value < 0) {
        throw new Error(`release manifest ${field} must be a non-negative integer`)
    }
    return value
}

export function validateReleaseManifest({ sitemapText, allUrls, postUrls, manifestText, expectedSha = '' }) {
    let manifest
    try {
        manifest = JSON.parse(manifestText)
    } catch {
        throw new Error('release manifest is not valid JSON')
    }
    if (!manifest || typeof manifest !== 'object' || Array.isArray(manifest)) {
        throw new Error('release manifest must be a JSON object')
    }
    if (!/^[0-9a-f]{40}$/.test(manifest.commitSha ?? '')) {
        throw new Error('release manifest commitSha is invalid')
    }
    if (expectedSha && manifest.commitSha !== expectedSha) {
        throw new Error(`release manifest commit mismatch: expected ${expectedSha}, received ${manifest.commitSha}`)
    }
    const urlCount = requireNonNegativeInteger(manifest.urlCount, 'urlCount')
    const postUrlCount = requireNonNegativeInteger(manifest.postUrlCount, 'postUrlCount')
    const prerenderCount = requireNonNegativeInteger(manifest.prerenderCount, 'prerenderCount')
    if (urlCount !== allUrls.length) {
        throw new Error(`release manifest URL count mismatch: expected ${urlCount}, received ${allUrls.length}`)
    }
    if (postUrlCount !== postUrls.length) {
        throw new Error(`release manifest post URL count mismatch: expected ${postUrlCount}, received ${postUrls.length}`)
    }
    if (prerenderCount !== postUrlCount) {
        throw new Error(`release manifest prerender count mismatch: posts=${postUrlCount}, prerenders=${prerenderCount}`)
    }
    const actualDigest = sitemapSha256(sitemapText)
    if (!/^[0-9a-f]{64}$/.test(manifest.sitemapSha256 ?? '') || manifest.sitemapSha256 !== actualDigest) {
        throw new Error('release manifest sitemap digest mismatch')
    }
    return manifest
}

function escapeRegExp(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function extractMetaContent(html, key) {
    const escapedKey = escapeRegExp(key)
    const forward = new RegExp(`<meta[^>]+(?:property|name)=["']${escapedKey}["'][^>]+content=["']([^"']+)["'][^>]*>`, 'i')
    const reverse = new RegExp(`<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${escapedKey}["'][^>]*>`, 'i')
    return (html.match(forward)?.[1] ?? html.match(reverse)?.[1] ?? '')
        .replace(/&amp;/g, '&')
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
}

async function main() {
    const failures = []

    const sitemapRes = await fetchText(sitemapUrl)
    if (!sitemapRes.ok) {
        throw new Error(`sitemap fetch failed: HTTP ${sitemapRes.status}`)
    }

    if (!/<urlset[\s>]/i.test(sitemapRes.text)) {
        throw new Error('sitemap is not a valid urlset xml')
    }

    const allUrls = parseSitemapUrls(sitemapRes.text)
    for (const url of allUrls) {
        assertAllowedSeoUrl(url, pageOrigins, 'sitemap URL')
    }
    const allPostUrls = findPostUrls(allUrls)
    const postUrls = allPostUrls.slice(0, maxUrlChecks)
    const targetUrls = postUrls.length > 0
        ? postUrls
        : [`${siteUrl}/`, `${siteUrl}/boards`]
    const shouldCheckArticleSignals = postUrls.length > 0

    if (postUrls.length === 0) {
        assertPostUrlsPresent(allPostUrls, requirePostUrls)
        console.warn('[seo-verify] no post URLs found in sitemap; running fallback checks on base URLs')
    }

    if (expectedReleaseSha && !/^[0-9a-f]{40}$/.test(expectedReleaseSha)) {
        throw new Error('SEO_EXPECTED_RELEASE_SHA must be a full lowercase commit SHA')
    }
    if (requireReleaseManifest) {
        const manifestRes = await fetchText(releaseManifestUrl)
        if (!manifestRes.ok) {
            throw new Error(`release manifest fetch failed: HTTP ${manifestRes.status}`)
        }
        validateReleaseManifest({
            sitemapText: sitemapRes.text,
            allUrls,
            postUrls: allPostUrls,
            manifestText: manifestRes.text,
            expectedSha: expectedReleaseSha
        })
    }

    for (const url of targetUrls) {
        for (const ua of userAgents) {
            const res = await fetchText(url, ua.value)
            if (!res.ok) {
                failures.push(`[${ua.name}] ${url} returned HTTP ${res.status}`)
                continue
            }

            assertContains(res.text, /<title>[\s\S]*?<\/title>/i, `[${ua.name}] ${url} missing <title>`, failures)
            if (shouldCheckArticleSignals) {
                assertContains(res.text, /rel=["']canonical["']/i, `[${ua.name}] ${url} missing canonical`, failures)
                assertContains(res.text, /application\/ld\+json/i, `[${ua.name}] ${url} missing JSON-LD`, failures)
                assertContains(res.text, /(?:property|name)=["']og:image["']/i, `[${ua.name}] ${url} missing og:image`, failures)
                assertContains(res.text, /(?:property|name)=["']og:image:alt["']/i, `[${ua.name}] ${url} missing og:image:alt`, failures)
                assertContains(res.text, /name=["']twitter:card["'][^>]+content=["']summary_large_image["']/i,
                    `[${ua.name}] ${url} missing twitter:card`, failures)
                assertContains(res.text, /name=["']twitter:image["']/i, `[${ua.name}] ${url} missing twitter:image`, failures)

                const imageUrl = extractMetaContent(res.text, 'og:image')
                let parsedImageUrl
                try {
                    parsedImageUrl = new URL(imageUrl)
                } catch {
                    failures.push(`[${ua.name}] ${url} has a non-absolute og:image URL`)
                }
                if (parsedImageUrl) {
                    const image = await fetchImageMetadata(parsedImageUrl.toString(), ua.value)
                    if (!image.ok) {
                        failures.push(`[${ua.name}] ${url} og:image returned HTTP ${image.status}`)
                    } else if (!image.contentType.toLowerCase().startsWith('image/')) {
                        failures.push(`[${ua.name}] ${url} og:image returned non-image MIME ${image.contentType || '(missing)'}`)
                    }
                }
            }
        }
    }

    if (failures.length > 0) {
        console.error('[seo-verify] failures found:')
        for (const failure of failures) {
            console.error(`- ${failure}`)
        }
        process.exitCode = 1
        return
    }

    console.log(`[seo-verify] OK (${targetUrls.length} URLs checked with ${userAgents.length} user agents)`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main().catch((error) => {
        console.error(`[seo-verify] failed: ${String(error)}`)
        process.exitCode = 1
    })
}
