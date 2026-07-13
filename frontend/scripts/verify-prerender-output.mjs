#!/usr/bin/env node

import { readFile, readdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { OG_IMAGE_HEIGHT, OG_IMAGE_WIDTH } from './og-image.mjs'

const distDir = resolve(process.cwd(), 'dist')

function escapeRegExp(value) {
    return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

export function extractMetaContent(html, key) {
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

export function validatePng(png) {
    const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
    if (png.length < 24 || !png.subarray(0, 8).equals(signature)) {
        throw new Error('invalid PNG signature')
    }
    const width = png.readUInt32BE(16)
    const height = png.readUInt32BE(20)
    if (width !== OG_IMAGE_WIDTH || height !== OG_IMAGE_HEIGHT) {
        throw new Error(`invalid PNG dimensions ${width}x${height}`)
    }
}

export function validatePrerenderHtml(html) {
    const required = ['og:image', 'og:image:alt', 'twitter:card', 'twitter:image']
    for (const key of required) {
        if (!extractMetaContent(html, key)) throw new Error(`missing ${key}`)
    }
    const imageUrl = extractMetaContent(html, 'og:image')
    const parsed = new URL(imageUrl)
    if (!['http:', 'https:'].includes(parsed.protocol)) throw new Error('og:image must be absolute HTTP(S)')
    if (extractMetaContent(html, 'twitter:card') !== 'summary_large_image') {
        throw new Error('twitter:card must be summary_large_image')
    }
    return parsed
}

async function main() {
    const entries = await readdir(distDir, { recursive: true })
    const postIndexes = entries.filter((entry) => /board[\\/][^\\/]+[\\/]post[\\/]\d+[\\/]index\.html$/.test(entry))
    if (postIndexes.length === 0) {
        console.warn('[prerender-verify] no pre-rendered post HTML found; skipping')
        return
    }

    for (const entry of postIndexes) {
        const html = await readFile(resolve(distDir, entry), 'utf8')
        const imageUrl = validatePrerenderHtml(html)
        if (imageUrl.pathname.startsWith('/img/og/')) {
            const png = await readFile(resolve(distDir, imageUrl.pathname.replace(/^\//, '')))
            validatePng(png)
        }
    }

    console.log(`[prerender-verify] OK (${postIndexes.length} post pages)`)
}

if (process.argv[1]?.endsWith('verify-prerender-output.mjs')) {
    main().catch((error) => {
        console.error(`[prerender-verify] failed: ${String(error)}`)
        process.exitCode = 1
    })
}
