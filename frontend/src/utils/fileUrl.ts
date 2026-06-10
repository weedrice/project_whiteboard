const LEGACY_FILE_URL_PATTERN = /^\/files\/(\d+)([?#].*)?$/
const LEGACY_MARKDOWN_FILE_PATTERN = /!\[emoticon\]\(\/files\/(\d+)([?#][^)]*)?\)/g
const LEGACY_HTML_FILE_PATTERN = /(\s(?:src|href)=["'])\/files\/(\d+)([?#][^"']*)?(["'])/gi
const EDITOR_IMAGE_URL_ATTRIBUTE = 'data-server-src'
const EDITOR_IMAGE_FILE_ID_ATTRIBUTE = 'data-file-id'

export function normalizeFileUrl(url: string): string {
    return url.replace(LEGACY_FILE_URL_PATTERN, '/api/v1/files/$1$2')
}

export function normalizeLegacyFileUrls(content: string): string {
    return content
        .replace(LEGACY_MARKDOWN_FILE_PATTERN, '![emoticon](/api/v1/files/$1$2)')
        .replace(LEGACY_HTML_FILE_PATTERN, '$1/api/v1/files/$2$3$4')
}

export function normalizeEditorFileImageUrls(content: string): string {
    if (!content || typeof DOMParser === 'undefined') {
        return content
    }

    const parser = new DOMParser()
    const doc = parser.parseFromString(content, 'text/html')
    doc.querySelectorAll<HTMLImageElement>(`img[${EDITOR_IMAGE_URL_ATTRIBUTE}]`).forEach((image) => {
        const serverSrc = image.getAttribute(EDITOR_IMAGE_URL_ATTRIBUTE)
        if (!serverSrc) return

        image.setAttribute('src', normalizeFileUrl(serverSrc))
        image.removeAttribute(EDITOR_IMAGE_URL_ATTRIBUTE)
        image.removeAttribute(EDITOR_IMAGE_FILE_ID_ATTRIBUTE)
    })

    return doc.body.innerHTML
}

export function normalizeEditorFileImagePreviewSources(content: string): string {
    if (!content || typeof DOMParser === 'undefined') {
        return content
    }

    const parser = new DOMParser()
    const doc = parser.parseFromString(content, 'text/html')
    doc.querySelectorAll<HTMLImageElement>(`img[${EDITOR_IMAGE_URL_ATTRIBUTE}]`).forEach((image) => {
        const serverSrc = image.getAttribute(EDITOR_IMAGE_URL_ATTRIBUTE)
        if (!serverSrc) return

        image.setAttribute('src', normalizeFileUrl(serverSrc))
    })

    return doc.body.innerHTML
}
