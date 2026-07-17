#!/usr/bin/env node

import { readFile, readdir, writeFile } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { resolve } from 'node:path'

const distDir = resolve(process.cwd(), 'dist')
const sitemapPath = resolve(process.cwd(), 'public', 'sitemap.xml')
const outputPath = resolve(distDir, '.noviis-seo-release.json')
const requestedCommitSha = String(process.env.SEO_RELEASE_SHA ?? process.env.VITE_COMMIT_HASH ?? '').trim()
const strict = process.env.SEO_STRICT === 'true'

async function main() {
    if (strict && !/^[0-9a-f]{40}$/.test(requestedCommitSha)) {
        throw new Error('SEO_RELEASE_SHA must be a full lowercase commit SHA')
    }
    const commitSha = /^[0-9a-f]{40}$/.test(requestedCommitSha) ? requestedCommitSha : 'local-development'

    const [sitemapXml, entries] = await Promise.all([
        readFile(sitemapPath, 'utf8'),
        readdir(distDir, { recursive: true })
    ])
    const urlCount = [...sitemapXml.matchAll(/<loc>[^<]+<\/loc>/g)].length
    const postUrlCount = [...sitemapXml.matchAll(/<loc>[^<]+\/board\/[^<]+\/post\/\d+\/?<\/loc>/g)].length
    const prerenderCount = entries.filter((entry) => /board[\\/][^\\/]+[\\/]post[\\/]\d+[\\/]index\.html$/.test(entry)).length
    const sitemapSha256 = createHash('sha256').update(sitemapXml, 'utf8').digest('hex')

    if (strict && (postUrlCount === 0 || prerenderCount !== postUrlCount)) {
        throw new Error(`strict SEO release is incomplete: postUrls=${postUrlCount}, prerenders=${prerenderCount}`)
    }

    await writeFile(outputPath, `${JSON.stringify({ commitSha, urlCount, postUrlCount, prerenderCount, sitemapSha256 }, null, 2)}\n`, 'utf8')
    console.log(`[seo-manifest] wrote ${outputPath} (${postUrlCount} posts, ${prerenderCount} prerenders)`)
}

main().catch((error) => {
    console.error(`[seo-manifest] failed: ${String(error)}`)
    process.exitCode = 1
})
