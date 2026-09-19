import { readFile } from 'node:fs/promises'
import { resolve } from 'node:path'
import { extractMetaContent } from './html-meta.mjs'
import { OG_IMAGE_HEIGHT, OG_IMAGE_WIDTH } from './og-image.mjs'

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

export async function verifyPrerenderPages(distDir, postIndexes, readContent = readFile) {
    const validatedImagePaths = new Set()
    for (const entry of postIndexes) {
        const html = await readContent(resolve(distDir, entry), 'utf8')
        const imageUrl = validatePrerenderHtml(html)
        if (imageUrl.pathname.startsWith('/img/og/')) {
            const imagePath = resolve(distDir, imageUrl.pathname.replace(/^\//, ''))
            if (!validatedImagePaths.has(imagePath)) {
                const png = await readContent(imagePath)
                validatePng(png)
                validatedImagePaths.add(imagePath)
            }
        }
    }
}
