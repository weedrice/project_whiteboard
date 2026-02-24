#!/usr/bin/env node

import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'

const siteUrl = normalizeBaseUrl(process.env.PRERENDER_SITE_URL ?? process.env.SITEMAP_SITE_URL ?? 'https://noviis.kr')
const apiBaseUrl = normalizeBaseUrl(process.env.PRERENDER_API_BASE_URL ?? process.env.SITEMAP_API_BASE_URL ?? `${siteUrl}/api/v1`)
const distDir = resolve(process.cwd(), 'dist')
const distIndexPath = resolve(distDir, 'index.html')
const sourceSitemapPath = resolve(process.cwd(), 'public', 'sitemap.xml')
const maxUrls = parsePositiveInt(process.env.PRERENDER_MAX_URLS, 2000)
const requestTimeoutMs = parsePositiveInt(process.env.PRERENDER_REQUEST_TIMEOUT_MS, 15000)

function normalizeBaseUrl(url) {
    return String(url).replace(/\/+$/, '')
}

function parsePositiveInt(value, fallback) {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function stripHtml(html) {
    return String(html ?? '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

async function fetchJson(path) {
    const url = `${apiBaseUrl}${path}`
    const response = await fetch(url, {
        headers: {
            Accept: 'application/json',
            'User-Agent': 'noviis-prerender/1.0'
        },
        signal: AbortSignal.timeout(requestTimeoutMs)
    })

    if (!response.ok) {
        throw new Error(`HTTP ${response.status} while fetching ${url}`)
    }

    const payload = await response.json()
    if (payload && typeof payload === 'object' && 'success' in payload) {
        if (!payload.success) {
            throw new Error(`API success=false: ${url}`)
        }
        return payload.data
    }
    return payload
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
        results.push({ boardUrl, postId, path: `/board/${boardUrl}/post/${postId}` })
        if (results.length >= maxUrls) break
    }

    return results
}

function buildPreRenderedSnippet(post, canonicalUrl) {
    const title = post?.title ?? 'Post'
    const authorName = post?.author?.displayName ?? 'Unknown'
    const createdAt = post?.createdAt ? new Date(post.createdAt).toISOString() : null
    const articleBody = post?.contents ?? ''
    const articleSummary = stripHtml(articleBody).slice(0, 240)

    const ldJson = JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: title,
        datePublished: post?.createdAt ?? null,
        dateModified: post?.modifiedAt ?? post?.createdAt ?? null,
        author: { '@type': 'Person', name: authorName },
        mainEntityOfPage: canonicalUrl,
        url: canonicalUrl
    }).replace(/</g, '\\u003c')

    return {
        title,
        description: articleSummary || 'Post content',
        extraHead: [
            `<link rel="canonical" href="${escapeHtml(canonicalUrl)}">`,
            `<meta property="og:title" content="${escapeHtml(`${title} | 노비스`)}">`,
            `<meta property="og:description" content="${escapeHtml(articleSummary || 'Post content')}">`,
            `<meta property="og:type" content="article">`,
            `<meta property="og:url" content="${escapeHtml(canonicalUrl)}">`,
            `<script type="application/ld+json">${ldJson}</script>`
        ].join('\n    '),
        body: `
<article data-prerendered="true" style="max-width:760px;margin:0 auto;padding:24px 16px;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.6;color:#111827;">
  <h1 style="font-size:1.75rem;font-weight:700;margin:0 0 12px;">${escapeHtml(title)}</h1>
  <p style="font-size:0.875rem;color:#6b7280;margin:0 0 20px;">${escapeHtml(authorName)}${createdAt ? ` · <time datetime="${createdAt}">${escapeHtml(new Date(createdAt).toLocaleString('ko-KR'))}</time>` : ''}</p>
  <section class="post-prerender-body">${articleBody}</section>
</article>`.trim()
    }
}

function injectIntoTemplate(indexHtml, renderData) {
    let html = indexHtml

    html = html.replace(/<title>.*?<\/title>/i, `<title>${escapeHtml(renderData.title)}</title>`)

    if (/<meta\s+name=["']description["']\s+content=["'][^"']*["']\s*\/?>/i.test(html)) {
        html = html.replace(
            /<meta\s+name=["']description["']\s+content=["'][^"']*["']\s*\/?>/i,
            `<meta name="description" content="${escapeHtml(renderData.description)}">`
        )
    } else {
        html = html.replace('</head>', `    <meta name="description" content="${escapeHtml(renderData.description)}">\n</head>`)
    }

    html = html.replace('</head>', `    ${renderData.extraHead}\n</head>`)
    html = html.replace('<div id="app"></div>', `<div id="app">${renderData.body}</div>`)

    return html
}

async function main() {
    const [indexHtml, sitemapXml] = await Promise.all([
        readFile(distIndexPath, 'utf8'),
        readFile(sourceSitemapPath, 'utf8')
    ])

    const postPaths = extractPostPathsFromSitemap(sitemapXml)
    if (postPaths.length === 0) {
        console.log('[prerender] no post URLs found in sitemap; skipping')
        return
    }

    let successCount = 0
    for (const target of postPaths) {
        try {
            const post = await fetchJson(`/posts/${target.postId}?incrementView=false`)
            const canonicalUrl = `${siteUrl}${target.path}`
            const renderData = buildPreRenderedSnippet(post, canonicalUrl)
            const html = injectIntoTemplate(indexHtml, renderData)
            const outputPath = resolve(distDir, target.path.replace(/^\//, ''), 'index.html')

            await mkdir(dirname(outputPath), { recursive: true })
            await writeFile(outputPath, html, 'utf8')
            successCount += 1
        } catch (error) {
            console.warn(`[prerender] failed for ${target.path}: ${String(error)}`)
        }
    }

    console.log(`[prerender] wrote ${successCount}/${postPaths.length} pre-rendered post pages`)
}

main().catch((error) => {
    console.error(`[prerender] failed: ${String(error)}`)
    process.exitCode = 1
})
