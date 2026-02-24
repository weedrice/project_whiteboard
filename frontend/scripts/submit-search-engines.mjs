#!/usr/bin/env node

const siteUrl = normalizeBaseUrl(process.env.SEO_SITE_URL ?? 'https://noviis.kr')
const sitemapUrl = process.env.SEO_SITEMAP_URL ?? `${siteUrl}/sitemap.xml`
const googleSiteUrl = normalizeSearchConsoleSiteUrl(process.env.GOOGLE_SEARCH_CONSOLE_SITE_URL ?? siteUrl)
const requestTimeoutMs = parsePositiveInt(process.env.SEO_SUBMIT_TIMEOUT_MS, 15000)

const googleAccessTokenFromEnv = process.env.GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN ?? ''
const googleClientId = process.env.GOOGLE_SEARCH_CONSOLE_CLIENT_ID ?? ''
const googleClientSecret = process.env.GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET ?? ''
const googleRefreshToken = process.env.GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN ?? ''

function normalizeBaseUrl(url) {
    return String(url).replace(/\/+$/, '')
}

function parsePositiveInt(value, fallback) {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function normalizeSearchConsoleSiteUrl(url) {
    const normalized = normalizeBaseUrl(url)
    return normalized.endsWith('/') ? normalized : `${normalized}/`
}

function isFilled(value) {
    return String(value ?? '').trim().length > 0
}

function resolveTargetUrl(rawUrl) {
    return String(rawUrl ?? '')
        .replaceAll('{sitemap}', encodeURIComponent(sitemapUrl))
        .replaceAll('{sitemapRaw}', sitemapUrl)
}

async function fetchGoogleAccessToken() {
    if (isFilled(googleAccessTokenFromEnv)) {
        return googleAccessTokenFromEnv.trim()
    }

    if (!isFilled(googleClientId) || !isFilled(googleClientSecret) || !isFilled(googleRefreshToken)) {
        return ''
    }

    const body = new URLSearchParams({
        client_id: googleClientId,
        client_secret: googleClientSecret,
        refresh_token: googleRefreshToken,
        grant_type: 'refresh_token'
    })

    const response = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
        },
        body,
        signal: AbortSignal.timeout(requestTimeoutMs)
    })

    if (!response.ok) {
        const errorText = await response.text()
        throw new Error(`google oauth token fetch failed: HTTP ${response.status}${errorText ? ` - ${errorText.slice(0, 200)}` : ''}`)
    }

    const payload = await response.json()
    const accessToken = payload?.access_token
    if (!isFilled(accessToken)) {
        throw new Error('google oauth token fetch failed: access_token missing in response')
    }
    return accessToken
}

async function submitTarget(target) {
    const method = target.request?.method ?? 'GET'
    const url = resolveTargetUrl(target.request?.url ?? '')
    const extraHeaders = target.request?.headers ?? {}
    const response = await fetch(url, {
        method,
        headers: {
            'User-Agent': 'noviis-seo-submit/1.0',
            Accept: '*/*',
            ...extraHeaders
        },
        signal: AbortSignal.timeout(requestTimeoutMs)
    })

    if (!response.ok) {
        const body = await response.text()
        throw new Error(`HTTP ${response.status} ${response.statusText}${body ? ` - ${body.slice(0, 200)}` : ''}`)
    }
}

async function main() {
    const googleAccessToken = await fetchGoogleAccessToken()
    const submitTargets = [
        {
            name: 'google-search-console-api',
            enabled: isFilled(googleAccessToken),
            request: {
                method: 'PUT',
                url: `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(googleSiteUrl)}/sitemaps/${encodeURIComponent(sitemapUrl)}`,
                headers: {
                    Authorization: `Bearer ${googleAccessToken}`
                }
            }
        },
        {
            name: 'custom-submit',
            enabled: Boolean(process.env.CUSTOM_SITEMAP_SUBMIT_URL),
            request: {
                method: process.env.CUSTOM_SITEMAP_METHOD ?? 'GET',
                url: process.env.CUSTOM_SITEMAP_SUBMIT_URL ?? ''
            }
        }
    ]

    const activeTargets = submitTargets.filter((target) => target.enabled && target.request?.url)
    if (activeTargets.length === 0) {
        console.log('[seo-submit] no submit target configured; skipping')
        return
    }

    let failed = 0
    for (const target of activeTargets) {
        try {
            await submitTarget(target)
            console.log(`[seo-submit] submitted: ${target.name}`)
        } catch (error) {
            failed += 1
            console.error(`[seo-submit] failed: ${target.name} (${String(error)})`)
        }
    }

    if (failed > 0) {
        process.exitCode = 1
    }
}

main().catch((error) => {
    console.error(`[seo-submit] fatal: ${String(error)}`)
    process.exitCode = 1
})
