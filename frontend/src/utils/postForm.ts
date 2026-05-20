import { normalizeEditorFileImageUrls, normalizeLegacyFileUrls } from '@/utils/fileUrl'

export type PostFormFileIdScope = 'content' | 'draft'

export type PostFormPayloadForm = {
    title: string
    content: string
    categoryId: string | number
    tags: string[]
    isNsfw: boolean
    isSpoiler: boolean
    isNotice: boolean
    isSecret: boolean
}

export type BuildPostFormPayloadOptions = {
    form: PostFormPayloadForm
    mode: 'create' | 'edit'
    hideCategory?: boolean
    hideTags?: boolean
    hideSpoiler?: boolean
    hideSecret?: boolean
    showNotice: boolean
    canShowNsfw: boolean
    fileIds: number[]
}

export function extractFileIdFromPostImageSrc(src: string, baseOrigin = getDefaultBaseOrigin()): number | null {
    let url: URL
    try {
        url = new URL(src, baseOrigin)
    } catch {
        return null
    }

    const isLocalFileUrl = src.startsWith('/') || url.origin === baseOrigin
    if (!isLocalFileUrl) {
        return null
    }

    const match = url.pathname.match(/^\/(?:api\/v1\/)?files\/(\d+)$/)
    if (!match) {
        return null
    }

    const fileId = Number(match[1])
    return Number.isSafeInteger(fileId) ? fileId : null
}

export function extractPostFileIdsFromContent(content: string): number[] {
    const fileIds = new Set<number>()
    const parser = new DOMParser()
    const doc = parser.parseFromString(content, 'text/html')

    doc.querySelectorAll('img[src]').forEach((image) => {
        const dataFileIdAttribute = image.getAttribute('data-file-id')
        const dataFileId = dataFileIdAttribute && /^\d+$/.test(dataFileIdAttribute)
            ? Number(dataFileIdAttribute)
            : null
        if (dataFileId != null && Number.isSafeInteger(dataFileId)) {
            fileIds.add(dataFileId)
            return
        }

        const fileId = extractFileIdFromPostImageSrc(image.getAttribute('src') ?? '')
        if (fileId != null) {
            fileIds.add(fileId)
        }
    })

    return [...fileIds]
}

export function resolvePostFormFileIds(
    content: string,
    draftFileIds: number[],
    scope: PostFormFileIdScope,
): number[] {
    const contentFileIds = extractPostFileIdsFromContent(content)
    if (scope === 'content') {
        return contentFileIds
    }

    return contentFileIds.filter((fileId) => draftFileIds.includes(fileId))
}

export function buildPostFormPayload({
    form,
    mode,
    hideCategory,
    hideTags,
    hideSpoiler,
    hideSecret,
    showNotice,
    canShowNsfw,
    fileIds,
}: BuildPostFormPayloadOptions) {
    const contents = normalizeLegacyFileUrls(normalizeEditorFileImageUrls(form.content))
    const parsedCategoryId = typeof form.categoryId === 'string'
        ? parseInt(form.categoryId, 10)
        : form.categoryId
    const categoryId = hideCategory || Number.isNaN(parsedCategoryId) || !parsedCategoryId
        ? undefined
        : parsedCategoryId

    return {
        title: form.title,
        ...(categoryId !== undefined && { categoryId }),
        tags: hideTags ? [] : form.tags,
        contents,
        isNsfw: canShowNsfw ? form.isNsfw : false,
        isSpoiler: hideSpoiler ? false : form.isSpoiler,
        isSecret: hideSecret ? false : form.isSecret,
        ...(mode === 'create' && { isNotice: showNotice ? form.isNotice : false }),
        fileIds,
    }
}

export function toEmbedPostVideoUrl(url: string): string {
    const trimmed = (url || '').trim()
    if (!trimmed) return ''

    const youtubeMatch = trimmed.match(/^(?:(https?):\/\/)?(?:(?:www|m)\.)?youtube\.com\/watch.*v=([a-zA-Z0-9_-]+)/)
        || trimmed.match(/^(?:(https?):\/\/)?(?:(?:www|m)\.)?youtu\.be\/([a-zA-Z0-9_-]+)/)
    if (youtubeMatch) {
        return `${youtubeMatch[1] || 'https'}://www.youtube.com/embed/${youtubeMatch[2]}?showinfo=0`
    }

    const vimeoMatch = trimmed.match(/^(?:(https?):\/\/)?(?:www\.)?vimeo\.com\/(\d+)/)
    if (vimeoMatch) {
        return `${vimeoMatch[1] || 'https'}://player.vimeo.com/video/${vimeoMatch[2]}/`
    }

    return trimmed
}

function getDefaultBaseOrigin(): string {
    return typeof window !== 'undefined' ? window.location.origin : 'http://localhost'
}
