#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { readSeoPostUrlCapacity, SITEMAP_PROTOCOL_MAX_URLS } from './seo-capacity.mjs'

const siteUrl = normalizeBaseUrl(process.env.SITEMAP_SITE_URL ?? 'https://noviis.kr')
const apiBaseUrl = normalizeBaseUrl(process.env.SITEMAP_API_BASE_URL ?? `${siteUrl}/api/v1`)
const outputPath = resolve(process.cwd(), 'public', 'sitemap.xml')
const pageSize = parsePositiveInt(process.env.SITEMAP_PAGE_SIZE, 100)
const maxPagesPerBoard = parsePositiveInt(process.env.SITEMAP_MAX_PAGES_PER_BOARD, 200)
const requestTimeoutMs = parsePositiveInt(process.env.SITEMAP_REQUEST_TIMEOUT_MS, 15000)
const strict = process.env.SEO_STRICT === 'true'
const postUrlCapacity = readSeoPostUrlCapacity()

function normalizeBaseUrl(url) {
    return String(url).replace(/\/+$/, '')
}

function parsePositiveInt(value, fallback) {
    const parsed = Number.parseInt(String(value ?? ''), 10)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

function escapeXml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&apos;')
}

function toLastmod(value) {
    if (!value) return null
    const date = new Date(value)
    if (Number.isNaN(date.valueOf())) return null
    return date.toISOString().slice(0, 10)
}

function toSortTimestamp(value) {
    const timestamp = Date.parse(value ?? '')
    return Number.isFinite(timestamp) ? timestamp : 0
}

async function fetchApiData(path) {
    const url = `${apiBaseUrl}${path}`
    const response = await fetch(url, {
        headers: {
            Accept: 'application/json',
            'User-Agent': 'noviis-sitemap-generator/1.0'
        },
        signal: AbortSignal.timeout(requestTimeoutMs)
    })

    if (!response.ok) {
        throw new Error(`HTTP ${response.status} ${response.statusText}`)
    }

    const payload = await response.json()
    if (payload && typeof payload === 'object' && 'success' in payload) {
        if (!payload.success) {
            throw new Error(`API responded with success=false for ${path}`)
        }
        return payload.data
    }
    return payload
}

function createEntry(loc, options = {}) {
    return {
        loc,
        lastmod: options.lastmod ?? null,
        sortTimestamp: options.sortTimestamp ?? 0,
        changefreq: options.changefreq ?? 'daily',
        priority: options.priority ?? '0.8'
    }
}

function toBoardUrl(boardUrl) {
    return `${siteUrl}/board/${encodeURIComponent(boardUrl)}/`
}

function toPostUrl(boardUrl, postId) {
    return `${siteUrl}/board/${encodeURIComponent(boardUrl)}/post/${postId}/`
}

function upsertEntry(entryMap, nextEntry) {
    const current = entryMap.get(nextEntry.loc)
    if (!current) {
        entryMap.set(nextEntry.loc, nextEntry)
        return
    }

    if (current.lastmod && nextEntry.lastmod) {
        entryMap.set(nextEntry.loc, current.lastmod >= nextEntry.lastmod ? current : nextEntry)
        return
    }

    if (!current.lastmod && nextEntry.lastmod) {
        entryMap.set(nextEntry.loc, nextEntry)
    }
}

async function fetchBoards() {
    const data = await fetchApiData('/boards')
    if (!Array.isArray(data)) return []
    return data
        .filter((board) => board && typeof board.boardUrl === 'string')
        .filter((board) => board.isActive !== false)
}

async function fetchBoardPosts(boardUrl) {
    const postEntries = []
    const encodedBoardUrl = encodeURIComponent(boardUrl)
    const firstPageParams = new URLSearchParams({
        page: '0',
        size: String(pageSize),
        sort: 'createdAt,desc'
    })

    const firstPage = await fetchApiData(`/boards/${encodedBoardUrl}/posts?${firstPageParams.toString()}`)
    const totalPages = Number.isFinite(firstPage?.totalPages) ? Number(firstPage.totalPages) : 0
    const pageCount = Math.min(totalPages, maxPagesPerBoard)

    const consumePage = (pageData) => {
        const posts = Array.isArray(pageData?.content) ? pageData.content : []
        for (const post of posts) {
            if (!post || post.postId == null) continue
            postEntries.push(createEntry(
                toPostUrl(boardUrl, post.postId),
                {
                    lastmod: toLastmod(post.modifiedAt || post.createdAt),
                    sortTimestamp: toSortTimestamp(post.modifiedAt || post.createdAt),
                    changefreq: 'weekly',
                    priority: '0.7'
                }
            ))
        }
    }

    consumePage(firstPage)

    for (let page = 1; page < pageCount; page += 1) {
        const pageParams = new URLSearchParams({
            page: String(page),
            size: String(pageSize),
            sort: 'createdAt,desc'
        })
        const pageData = await fetchApiData(`/boards/${encodedBoardUrl}/posts?${pageParams.toString()}`)
        consumePage(pageData)
    }

    return {
        entries: postEntries,
        discoveredCount: Number.isSafeInteger(firstPage?.totalElements)
            ? Number(firstPage.totalElements)
            : postEntries.length
    }
}

function renderSitemap(entries) {
    const urls = [...entries.values()].sort((a, b) => a.loc.localeCompare(b.loc))
    const body = urls.map((entry) => {
        const lines = [
            '  <url>',
            `    <loc>${escapeXml(entry.loc)}</loc>`,
        ]

        if (entry.lastmod) {
            lines.push(`    <lastmod>${entry.lastmod}</lastmod>`)
        }
        if (entry.changefreq) {
            lines.push(`    <changefreq>${entry.changefreq}</changefreq>`)
        }
        if (entry.priority) {
            lines.push(`    <priority>${entry.priority}</priority>`)
        }

        lines.push('  </url>')
        return lines.join('\n')
    }).join('\n')

    return `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${body}\n</urlset>\n`
}

async function main() {
    const entries = new Map()
    let postCount = 0
    let discoveredPostCount = 0
    const discoveredPostEntries = []

    upsertEntry(entries, createEntry(`${siteUrl}/`, { changefreq: 'daily', priority: '1.0' }))
    upsertEntry(entries, createEntry(`${siteUrl}/boards`, { changefreq: 'daily', priority: '0.9' }))

    try {
        const boards = await fetchBoards()
        console.log(`[sitemap] fetched ${boards.length} boards`)

        for (const board of boards) {
            const boardUrl = board.boardUrl
            upsertEntry(entries, createEntry(
                toBoardUrl(boardUrl),
                {
                    lastmod: toLastmod(board.modifiedAt || board.createdAt),
                    changefreq: 'daily',
                    priority: '0.8'
                }
            ))

            try {
                const posts = await fetchBoardPosts(boardUrl)
                discoveredPostCount += posts.discoveredCount
                discoveredPostEntries.push(...posts.entries)
            } catch (error) {
                if (strict) throw error
                console.warn(`[sitemap] failed to fetch posts for board "${boardUrl}": ${String(error)}`)
            }
        }
    } catch (error) {
        if (strict) throw error
        console.warn(`[sitemap] failed to fetch boards: ${String(error)}`)
        console.warn('[sitemap] falling back to base URLs only')
    }

    const uniquePosts = new Map()
    for (const postEntry of discoveredPostEntries) upsertEntry(uniquePosts, postEntry)
    const selectedPosts = [...uniquePosts.values()]
        .sort((left, right) => right.sortTimestamp - left.sortTimestamp || left.loc.localeCompare(right.loc))
        .slice(0, postUrlCapacity)
    for (const postEntry of selectedPosts) upsertEntry(entries, postEntry)
    postCount = selectedPosts.length

    if (strict && postCount === 0) {
        throw new Error('strict production sitemap contains no post URLs')
    }
    if (entries.size > SITEMAP_PROTOCOL_MAX_URLS) {
        throw new Error(`sitemap URL count exceeds protocol limit: ${entries.size}/${SITEMAP_PROTOCOL_MAX_URLS}`)
    }

    await mkdir(dirname(outputPath), { recursive: true })
    await writeFile(outputPath, renderSitemap(entries), 'utf8')
    const omittedPostCount = Math.max(0, discoveredPostCount - postCount)
    console.log(`[sitemap] wrote ${entries.size} URLs (${postCount}/${discoveredPostCount} posts, capacity=${postUrlCapacity}, omitted=${omittedPostCount}) to ${outputPath}`)
}

main().catch((error) => {
    console.error(`[sitemap] generation failed: ${String(error)}`)
    process.exitCode = 1
})
