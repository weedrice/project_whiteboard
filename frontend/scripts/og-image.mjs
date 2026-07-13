import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import satori from 'satori'
import { Resvg } from '@resvg/resvg-js'

export const OG_IMAGE_WIDTH = 1200
export const OG_IMAGE_HEIGHT = 630
export const OG_TEMPLATE_VERSION = 'warm-paper-v1'

const fontPath = resolve(process.cwd(), 'scripts', 'assets', 'fonts', 'NotoSansKR-Regular.otf')
let fontDataPromise

const text = (value, style = {}) => ({
    type: 'div',
    props: {
        style: { display: 'flex', ...style },
        children: String(value ?? ''),
    },
})

export async function loadOgFont() {
    fontDataPromise ??= readFile(fontPath)
    return fontDataPromise
}

export function createPostOgImageFilename(post) {
    const identity = [
        OG_TEMPLATE_VERSION,
        post?.title ?? '',
        post?.board?.boardName ?? post?.boardName ?? '',
    ].join('\u0000')
    const hash = createHash('sha256').update(identity).digest('hex').slice(0, 12)
    return `post-${post?.postId}-${hash}.png`
}

export function canUsePostAttachment(post) {
    return !post?.isNsfw
        && !post?.isSpoiler
        && !post?.isSecret
        && !post?.isBlinded
        && Array.isArray(post?.imageUrls)
        && post.imageUrls.some(Boolean)
}

export function selectPostOgImage(post, siteUrl) {
    if (!canUsePostAttachment(post)) return null
    const firstImage = post.imageUrls.find(Boolean)
    try {
        const imageUrl = new URL(firstImage, `${String(siteUrl).replace(/\/+$/, '')}/`)
        return ['http:', 'https:'].includes(imageUrl.protocol) ? imageUrl.toString() : null
    } catch {
        return null
    }
}

export async function renderPostOgImage(post, fontData) {
    const resolvedFontData = fontData ?? await loadOgFont()
    const title = String(post?.title ?? 'Post').trim() || 'Post'
    const boardName = String(post?.board?.boardName ?? post?.boardName ?? 'NoviIs').trim() || 'NoviIs'
    const tree = {
        type: 'div',
        props: {
            style: {
                alignItems: 'stretch',
                background: '#f5f0e6',
                color: '#1d2433',
                display: 'flex',
                flexDirection: 'column',
                fontFamily: 'Noto Sans KR',
                height: '100%',
                justifyContent: 'space-between',
                padding: '70px 78px 62px',
                width: '100%',
            },
            children: [
                text(boardName.toUpperCase(), {
                    color: '#2447b8',
                    fontSize: 25,
                    fontWeight: 700,
                    letterSpacing: '0.18em',
                }),
                text(title, {
                    display: 'block',
                    fontSize: title.length > 54 ? 54 : 66,
                    fontWeight: 700,
                    letterSpacing: '-0.045em',
                    lineClamp: 3,
                    lineHeight: 1.2,
                    maxWidth: '1044px',
                    overflow: 'hidden',
                }),
                {
                    type: 'div',
                    props: {
                        style: {
                            alignItems: 'center',
                            borderTop: '2px solid #d5cdbd',
                            display: 'flex',
                            justifyContent: 'space-between',
                            paddingTop: '26px',
                        },
                        children: [
                            text('NOVIIS', {
                                fontSize: 30,
                                fontWeight: 700,
                                letterSpacing: '0.12em',
                            }),
                            text('noviis.kr', {
                                color: '#646772',
                                fontSize: 22,
                                fontWeight: 500,
                            }),
                        ],
                    },
                },
            ],
        },
    }

    const svg = await satori(tree, {
        width: OG_IMAGE_WIDTH,
        height: OG_IMAGE_HEIGHT,
        fonts: [{
            name: 'Noto Sans KR',
            data: resolvedFontData,
            weight: 400,
            style: 'normal',
        }],
    })
    return Buffer.from(new Resvg(svg, {
        fitTo: { mode: 'width', value: OG_IMAGE_WIDTH },
    }).render().asPng())
}

export async function resolvePostOgImage(post, { siteUrl, distDir }) {
    const attachmentUrl = selectPostOgImage(post, siteUrl)
    const title = String(post?.title ?? 'Post').trim() || 'Post'
    if (attachmentUrl) {
        return { url: attachmentUrl, alt: `${title} 대표 이미지`, generated: false }
    }

    const filename = createPostOgImageFilename(post)
    const outputPath = resolve(distDir, 'img', 'og', filename)
    const png = await renderPostOgImage(post)
    await mkdir(dirname(outputPath), { recursive: true })
    await writeFile(outputPath, png)
    return {
        url: new URL(`/img/og/${filename}`, `${String(siteUrl).replace(/\/+$/, '')}/`).toString(),
        alt: `${title} 공유 이미지`,
        generated: true,
        outputPath,
    }
}

export function escapeMetaContent(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;')
}

export function buildPostOgMeta(ogImage) {
    const imageUrl = escapeMetaContent(ogImage.url)
    const imageAlt = escapeMetaContent(ogImage.alt)
    const tags = [
        `<meta property="og:image" content="${imageUrl}">`,
        `<meta property="og:image:alt" content="${imageAlt}">`,
        '<meta name="twitter:card" content="summary_large_image">',
        `<meta name="twitter:image" content="${imageUrl}">`,
        `<meta name="twitter:image:alt" content="${imageAlt}">`,
    ]
    if (ogImage.generated) {
        tags.splice(2, 0,
            '<meta property="og:image:type" content="image/png">',
            `<meta property="og:image:width" content="${OG_IMAGE_WIDTH}">`,
            `<meta property="og:image:height" content="${OG_IMAGE_HEIGHT}">`)
    }
    return tags.join('\n            ')
}
