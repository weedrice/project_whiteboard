import { normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { sanitizeQuillHtml } from '@/utils/sanitize'

export function renderPostContentHtml(content: string | null | undefined): string {
    if (!content) return ''

    const normalizedContents = normalizeLegacyFileUrls(content)
        .replace(/<p>\s*<\/p>/gi, '<p><br></p>')
    const sanitized = sanitizeQuillHtml(normalizedContents)

    return sanitized.replace(/<img(?![^>]*\bloading=)([^>]+)>/gi, '<img loading="lazy"$1>')
}
