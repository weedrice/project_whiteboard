import { normalizeEditorFileImageUrls, normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { getWindowOrigin } from '@/utils/browserEnv'
import { encodeSandboxedPostHtml, requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import type { PollPayload } from '@/api/post'

export type PostFormFileIdScope = 'content' | 'draft'

export type PostFormPoll = {
    question: string
    options: string[]
    multipleChoiceEnabled: boolean
    anonymousEnabled: boolean
    closesAt: string | null
}

export const POST_POLL_QUESTION_MAX_LENGTH = 200
export const POST_POLL_OPTION_MAX_LENGTH = 100
export const POST_POLL_MIN_OPTIONS = 2
export const POST_POLL_MAX_OPTIONS = 10

export type PostFormPollValidationError =
    | 'questionRequired'
    | 'questionTooLong'
    | 'optionRequired'
    | 'optionCount'
    | 'optionTooLong'
    | 'closesAtFuture'
    | 'closesAtAfterSchedule'

export type PostFormPayloadForm = {
    title: string
    content: string
    categoryId: string | number
    tags: string[]
    isNsfw: boolean
    isSpoiler: boolean
    isNotice: boolean
    isSecret: boolean
    seriesId?: string | number
    poll?: PostFormPoll | null
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
    const parsedSeriesId = form.seriesId == null || form.seriesId === ''
        ? undefined
        : (typeof form.seriesId === 'string' ? Number.parseInt(form.seriesId, 10) : form.seriesId)
    const seriesId = parsedSeriesId != null && !Number.isNaN(parsedSeriesId) && parsedSeriesId > 0
        ? parsedSeriesId
        : (mode === 'edit' ? null : undefined)
    const poll = mode === 'create' ? normalizePostFormPoll(form.poll) : null

    return {
        title: form.title.trim(),
        ...(categoryId !== undefined && { categoryId }),
        tags: hideTags ? [] : form.tags,
        contents,
        isNsfw: canShowNsfw ? form.isNsfw : false,
        isSpoiler: hideSpoiler ? false : form.isSpoiler,
        isSecret: hideSecret ? false : form.isSecret,
        ...(showNotice && { isNotice: form.isNotice }),
        ...(seriesId !== undefined && { seriesId }),
        ...(poll && { poll }),
        fileIds,
    }
}

export function createEmptyPostFormPoll(): PostFormPoll {
    return {
        question: '',
        options: ['', ''],
        multipleChoiceEnabled: false,
        anonymousEnabled: false,
        closesAt: null,
    }
}

export function normalizePostFormPoll(poll?: PostFormPoll | null): PollPayload | null {
    if (!poll) return null
    const question = poll.question.trim()
    const options = poll.options.map((option) => option.trim()).filter(Boolean)
    if (!question || options.length < 2) return null

    return {
        question,
        options: options.slice(0, 10),
        multipleChoiceEnabled: poll.multipleChoiceEnabled,
        anonymousEnabled: poll.anonymousEnabled,
        closesAt: poll.closesAt || null,
    }
}

export function validatePostFormPoll(
    poll?: PostFormPoll | null,
    now = Date.now(),
    scheduledAt?: string | null,
): PostFormPollValidationError | null {
    if (!poll) return null

    const question = poll.question.trim()
    if (!question) return 'questionRequired'
    if (question.length > POST_POLL_QUESTION_MAX_LENGTH) return 'questionTooLong'
    if (poll.options.length < POST_POLL_MIN_OPTIONS || poll.options.length > POST_POLL_MAX_OPTIONS) {
        return 'optionCount'
    }

    const options = poll.options.map((option) => option.trim())
    if (options.some((option) => !option)) return 'optionRequired'
    if (options.some((option) => option.length > POST_POLL_OPTION_MAX_LENGTH)) return 'optionTooLong'

    if (poll.closesAt) {
        const closesAt = new Date(poll.closesAt).getTime()
        if (!Number.isFinite(closesAt) || closesAt <= now) return 'closesAtFuture'

        const scheduledTime = scheduledAt ? new Date(scheduledAt).getTime() : Number.NaN
        if (Number.isFinite(scheduledTime) && closesAt <= scheduledTime + 60_000) {
            return 'closesAtAfterSchedule'
        }
    }

    return null
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
