import DOMPurify from 'dompurify'

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
export function sanitizeHtml(html: string, options?: Record<string, unknown>): string {
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
    return DOMPurify.sanitize(html, {
        ALLOWED_TAGS: [
            'p', 'br', 'strong', 'em', 'u', 's', 'strike',
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
            'ul', 'ol', 'li',
            'blockquote', 'code', 'pre',
            'a', 'img', 'video', 'iframe',
            'div', 'span', 'sub', 'sup',
            'table', 'thead', 'tbody', 'tr', 'th', 'td',
            'mark'
        ],
        ALLOWED_ATTR: [
            'href', 'src', 'alt', 'title', 'class',
            'loading', 'width', 'height', 'style',
            'data-id', 'data-value', 'data-video-embed',
            'frameborder', 'allowfullscreen', 'allow',
            'data-color'
        ],
        ALLOW_DATA_ATTR: true,
        ADD_ATTR: ['loading']
    })
}
