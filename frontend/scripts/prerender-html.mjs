import { buildPostOgMeta, PRIVATE_POST_OG_TITLE, resolvePostOgTitle } from './og-image.mjs'
import { parseServiceInstant, SERVICE_TIME_ZONE } from './serviceTime.mjs'

function stripHtml(html) {
    return String(html ?? '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim()
}

function formatServiceDateTime(value) {
    // 서비스 기준 지역으로 그린다. 빌드 컨테이너 지역(UTC)이 아니라 독자 기준이어야 한다.
    // 입력에 offset이 없으면 컨테이너 지역으로 오해석되므로 파싱도 서비스 기준을 따른다.
    const date = parseServiceInstant(value)
    if (!date) return ''
    return new Intl.DateTimeFormat('ko-KR', {
        timeZone: SERVICE_TIME_ZONE,
        dateStyle: 'long',
        timeStyle: 'short',
    }).format(date)
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
    const isPrivatePost = Boolean(post?.isSecret || post?.isBlinded)
    const title = resolvePostOgTitle(post)
    const authorName = isPrivatePost ? 'NoviIs' : (post?.author?.displayName ?? 'Unknown')
    const createdAt = !isPrivatePost && post?.createdAt
        ? (parseServiceInstant(post.createdAt)?.toISOString() ?? null)
        : null
    const articleBody = isPrivatePost ? PRIVATE_POST_OG_TITLE : (post?.contents ?? '')
    const articleSummary = isPrivatePost
        ? PRIVATE_POST_OG_TITLE
        : stripHtml(articleBody).slice(0, 240)

    const ldJson = JSON.stringify({
        '@context': 'https://schema.org',
        '@type': 'Article',
        headline: title,
        datePublished: isPrivatePost ? null : post?.createdAt ?? null,
        dateModified: isPrivatePost ? null : post?.modifiedAt ?? post?.createdAt ?? null,
        author: { '@type': isPrivatePost ? 'Organization' : 'Person', name: authorName },
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
  <p style="font-size:0.875rem;color:#6b7280;margin:0 0 20px;">${escapeHtml(authorName)}${createdAt ? ` | <time datetime="${createdAt}">${escapeHtml(formatServiceDateTime(createdAt))}</time>` : ''}</p>
  <section class="post-prerender-body">${articleBody}</section>
</article>`.trim()
    }
}

export function buildPreRenderedListingSnippet({ title, description, canonicalUrl, items }) {
    const safeItems = Array.isArray(items) ? items : []
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
      ${item.description ? `<p style="margin:4px 0 0;color:#4b5563;">${escapeHtml(item.description)}</p>` : ''}
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
