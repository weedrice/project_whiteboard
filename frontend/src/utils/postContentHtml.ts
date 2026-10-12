import { normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { transformHtmlDocument } from '@/utils/htmlTransformPipeline'
import { asSanitizedHtml, sanitizeQuillHtml, type SanitizedHtml } from '@/utils/sanitize'
import { resolveLegacyPostEditorImageCssWidth } from '@/utils/postEditorImageLayout'

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
        addImageCaptionsInDocument,
    ])

    return asSanitizedHtml(transformed)
}

function addImageCaptionsInDocument(doc: Document): void {
    doc.querySelectorAll<HTMLImageElement>('img[data-caption]').forEach((image) => {
        const caption = image.getAttribute('data-caption')
        if (!caption) return
        const wrapper = doc.createElement('span')
        wrapper.className = 'nv-image-with-caption'
        for (const alignment of ['left', 'center', 'right']) {
            if (image.classList.contains(`tiptap-image-align-${alignment}`)) {
                wrapper.classList.add(`tiptap-image-align-${alignment}`)
            }
        }
        const width = image.style.width || resolveLegacyPostEditorImageCssWidth(image.getAttribute('width'))
        if (width) {
            wrapper.style.width = width
            image.style.width = '100%'
        }
        const description = doc.createElement('span')
        description.className = 'nv-image-caption'
        description.textContent = caption
        image.parentNode?.insertBefore(wrapper, image)
        wrapper.append(image, description)
    })
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
