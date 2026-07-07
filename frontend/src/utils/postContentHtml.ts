import { normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { highlightCodeBlocks } from '@/utils/codeHighlighting'
import { asSanitizedHtml, sanitizeQuillHtml, type SanitizedHtml } from '@/utils/sanitize'

export function renderPostContentHtml(content: string | null | undefined): SanitizedHtml {
    if (!content) return asSanitizedHtml('')

    const normalizedContents = normalizeLegacyFileUrls(content)
        .replace(/<p>\s*<\/p>/gi, '<p><br></p>')
    const sanitized = highlightCodeBlocks(sanitizeQuillHtml(normalizedContents))

    return asSanitizedHtml(sanitized.replace(/<img(?![^>]*\bloading=)([^>]+)>/gi, '<img loading="lazy"$1>'))
}
