import { createHash } from 'node:crypto'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import satori from 'satori'
import { Resvg } from '@resvg/resvg-js'

export const OG_IMAGE_WIDTH = 1200
export const OG_IMAGE_HEIGHT = 630
export const OG_TEMPLATE_VERSION = 'icon-title-v1'
export const PRIVATE_POST_OG_TITLE = '\uacf5\uac1c\ub418\uc9c0 \uc54a\uc740 \uac8c\uc2dc\uae00\uc785\ub2c8\ub2e4'

const fontPath = resolve(process.cwd(), 'scripts', 'assets', 'fonts', 'NotoSansKR-Regular.otf')
const iconPath = resolve(process.cwd(), 'public', 'favicon.ico')
let fontDataPromise
let iconDataUrlPromise

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

export async function loadOgIcon() {
    iconDataUrlPromise ??= readFile(iconPath)
        .then((icon) => `data:image/png;base64,${icon.toString('base64')}`)
    return iconDataUrlPromise
}

export function resolvePostOgTitle(post) {
    if (post?.isSecret || post?.isBlinded) return PRIVATE_POST_OG_TITLE
    return String(post?.title ?? 'Post').trim() || 'Post'
}

export function createPostOgImageFilename(post) {
    const identity = [
        OG_TEMPLATE_VERSION,
        resolvePostOgTitle(post),
        post?.board?.boardName ?? post?.boardName ?? '',
    ].join('\u0000')
    const hash = createHash('sha256').update(identity).digest('hex').slice(0, 12)
    return `post-${post?.postId}-${hash}.png`
}

export async function renderPostOgImage(post, fontData, iconDataUrl) {
    const [resolvedFontData, resolvedIconDataUrl] = await Promise.all([
        fontData ?? loadOgFont(),
        iconDataUrl ?? loadOgIcon(),
    ])
    const title = resolvePostOgTitle(post)
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
                {
                    type: 'div',
                    props: {
                        style: {
                            alignItems: 'center',
                            display: 'flex',
                            gap: '24px',
                        },
                        children: [
                            {
                                type: 'div',
                                props: {
                                    style: {
                                        alignItems: 'center',
                                        background: '#e6e8f2',
                                        border: '2px solid #d5d9ea',
                                        borderRadius: '24px',
                                        display: 'flex',
                                        height: '96px',
                                        justifyContent: 'center',
                                        width: '96px',
                                    },
                                    children: {
                                        type: 'img',
                                        props: {
                                            alt: '',
                                            height: 56,
                                            src: resolvedIconDataUrl,
                                            style: {
                                                height: '56px',
                                                objectFit: 'contain',
                                                width: '56px',
                                            },
                                            width: 56,
                                        },
                                    },
                                },
                            },
                            {
                                type: 'div',
                                props: {
                                    style: {
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: '7px',
                                    },
                                    children: [
                                        text('NOVIIS', {
                                            color: '#1d2433',
                                            fontSize: 30,
                                            fontWeight: 700,
                                            letterSpacing: '0.12em',
                                        }),
                                        text(boardName, {
                                            color: '#646772',
                                            fontSize: 22,
                                            fontWeight: 500,
                                        }),
                                    ],
                                },
                            },
                        ],
                    },
                },
                text(title, {
                    display: 'block',
                    fontSize: title.length > 54 ? 46 : 56,
                    fontWeight: 700,
                    letterSpacing: '-0.035em',
                    lineClamp: 3,
                    lineHeight: 1.25,
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
                            text('생각을 나누는 커뮤니티', {
                                color: '#646772',
                                fontSize: 21,
                                fontWeight: 500,
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
    const title = resolvePostOgTitle(post)
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
