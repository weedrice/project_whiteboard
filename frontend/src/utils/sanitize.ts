import DOMPurify from 'dompurify'
import type { Config } from 'dompurify'

const ALLOWED_INLINE_STYLE_PROPERTIES = new Set([
    'color',
    'background-color',
    'font-size',
    'line-height',
    'text-align',
])

const UNSAFE_STYLE_VALUE_PATTERN = /(?:url\s*\(|expression\s*\(|javascript:)/i
const ALLOWED_IFRAME_HOSTS = new Set([
    'www.youtube.com',
    'youtube.com',
    'www.youtube-nocookie.com',
    'youtube-nocookie.com',
    'player.vimeo.com',
])

export type SanitizedHtml = string & { readonly __sanitizedHtmlBrand: unique symbol }

export function asSanitizedHtml(html: string): SanitizedHtml {
    return html as SanitizedHtml
}

/**
 * Sanitizes generic HTML content to prevent XSS.
 *
 * @param html Source HTML string.
 * @param options DOMPurify options merged into the default allowlist.
 * @returns Sanitized HTML string branded for v-html rendering.
 */
export function sanitizeHtml(html: string, options?: Config): SanitizedHtml {
    const config = {
        ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'a', 'img'],
        ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'loading'],
        ALLOW_DATA_ATTR: false,
        ...options
    }
    return asSanitizedHtml(DOMPurify.sanitize(html, config))
}

/**
 * Sanitizes post body HTML produced by the rich-text editor.
 * Allows the editor tags and attributes needed for images, videos, tables, and inline formatting.
 *
 * @param html Editor HTML string.
 * @returns Sanitized HTML string branded for v-html rendering.
 */
export function sanitizeQuillHtml(html: string): SanitizedHtml {
    const sanitized = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: [
            'p', 'br', 'strong', 'em', 'u', 's', 'strike',
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
            'ul', 'ol', 'li',
            'blockquote', 'code', 'pre',
            'a', 'img', 'iframe', 'hr',
            'div', 'span', 'sub', 'sup',
            'table', 'thead', 'tbody', 'tr', 'th', 'td',
            'mark'
        ],
        ALLOWED_ATTR: [
            'href', 'src', 'alt', 'title', 'class',
            'loading', 'width', 'height', 'style',
            'data-file-id', 'data-server-src', 'data-video-embed',
            'frameborder', 'allowfullscreen', 'allow',
            'referrerpolicy', 'sandbox',
        ],
        ALLOW_DATA_ATTR: false,
        ADD_ATTR: ['loading']
    })

    return asSanitizedHtml(tightenQuillHtml(sanitized))
}

function tightenQuillHtml(html: string): string {
    if (typeof DOMParser === 'undefined') {
        return html
    }

    const parser = new DOMParser()
    const doc = parser.parseFromString(html, 'text/html')

    doc.querySelectorAll<HTMLIFrameElement>('iframe').forEach((iframe) => {
        if (!isAllowedIframeSrc(iframe.getAttribute('src'))) {
            iframe.remove()
            return
        }
        iframe.setAttribute('loading', 'lazy')
        iframe.setAttribute('referrerpolicy', 'strict-origin-when-cross-origin')
        iframe.setAttribute('sandbox', 'allow-scripts allow-same-origin allow-presentation')
        iframe.setAttribute('allow', 'encrypted-media; picture-in-picture')
    })

    doc.querySelectorAll<HTMLElement>('[style]').forEach((element) => {
        const style = filterInlineStyle(element.getAttribute('style') ?? '')
        if (style) {
            element.setAttribute('style', style)
        } else {
            element.removeAttribute('style')
        }
    })

    return doc.body.innerHTML
}

function isAllowedIframeSrc(src: string | null): boolean {
    if (!src) return false

    try {
        const url = new URL(src)
        if (url.protocol !== 'https:') return false

        const host = url.hostname.toLowerCase()
        if (!ALLOWED_IFRAME_HOSTS.has(host)) return false

        if (host.includes('youtube')) {
            return /^\/embed\/[^/]+$/.test(url.pathname)
        }

        if (host === 'player.vimeo.com') {
            return /^\/video\/\d+\/?$/.test(url.pathname)
        }

        return false
    } catch {
        return false
    }
}

function filterInlineStyle(style: string): string {
    return style
        .split(';')
        .map((declaration) => declaration.trim())
        .filter(Boolean)
        .map((declaration) => {
            const separatorIndex = declaration.indexOf(':')
            if (separatorIndex === -1) return null

            const property = declaration.slice(0, separatorIndex).trim().toLowerCase()
            const value = declaration.slice(separatorIndex + 1).trim()
            if (!ALLOWED_INLINE_STYLE_PROPERTIES.has(property)) return null
            if (!value || UNSAFE_STYLE_VALUE_PATTERN.test(value)) return null

            return `${property}: ${value}`
        })
        .filter((declaration): declaration is string => Boolean(declaration))
        .join('; ')
}
