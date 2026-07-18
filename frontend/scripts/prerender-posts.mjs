#!/usr/bin/env node

import { access, mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { resolvePostOgImage } from './og-image.mjs'
import { buildPreRenderedSnippet, injectIntoTemplate } from './prerender-html.mjs'
import { assertSeoPostUrlCountWithinCapacity, readSeoPostUrlCapacity } from './seo-capacity.mjs'

const siteUrl = normalizeBaseUrl(process.env.PRERENDER_SITE_URL ?? process.env.SITEMAP_SITE_URL ?? 'https://noviis.kr')
const apiBaseUrl = normalizeBaseUrl(process.env.PRERENDER_API_BASE_URL ?? process.env.SITEMAP_API_BASE_URL ?? `${siteUrl}/api/v1`)
const distDir = resolve(process.cwd(), 'dist')
const distIndexPath = resolve(distDir, 'index.html')
const sourceSitemapPath = resolve(process.cwd(), 'public', 'sitemap.xml')
const maxUrls = readSeoPostUrlCapacity()
const requestTimeoutMs = parsePositiveInt(process.env.PRERENDER_REQUEST_TIMEOUT_MS, 15000)
const fetchRetries = parseNonNegativeInt(process.env.PRERENDER_FETCH_RETRIES, 3)
const fetchRetryDelayMs = parsePositiveInt(process.env.PRERENDER_FETCH_RETRY_DELAY_MS, 1000)
const retryableStatuses = new Set([408, 425, 429, 500, 502, 503, 504])
const strict = process.env.SEO_STRICT === 'true'

function normalizeBaseUrl(url) {
    return String(url).replace(/\/+$/, '')
}

function parsePositiveInt(value, fallback) {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function parseNonNegativeInt(value, fallback) {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    return Number.isFinite(parsed) && parsed >= 0 ? parsed : fallback
}

function delay(ms) {
    return new Promise((resolveDelay) => {
        setTimeout(resolveDelay, ms)
    })
}

function shouldRetryFetch(error) {
    if (error instanceof HttpError) {
        return retryableStatuses.has(error.status)
    }

    return error instanceof TypeError || error?.name === 'TimeoutError' || error?.name === 'AbortError'
}

class HttpError extends Error {
    constructor(status, url) {
        super(`HTTP ${status} while fetching ${url}`)
        this.name = 'HttpError'
        this.status = status
    }
}

function ensureTrailingSlashPath(path) {
    const normalized = String(path)
    return normalized.endsWith('/') ? normalized : `${normalized}/`
}

async function fetchJson(path) {
    const url = `${apiBaseUrl}${path}`

    for (let attempt = 0; attempt <= fetchRetries; attempt += 1) {
        try {
            const response = await fetch(url, {
                headers: {
                    Accept: 'application/json',
                    'User-Agent': 'noviis-prerender/1.0'
                },
                signal: AbortSignal.timeout(requestTimeoutMs)
            })

            if (!response.ok) {
                throw new HttpError(response.status, url)
            }

            const payload = await response.json()
            if (payload && typeof payload === 'object' && 'success' in payload) {
                if (!payload.success) {
                    throw new Error(`API success=false: ${url}`)
                }
                return payload.data
            }
            return payload
        } catch (error) {
            if (attempt >= fetchRetries || !shouldRetryFetch(error)) {
                throw error
            }

            const retryDelay = fetchRetryDelayMs * (attempt + 1)
            console.warn(`[prerender] retrying ${url} after ${String(error)} (${attempt + 1}/${fetchRetries})`)
            await delay(retryDelay)
        }
    }
}

function extractPostPathsFromSitemap(xmlText) {
    const matches = [...xmlText.matchAll(/<loc>(.*?)<\/loc>/g)].map((match) => match[1].trim())
    const seen = new Set()
    const results = []

    for (const loc of matches) {
        let url
        try {
            url = new URL(loc)
        } catch {
            continue
        }

        const path = decodeURI(url.pathname)
        const postMatch = path.match(/^\/board\/([^/]+)\/post\/(\d+)\/?$/)
        if (!postMatch) continue

        const boardUrl = postMatch[1]
        const postId = postMatch[2]
        const key = `${boardUrl}:${postId}`
        if (seen.has(key)) continue

        seen.add(key)
        results.push({ boardUrl, postId, path: `/board/${boardUrl}/post/${postId}/` })
    }

    return results
}

async function writeIfMissing(outputPath, html) {
    try {
        await access(outputPath)
        return false
    } catch {
        await mkdir(dirname(outputPath), { recursive: true })
        await writeFile(outputPath, html, 'utf8')
        return true
    }
}

function collectParentDirectoryPaths(postPath) {
    const normalized = ensureTrailingSlashPath(postPath).replace(/^\/|\/$/g, '')
    const segments = normalized.split('/').filter(Boolean)
    const parentPaths = []

    for (let i = 1; i < segments.length; i += 1) {
        parentPaths.push(`/${segments.slice(0, i).join('/')}/`)
    }

    return parentPaths
}

async function ensureSpaFallbackIndexes(indexHtml, postPaths) {
    const directoryPaths = new Set()

    for (const target of postPaths) {
        for (const path of collectParentDirectoryPaths(target.path)) {
            directoryPaths.add(path)
        }
    }

    let createdCount = 0
    for (const path of directoryPaths) {
        const outputPath = resolve(distDir, path.replace(/^\//, ''), 'index.html')
        if (await writeIfMissing(outputPath, indexHtml)) {
            createdCount += 1
        }
    }

    return createdCount
}

async function main() {
    const [indexHtml, sitemapXml] = await Promise.all([
        readFile(distIndexPath, 'utf8'),
        readFile(sourceSitemapPath, 'utf8')
    ])

    const postPaths = extractPostPathsFromSitemap(sitemapXml)
    assertSeoPostUrlCountWithinCapacity(postPaths.length, maxUrls)
    if (postPaths.length === 0) {
        if (strict) throw new Error('strict production prerender requires at least one post URL')
        console.log('[prerender] no post URLs found in sitemap; skipping')
        return
    }

    let successCount = 0
    const failedTargets = []
    for (const target of postPaths) {
        try {
            const post = await fetchJson(`/posts/${target.postId}?incrementView=false`)
            const canonicalUrl = `${siteUrl}${ensureTrailingSlashPath(target.path)}`
            const ogImage = await resolvePostOgImage(post, { siteUrl, distDir })
            const renderData = buildPreRenderedSnippet(post, canonicalUrl, ogImage)
            const html = injectIntoTemplate(indexHtml, renderData)
            const outputPath = resolve(distDir, target.path.replace(/^\//, ''), 'index.html')

            await mkdir(dirname(outputPath), { recursive: true })
            await writeFile(outputPath, html, 'utf8')
            successCount += 1
        } catch (error) {
            failedTargets.push(target.path)
            console.warn(`[prerender] failed for ${target.path}: ${String(error)}`)
        }
    }

    const fallbackIndexCount = await ensureSpaFallbackIndexes(indexHtml, postPaths)
    console.log(`[prerender] wrote ${fallbackIndexCount} SPA fallback index files for parent directories`)
    console.log(`[prerender] wrote ${successCount}/${postPaths.length} pre-rendered post pages`)

    if (failedTargets.length > 0) {
        console.error(`[prerender] failed to render ${failedTargets.length}/${postPaths.length} post pages`)
        for (const path of failedTargets.slice(0, 20)) {
            console.error(`[prerender] missing pre-rendered page: ${path}`)
        }
        if (failedTargets.length > 20) {
            console.error(`[prerender] ...and ${failedTargets.length - 20} more`)
        }
        process.exitCode = 1
    }
}

if (process.argv[1]?.endsWith('prerender-posts.mjs')) {
    main().catch((error) => {
        console.error(`[prerender] failed: ${String(error)}`)
        process.exitCode = 1
    })
}
