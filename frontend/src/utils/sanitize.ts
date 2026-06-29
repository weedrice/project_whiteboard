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

/**
 * HTML 콘텐츠를 sanitize하여 XSS 공격을 방지합니다.
 * 
 * @param html 원본 HTML 문자열
 * @param options DOMPurify 옵션
 * @returns sanitize된 HTML 문자열
 * 
 * @example
 * ```typescript
 * const safeHtml = sanitizeHtml(userInput)
 * ```
 */
export function sanitizeHtml(html: string, options?: Config): string {
    const config = {
        // 기본 옵션: 이미지, 링크, 기본 포맷팅 허용
        ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'blockquote', 'code', 'pre', 'a', 'img'],
        ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'class', 'loading'],
        // 링크는 http/https만 허용
        ALLOW_DATA_ATTR: false,
        ...options
    }
    return DOMPurify.sanitize(html, config)
}

/**
 * 게시글 본문(에디터) HTML을 sanitize합니다.
 * Quill / TipTap 등 에디터에서 생성된 태그·속성을 허용합니다.
 *
 * @param html 에디터 HTML 문자열
 * @returns sanitize된 HTML 문자열
 */
export function sanitizeQuillHtml(html: string): string {
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
        ],
        ALLOW_DATA_ATTR: false,
        ADD_ATTR: ['loading']
    })

    return tightenQuillHtml(sanitized)
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
        }
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
        const host = url.hostname.toLowerCase()
        if (!ALLOWED_IFRAME_HOSTS.has(host)) return false

        if (host.includes('youtube')) {
            return url.pathname.startsWith('/embed/')
        }

        if (host === 'player.vimeo.com') {
            return url.pathname.startsWith('/video/')
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
