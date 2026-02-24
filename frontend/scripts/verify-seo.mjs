#!/usr/bin/env node

const siteUrl = normalizeBaseUrl(process.env.SEO_SITE_URL ?? 'https://noviis.kr')
const sitemapUrl = process.env.SEO_SITEMAP_URL ?? `${siteUrl}/sitemap.xml`
const requestTimeoutMs = parsePositiveInt(process.env.SEO_VERIFY_TIMEOUT_MS, 15000)
const maxUrlChecks = parsePositiveInt(process.env.SEO_VERIFY_MAX_URLS, 10)
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
    const postUrls = findPostUrls(allUrls).slice(0, maxUrlChecks)
    const targetUrls = postUrls.length > 0
        ? postUrls
        : [`${siteUrl}/`, `${siteUrl}/boards`]
    const shouldCheckArticleSignals = postUrls.length > 0

    if (postUrls.length === 0) {
        console.warn('[seo-verify] no post URLs found in sitemap; running fallback checks on base URLs')
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

main().catch((error) => {
    console.error(`[seo-verify] failed: ${String(error)}`)
    process.exitCode = 1
})
