#!/usr/bin/env node

import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'

const siteUrl = normalizeBaseUrl(process.env.SEO_SITE_URL ?? 'https://noviis.kr')
const sitemapUrl = process.env.SEO_SITEMAP_URL ?? `${siteUrl}/sitemap.xml`
const requestTimeoutMs = parsePositiveInt(process.env.SEO_VERIFY_TIMEOUT_MS, 15000)
const maxUrlChecks = parsePositiveInt(process.env.SEO_VERIFY_MAX_URLS, 10)
const requirePostUrls = process.env.SEO_REQUIRE_POST_URLS === 'true'
const requireReleaseManifest = process.env.SEO_REQUIRE_RELEASE_MANIFEST === 'true'
const releaseManifestUrl = process.env.SEO_RELEASE_MANIFEST_URL ?? `${siteUrl}/.noviis-seo-release.json`
const expectedReleaseSha = String(process.env.SEO_EXPECTED_RELEASE_SHA ?? '').trim()
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

async function fetchText(url, userAgent) {
    const response = await fetch(url, {
        headers: userAgent ? { 'User-Agent': userAgent } : undefined,
        signal: AbortSignal.timeout(requestTimeoutMs)
    })
    return { ok: response.ok, status: response.status, text: await response.text() }
}

async function fetchImageMetadata(url, userAgent) {
    const response = await fetch(url, {
        headers: userAgent ? { 'User-Agent': userAgent } : undefined,
        signal: AbortSignal.timeout(requestTimeoutMs)
    })
    await response.body?.cancel()
    return {
        ok: response.ok,
        status: response.status,
        contentType: response.headers.get('content-type') ?? ''
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
