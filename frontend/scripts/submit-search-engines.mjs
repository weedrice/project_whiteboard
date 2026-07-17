#!/usr/bin/env node

import { lookup } from 'node:dns/promises'
import { request as httpsRequest } from 'node:https'
import { isIP } from 'node:net'
import { pathToFileURL } from 'node:url'

const siteUrl = normalizeBaseUrl(process.env.SEO_SITE_URL ?? 'https://noviis.kr')
const sitemapUrl = process.env.SEO_SITEMAP_URL ?? `${siteUrl}/sitemap.xml`
const googleSiteUrl = normalizeSearchConsoleSiteUrl(process.env.GOOGLE_SEARCH_CONSOLE_SITE_URL ?? siteUrl)
const requestTimeoutMs = parsePositiveInt(process.env.SEO_SUBMIT_TIMEOUT_MS, 15000)

const googleAccessTokenFromEnv = process.env.GOOGLE_SEARCH_CONSOLE_ACCESS_TOKEN ?? ''
const googleClientId = process.env.GOOGLE_SEARCH_CONSOLE_CLIENT_ID ?? ''
const googleClientSecret = process.env.GOOGLE_SEARCH_CONSOLE_CLIENT_SECRET ?? ''
const googleRefreshToken = process.env.GOOGLE_SEARCH_CONSOLE_REFRESH_TOKEN ?? ''

export function normalizeBaseUrl(url) {
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

function isPrivateIpv4(address) {
    const octets = address.split('.').map(Number)
    if (octets.length !== 4 || octets.some((octet) => !Number.isInteger(octet) || octet < 0 || octet > 255)) {
        return true
    }
    const [first, second] = octets
    return first === 0
        || first === 10
        || first === 127
        || (first === 169 && second === 254)
        || (first === 172 && second >= 16 && second <= 31)
        || (first === 192 && second === 168)
        || (first === 192 && second === 0)
        || (first === 192 && second === 2)
        || (first === 198 && (second === 18 || second === 19 || second === 51))
        || (first === 203 && second === 0)
        || (first === 100 && second >= 64 && second <= 127)
        || first >= 224
}

export function isPrivateAddress(address) {
    const normalized = String(address).trim().toLowerCase()
    const family = isIP(normalized)
    if (family === 4) return isPrivateIpv4(normalized)
    if (family !== 6) return true
    if (normalized === '::' || normalized === '::1') return true
    if (normalized.startsWith('fc') || normalized.startsWith('fd') || /^fe[89ab]/.test(normalized)) return true
    if (normalized.startsWith('ff') || normalized.startsWith('2001:db8')) return true
    const mapped = normalized.match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/)
    return mapped ? isPrivateIpv4(mapped[1]) : false
}

export function parseAllowedOrigins(rawOrigins) {
    return new Set(String(rawOrigins ?? '')
        .split(',')
        .map((origin) => origin.trim())
        .filter(Boolean)
        .map((origin) => new URL(origin).origin))
}

export async function validateCustomSubmitUrl(rawUrl, rawAllowedOrigins, resolver = lookup) {
    const resolvedTarget = await resolveCustomSubmitTarget(rawUrl, rawAllowedOrigins, resolver)
    return resolvedTarget.url
}

export async function resolveCustomSubmitTarget(rawUrl, rawAllowedOrigins, resolver = lookup) {
    const target = new URL(resolveTargetUrl(rawUrl))
    if (target.protocol !== 'https:' || target.username || target.password) {
        throw new Error('custom submit URL must be credential-free HTTPS')
    }
    const allowedOrigins = parseAllowedOrigins(rawAllowedOrigins)
    if (!allowedOrigins.has(target.origin)) {
        throw new Error('custom submit URL origin is not allowlisted')
    }
    const resolved = isIP(target.hostname)
        ? [{ address: target.hostname }]
        : await resolver(target.hostname, { all: true, verbatim: true })
    if (resolved.length === 0 || resolved.some(({ address }) => isPrivateAddress(address))) {
        throw new Error('custom submit URL resolves to a private or invalid address')
    }
    const selected = resolved[0]
    return {
        url: target.toString(),
        address: selected.address,
        family: selected.family ?? isIP(selected.address)
    }
}

export function validateGoogleCredentialConfiguration({ accessToken, clientId, clientSecret, refreshToken }) {
    const refreshCredentials = [clientId, clientSecret, refreshToken]
    const configuredRefreshCredentials = refreshCredentials.filter(isFilled).length
    if (configuredRefreshCredentials !== 0 && configuredRefreshCredentials !== refreshCredentials.length) {
        throw new Error('Google Search Console refresh credentials must be configured all-or-none')
    }
    return isFilled(accessToken) || configuredRefreshCredentials === refreshCredentials.length
}

export function httpFailure(provider, response) {
    const statusText = String(response.statusText ?? '').replace(/[\r\n]/g, ' ').slice(0, 80)
    return new Error(`${provider} failed: HTTP ${response.status}${statusText ? ` ${statusText}` : ''}`)
}

async function fetchGoogleAccessToken() {
    if (isFilled(googleAccessTokenFromEnv)) return googleAccessTokenFromEnv.trim()
    if (!isFilled(googleClientId) || !isFilled(googleClientSecret) || !isFilled(googleRefreshToken)) return ''

    const response = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            client_id: googleClientId,
            client_secret: googleClientSecret,
            refresh_token: googleRefreshToken,
            grant_type: 'refresh_token'
        }),
        redirect: 'error',
        signal: AbortSignal.timeout(requestTimeoutMs)
    })
    if (!response.ok) throw httpFailure('google oauth token fetch', response)
    const payload = await response.json()
    if (!isFilled(payload?.access_token)) throw new Error('google oauth token fetch failed: access_token missing')
    return payload.access_token
}

async function submitTarget(target) {
    if (target.pinned) {
        await submitPinnedHttps(target)
        return
    }
    const response = await fetch(target.request.url, {
        method: target.request.method ?? 'GET',
        headers: {
            'User-Agent': 'noviis-seo-submit/1.0',
            Accept: '*/*',
            ...(target.request.headers ?? {})
        },
        redirect: target.name === 'custom-submit' ? 'manual' : 'error',
        signal: AbortSignal.timeout(requestTimeoutMs)
    })
    if (!response.ok) throw httpFailure(target.name, response)
}

function submitPinnedHttps(target) {
    const targetUrl = new URL(target.request.url)
    return new Promise((resolve, reject) => {
        const request = httpsRequest(targetUrl, {
            method: target.request.method ?? 'GET',
            headers: {
                Host: targetUrl.host,
                'User-Agent': 'noviis-seo-submit/1.0',
                Accept: '*/*',
                ...(target.request.headers ?? {})
            },
            servername: isIP(targetUrl.hostname) ? undefined : targetUrl.hostname,
            lookup: (_hostname, _options, callback) => callback(null, target.pinned.address, target.pinned.family)
        }, (response) => {
            response.resume()
            if ((response.statusCode ?? 500) < 200 || (response.statusCode ?? 500) >= 300) {
                reject(httpFailure(target.name, {
                    status: response.statusCode ?? 500,
                    statusText: response.statusMessage ?? ''
                }))
                return
            }
            resolve()
        })
        request.setTimeout(requestTimeoutMs, () => request.destroy(new Error(`${target.name} failed: timeout`)))
        request.on('error', reject)
        request.end()
    })
}

export async function main() {
    const googleConfigured = validateGoogleCredentialConfiguration({
        accessToken: googleAccessTokenFromEnv,
        clientId: googleClientId,
        clientSecret: googleClientSecret,
        refreshToken: googleRefreshToken
    })
    const googleAccessToken = await fetchGoogleAccessToken()
    const submitTargets = []
    if (isFilled(googleAccessToken)) {
        submitTargets.push({
            name: 'google-search-console-api',
            request: {
                method: 'PUT',
                url: `https://www.googleapis.com/webmasters/v3/sites/${encodeURIComponent(googleSiteUrl)}/sitemaps/${encodeURIComponent(sitemapUrl)}`,
                headers: { Authorization: `Bearer ${googleAccessToken}` }
            }
        })
    }
    if (isFilled(process.env.CUSTOM_SITEMAP_SUBMIT_URL)) {
        const customTarget = await resolveCustomSubmitTarget(
            process.env.CUSTOM_SITEMAP_SUBMIT_URL,
            process.env.CUSTOM_SITEMAP_SUBMIT_ALLOWED_ORIGINS
        )
        submitTargets.push({
            name: 'custom-submit',
            pinned: customTarget,
            request: {
                method: process.env.CUSTOM_SITEMAP_METHOD ?? 'GET',
                url: customTarget.url
            }
        })
    }
    if (submitTargets.length === 0) {
        if (process.env.SEO_SUBMIT_REQUIRED === 'true') {
            throw new Error('production sitemap submission requires at least one configured provider')
        }
        console.log('[seo-submit] no submit target configured; skipping')
        return
    }
    if (googleConfigured && !isFilled(googleAccessToken)) {
        throw new Error('Google Search Console credentials did not produce an access token')
    }

    let failed = 0
    for (const target of submitTargets) {
        try {
            await submitTarget(target)
            console.log(`[seo-submit] submitted: ${target.name}`)
        } catch (error) {
            failed += 1
            console.error(`[seo-submit] failed: ${target.name} (${String(error)})`)
        }
    }
    if (failed > 0) process.exitCode = 1
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    main().catch((error) => {
        console.error(`[seo-submit] fatal: ${String(error)}`)
        process.exitCode = 1
    })
}
