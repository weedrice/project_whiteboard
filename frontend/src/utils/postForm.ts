import { normalizeEditorFileImageUrls, normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { getWindowOrigin } from '@/utils/browserEnv'
import { encodeSandboxedPostHtml, requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'

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
    const normalizedContents = requiresSandboxedPostHtml(form.content)
        ? normalizeLegacyFileUrls(form.content)
        : normalizeLegacyFileUrls(normalizeEditorFileImageUrls(form.content))
    const contents = encodeSandboxedPostHtml(normalizedContents)
    const parsedCategoryId = typeof form.categoryId === 'string'
        ? Number.parseInt(form.categoryId, 10)
        : form.categoryId
    const categoryId = hideCategory || Number.isNaN(parsedCategoryId) || !parsedCategoryId
        ? undefined
        : parsedCategoryId

    return {
        title: form.title.trim(),
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

export function toSafePostLinkUrl(url: string): string {
    const trimmed = (url || '').trim()
    if (!trimmed) return ''
    if (/^[a-z][a-z0-9+.-]*:/i.test(trimmed) && !/^https?:\/\//i.test(trimmed)) return ''

    let parsed: URL
    try {
        parsed = new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`)
    } catch {
        return ''
    }

    if (!['http:', 'https:'].includes(parsed.protocol)) {
        return ''
    }

    if (!parsed.hostname || parsed.username || parsed.password) {
        return ''
    }

    return parsed.toString()
}

export function toEmbedPostVideoUrl(url: string): string {
    const trimmed = (url || '').trim()
    if (!trimmed) return ''

    let parsed: URL
    try {
        parsed = new URL(/^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`)
    } catch {
        return ''
    }

    if (!['http:', 'https:'].includes(parsed.protocol)) {
        return ''
    }

    const host = parsed.hostname.toLowerCase()
    const path = parsed.pathname
    const youtubeIdPattern = /^[a-zA-Z0-9_-]+$/
    const vimeoIdPattern = /^\d+$/

    if (host === 'youtu.be') {
        const id = path.split('/').filter(Boolean)[0] ?? ''
        return youtubeIdPattern.test(id) ? `https://www.youtube.com/embed/${id}?showinfo=0` : ''
    }

    if (['youtube.com', 'www.youtube.com', 'm.youtube.com'].includes(host)) {
        const watchId = parsed.searchParams.get('v') ?? ''
        if (path === '/watch' && youtubeIdPattern.test(watchId)) {
            return `https://www.youtube.com/embed/${watchId}?showinfo=0`
        }

        const pathParts = path.split('/').filter(Boolean)
        if (pathParts[0] === 'embed' && youtubeIdPattern.test(pathParts[1] ?? '')) {
            return `https://www.youtube.com/embed/${pathParts[1]}?showinfo=0`
        }
        if (pathParts[0] === 'shorts' && youtubeIdPattern.test(pathParts[1] ?? '')) {
            return `https://www.youtube.com/embed/${pathParts[1]}?showinfo=0`
        }
    }

    if (['youtube-nocookie.com', 'www.youtube-nocookie.com'].includes(host)) {
        const pathParts = path.split('/').filter(Boolean)
        if (pathParts[0] === 'embed' && youtubeIdPattern.test(pathParts[1] ?? '')) {
            return `https://www.youtube-nocookie.com/embed/${pathParts[1]}?showinfo=0`
        }
    }

    if (host === 'vimeo.com') {
        const id = path.split('/').filter(Boolean)[0] ?? ''
        return vimeoIdPattern.test(id) ? `https://player.vimeo.com/video/${id}` : ''
    }

    if (host === 'player.vimeo.com') {
        const pathParts = path.split('/').filter(Boolean)
        if (pathParts[0] === 'video' && vimeoIdPattern.test(pathParts[1] ?? '')) {
            return `https://player.vimeo.com/video/${pathParts[1]}`
        }
    }

    return ''
}

function getDefaultBaseOrigin(): string {
    return getWindowOrigin()
}
