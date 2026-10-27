import { hasPostHtmlDocumentStructure, mapSandboxedPostHtmlPayloads } from '@/utils/postHtmlSandbox'
import { getViewportImageElements } from '@/utils/postViewportHtml'

const LEGACY_FILE_URL_PATTERN = /^\/files\/(\d+)([?#].*)?$/
const LEGACY_MARKDOWN_FILE_PATTERN = /!\[emoticon\]\(\/files\/(\d+)([?#][^)]*)?\)/g
const LEGACY_HTML_FILE_PATTERN = /(\s(?:src|href)=["'])\/files\/(\d+)([?#][^"']*)?(["'])/gi
const EDITOR_IMAGE_URL_ATTRIBUTE = 'data-server-src'
const EDITOR_IMAGE_FILE_ID_ATTRIBUTE = 'data-file-id'
const LOCAL_FILE_URL_PATTERN = /^\/(?:api\/v1\/)?files\/(\d+)([?#].*)?$/

/** Only the explicit viewport template is rendered; other templates stay inert. */
export function queryPostFileElements<T extends Element>(root: ParentNode, selector: string): T[] {
    const matches = Array.from(root.querySelectorAll<T>(selector))
    for (const template of Array.from(root.querySelectorAll<HTMLTemplateElement>('template[data-noviis-layer="viewport"]'))) {
        if (template instanceof HTMLTemplateElement) {
            const supportedImages = new Set<Element>(getViewportImageElements(template.content))
            matches.push(...Array.from(template.content.querySelectorAll<T>(selector))
                .filter((element) => supportedImages.has(element)))
        }
    }
    return matches
}

/** Preserve document/fragment boundaries when changing only file references. */
export function serializePostFileDocument(doc: Document, source: string): string {
    return Array.from(doc.childNodes).map((node) => {
        if (node === doc.documentElement) {
            return hasPostHtmlDocumentStructure(source)
                ? doc.documentElement.outerHTML
                : doc.head.innerHTML + doc.body.innerHTML
        }
        return new XMLSerializer().serializeToString(node)
    }).join('')
}

export function normalizeFileUrl(url: string): string {
    return url.replace(LEGACY_FILE_URL_PATTERN, '/api/v1/files/$1$2')
}

export function normalizeLegacyFileUrls(content: string): string {
    return content
        .replace(LEGACY_MARKDOWN_FILE_PATTERN, '![emoticon](/api/v1/files/$1$2)')
        .replace(LEGACY_HTML_FILE_PATTERN, '$1/api/v1/files/$2$3$4')
}

function normalizeEditorFileImages(
    content: string,
    options: { removeEditorAttributes: boolean }
): string {
    if (!content || typeof DOMParser === 'undefined') {
        return content
    }

    const parser = new DOMParser()
    const doc = parser.parseFromString(content, 'text/html')
    let changed = false
    queryPostFileElements<HTMLImageElement>(doc, `img[${EDITOR_IMAGE_URL_ATTRIBUTE}]`).forEach((image) => {
        const serverSrc = image.getAttribute(EDITOR_IMAGE_URL_ATTRIBUTE)
        if (!serverSrc) return

        const normalizedServerSrc = normalizeLocalEditorFileUrl(serverSrc, image.getAttribute(EDITOR_IMAGE_FILE_ID_ATTRIBUTE))
        if (normalizedServerSrc) {
            changed ||= image.getAttribute('src') !== normalizedServerSrc
            image.setAttribute('src', normalizedServerSrc)
        }
        if (options.removeEditorAttributes) {
            changed = true
            image.removeAttribute(EDITOR_IMAGE_URL_ATTRIBUTE)
            image.removeAttribute(EDITOR_IMAGE_FILE_ID_ATTRIBUTE)
        }
    })

    if (!changed) return content
    return serializePostFileDocument(doc, content)
}

function normalizeLocalEditorFileUrl(url: string, fileId: string | null): string | null {
    const match = url.match(LOCAL_FILE_URL_PATTERN)
    if (!match) {
        return null
    }

    const normalizedFileId = fileId?.trim()
    if (normalizedFileId && normalizedFileId !== match[1]) {
        return null
    }

    return `/api/v1/files/${match[1]}${match[2] ?? ''}`
}

export function normalizeEditorFileImageUrls(content: string): string {
    return normalizeEditorFileImages(mapSandboxedPostHtmlPayloads(content,
        (payload) => normalizeEditorFileImages(payload, { removeEditorAttributes: true })),
    { removeEditorAttributes: true })
}

export function normalizeEditorFileImagePreviewSources(content: string): string {
    return normalizeEditorFileImages(mapSandboxedPostHtmlPayloads(content,
        (payload) => normalizeEditorFileImages(payload, { removeEditorAttributes: false })),
    { removeEditorAttributes: false })
}
