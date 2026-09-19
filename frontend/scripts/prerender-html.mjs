import { escapeHtml } from './html-meta.mjs'
import { buildPostOgMeta } from './og-image.mjs'

// Public files outlive API visibility changes. Never persist mutable post content here.
export function buildPreRenderedSnippet(canonicalUrl, ogImage) {
    const title = 'NoviIs 게시글'
    const description = '게시글은 페이지에서 현재 공개 상태를 확인한 뒤 표시됩니다.'
    const ldJson = JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'WebPage',
        name: title,
        url: canonicalUrl,
    }).replace(/</g, '\\u003c')

    return {
        title,
        description,
        extraHead: [
            `<link rel="canonical" href="${escapeHtml(canonicalUrl)}">`,
            `<meta property="og:title" content="${escapeHtml(`${title} | Noviis`)}">`,
            `<meta property="og:description" content="${escapeHtml(description)}">`,
            '<meta property="og:type" content="website">',
            `<meta property="og:url" content="${escapeHtml(canonicalUrl)}">`,
            buildPostOgMeta(ogImage),
            `<script type="application/ld+json">${ldJson}</script>`
        ].join('\n    '),
        body: `
<article data-prerendered="true" style="max-width:760px;margin:0 auto;padding:24px 16px;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.6;color:#111827;">
  <h1 style="font-size:1.75rem;font-weight:700;margin:0 0 12px;">${escapeHtml(title)}</h1>
  <p>${escapeHtml(description)}</p>
</article>`.trim()
    }
}

export function buildPreRenderedListingSnippet({ canonicalUrl, isAllBoards, urls }) {
    const title = isAllBoards ? '전체 게시판' : 'NoviIs 게시판'
    const description = '게시판과 게시글은 페이지에서 현재 공개 상태를 확인한 뒤 표시됩니다.'
    const safeItems = (Array.isArray(urls) ? urls : []).map((url) => ({
        url,
        title: isAllBoards ? '게시판 보기' : '게시글 보기',
    }))
    const ldJson = JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'CollectionPage',
        name: title,
        description,
        url: canonicalUrl,
        mainEntity: {
            '@type': 'ItemList',
            itemListElement: safeItems.map((item, index) => ({
                '@type': 'ListItem',
                position: index + 1,
                name: item.title,
                url: item.url
            }))
        }
    }).replace(/</g, '\\u003c')

    const itemMarkup = safeItems.length > 0
        ? `<ul style="padding-left:20px;">${safeItems.map((item) => `
    <li style="margin:0 0 12px;">
      <a href="${escapeHtml(item.url)}">${escapeHtml(item.title)}</a>
    </li>`).join('')}
  </ul>`
        : '<p>아직 공개된 항목이 없습니다.</p>'

    return {
        title,
        description,
        extraHead: [
            `<link rel="canonical" href="${escapeHtml(canonicalUrl)}">`,
            `<meta property="og:title" content="${escapeHtml(`${title} | NoviIs`)}">`,
            `<meta property="og:description" content="${escapeHtml(description)}">`,
            '<meta property="og:type" content="website">',
            `<meta property="og:url" content="${escapeHtml(canonicalUrl)}">`,
            `<script type="application/ld+json">${ldJson}</script>`
        ].join('\n    '),
        body: `
<main data-prerendered="true" style="max-width:960px;margin:0 auto;padding:24px 16px;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;line-height:1.6;color:#111827;">
  <h1 style="font-size:1.75rem;font-weight:700;margin:0 0 12px;">${escapeHtml(title)}</h1>
  <p style="color:#4b5563;margin:0 0 20px;">${escapeHtml(description)}</p>
  ${itemMarkup}
</main>`.trim()
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
