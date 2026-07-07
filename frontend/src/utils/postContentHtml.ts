import { normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { highlightCodeBlocks } from '@/utils/codeHighlighting'
import { asSanitizedHtml, sanitizeQuillHtml, type SanitizedHtml } from '@/utils/sanitize'

export function renderPostContentHtml(content: string | null | undefined): SanitizedHtml {
    if (!content) return asSanitizedHtml('')

    const normalizedContents = normalizeLegacyFileUrls(content)
        .replace(/<p>\s*<\/p>/gi, '<p><br></p>')
    const sanitized = enhanceMentionLinks(highlightCodeBlocks(sanitizeQuillHtml(normalizedContents)))

    return asSanitizedHtml(sanitized.replace(/<img(?![^>]*\bloading=)([^>]+)>/gi, '<img loading="lazy"$1>'))
}

function enhanceMentionLinks(html: string): string {
    if (typeof DOMParser === 'undefined') {
        return html
    }

    const parser = new DOMParser()
    const doc = parser.parseFromString(html, 'text/html')
    doc.querySelectorAll<HTMLElement>('[data-mention-user-id]').forEach((mention) => {
        mention.setAttribute('role', 'link')
        mention.setAttribute('tabindex', '0')
    })
    return doc.body.innerHTML
}
