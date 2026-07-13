import { buildPostOgMeta } from './og-image.mjs'

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

export function buildPreRenderedSnippet(post, canonicalUrl, ogImage) {
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
            `<meta property="og:title" content="${escapeHtml(`${title} | Noviis`)}">`,
            `<meta property="og:description" content="${escapeHtml(articleSummary || 'Post content')}">`,
            '<meta property="og:type" content="article">',
            `<meta property="og:url" content="${escapeHtml(canonicalUrl)}">`,
            buildPostOgMeta(ogImage),
            `<script type="application/ld+json">${ldJson}</script>`
        ].join('\n    '),
        body: `
<article data-prerendered="true" style="max-width:760px;margin:0 auto;padding:24px 16px;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.6;color:#111827;">
  <h1 style="font-size:1.75rem;font-weight:700;margin:0 0 12px;">${escapeHtml(title)}</h1>
  <p style="font-size:0.875rem;color:#6b7280;margin:0 0 20px;">${escapeHtml(authorName)}${createdAt ? ` | <time datetime="${createdAt}">${escapeHtml(new Date(createdAt).toLocaleString('ko-KR'))}</time>` : ''}</p>
  <section class="post-prerender-body">${articleBody}</section>
</article>`.trim()
    }
}

export function injectIntoTemplate(indexHtml, renderData) {
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
