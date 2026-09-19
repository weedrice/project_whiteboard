import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { verifyPrerenderPages } from '../prerender-output-validation.mjs'

const readFile = vi.fn()
const distDir = resolve(process.cwd(), 'dist')
const firstPost = 'board/free/post/1/index.html'
const secondPost = 'board/free/post/2/index.html'
const imagePath = '/img/og/community-brand-v2.png'

function postHtml(path = imagePath) {
    return `<meta property="og:image" content="https://noviis.kr${path}">
        <meta property="og:image:alt" content="공유 이미지">
        <meta name="twitter:card" content="summary_large_image">
        <meta name="twitter:image" content="https://noviis.kr${path}">`
}

function validPng() {
    const png = Buffer.alloc(24)
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(png)
    png.writeUInt32BE(1200, 16)
    png.writeUInt32BE(630, 20)
    return png
}

function provideFiles(entries) {
    const files = new Map(entries.map(([path, value]) => [resolve(distDir, path), value]))
    readFile.mockImplementation(async (path) => {
        if (!files.has(path)) throw new Error(`Missing test file: ${path}`)
        return files.get(path)
    })
}

beforeEach(() => {
    vi.clearAllMocks()
})

afterEach(() => {
    vi.restoreAllMocks()
})

describe('prerender image verification', () => {
    it('reads a shared PNG once while reading every post HTML', async () => {
        provideFiles([
            [firstPost, postHtml()],
            [secondPost, postHtml()],
            [imagePath.slice(1), validPng()],
        ])

        await verifyPrerenderPages(distDir, [firstPost, secondPost], readFile)

        expect(readFile).toHaveBeenCalledTimes(3)
        expect(readFile).toHaveBeenCalledWith(resolve(distDir, firstPost), 'utf8')
        expect(readFile).toHaveBeenCalledWith(resolve(distDir, secondPost), 'utf8')
        expect(readFile).toHaveBeenCalledWith(resolve(distDir, imagePath.slice(1)))
    })

    it('still rejects invalid metadata in a later post sharing a verified image', async () => {
        provideFiles([
            [firstPost, postHtml()],
            [secondPost, postHtml().replace('summary_large_image', 'summary')],
            [imagePath.slice(1), validPng()],
        ])

        await expect(verifyPrerenderPages(distDir, [firstPost, secondPost], readFile)).rejects.toThrow('twitter:card must be summary_large_image')
    })

    it('validates each distinct image and rejects a corrupt second PNG', async () => {
        provideFiles([
            [firstPost, postHtml()],
            [secondPost, postHtml('/img/og/other.png')],
            [imagePath.slice(1), validPng()],
            ['img/og/other.png', Buffer.from('not a PNG')],
        ])

        await expect(verifyPrerenderPages(distDir, [firstPost, secondPost], readFile)).rejects.toThrow('invalid PNG signature')
    })

    it('does not retain verified images across separate verification runs', async () => {
        provideFiles([
            [firstPost, postHtml()],
            [secondPost, postHtml()],
            [imagePath.slice(1), validPng()],
        ])
        await verifyPrerenderPages(distDir, [firstPost, secondPost], readFile)
        provideFiles([[firstPost, postHtml()], [imagePath.slice(1), Buffer.from('corrupted')]])

        await expect(verifyPrerenderPages(distDir, [firstPost, secondPost], readFile)).rejects.toThrow('invalid PNG signature')
    })
})
