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
 * Quill 에디터에서 생성된 HTML을 sanitize합니다.
 * Quill의 기본 태그와 속성을 허용합니다.
 * 
 * @param html Quill HTML 문자열
 * @returns sanitize된 HTML 문자열
 */
export function sanitizeQuillHtml(html: string): string {
    return DOMPurify.sanitize(html, {
        // Quill 에디터에서 사용하는 태그 허용
        ALLOWED_TAGS: [
            'p', 'br', 'strong', 'em', 'u', 's', 'strike',
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
            'ul', 'ol', 'li',
            'blockquote', 'code', 'pre',
            'a', 'img', 'video', 'iframe',
            'div', 'span', 'sub', 'sup'
        ],
        ALLOWED_ATTR: [
            'href', 'src', 'alt', 'title', 'class',
            'loading', 'width', 'height', 'style',
            'data-id', 'data-value'
        ],
        // 스타일 속성 허용 (Quill이 사용)
        ALLOW_DATA_ATTR: true,
        // 이미지 lazy loading 지원
        ADD_ATTR: ['loading']
    })
}
