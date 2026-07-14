#!/usr/bin/env node

import { readFile, readdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { validatePng, validatePrerenderHtml } from './prerender-output-validation.mjs'

const distDir = resolve(process.cwd(), 'dist')

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
