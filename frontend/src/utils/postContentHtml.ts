import { normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { transformHtmlDocument } from '@/utils/htmlTransformPipeline'
import { asSanitizedHtml, sanitizeQuillHtml, type SanitizedHtml } from '@/utils/sanitize'

export interface CodeBlockHighlightLabels {
    copy: string
    copyAriaLabel: string
}

export type CodeBlockHighlighter = (doc: Document, labels?: CodeBlockHighlightLabels) => void

export function renderPostContentHtml(
    content: string | null | undefined,
    codeBlockLabels?: CodeBlockHighlightLabels,
    codeBlockHighlighter?: CodeBlockHighlighter | null
): SanitizedHtml {
    if (!content) return asSanitizedHtml('')

    const normalizedContents = normalizeLegacyFileUrls(content)
        .replace(/<p>\s*<\/p>/gi, '<p><br></p>')
    const sanitized = sanitizeQuillHtml(normalizedContents)
    const transformed = transformHtmlDocument(sanitized, [
        (doc) => codeBlockHighlighter?.(doc, codeBlockLabels),
        enhanceMentionLinksInDocument,
        addLazyLoadingToImagesInDocument,
    ])

    return asSanitizedHtml(transformed)
}

function enhanceMentionLinksInDocument(doc: Document): void {
    doc.querySelectorAll<HTMLElement>('[data-mention-user-id]').forEach((mention) => {
        mention.setAttribute('role', 'link')
        mention.setAttribute('tabindex', '0')
    })
}

function addLazyLoadingToImagesInDocument(doc: Document): void {
    doc.querySelectorAll<HTMLImageElement>('img').forEach((image) => {
        if (!image.hasAttribute('loading')) {
            image.setAttribute('loading', 'lazy')
        }
    })
}
