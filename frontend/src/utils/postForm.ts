import { normalizeEditorFileImageUrls, normalizeLegacyFileUrls } from '@/utils/fileUrl'
import { getWindowOrigin } from '@/utils/browserEnv'
import { withServerOffset } from '@/utils/date'
import {
    encodeSandboxedPostHtml,
    expandSandboxedPostHtml,
    mapSandboxedPostHtmlPayloads,
    requiresPreservedPostHtml,
} from '@/utils/postHtmlSandbox'
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
export const POST_TITLE_MAX_LENGTH = 200
export const POST_CONTENT_MAX_LENGTH = 100_000
export const POST_STORED_CONTENT_MAX_LENGTH = 500_000
export const POST_TAG_MAX_COUNT = 10
export const POST_TAG_MAX_LENGTH = 100
export const POST_FILE_MAX_COUNT = 20
export const POST_SERIES_TITLE_MAX_LENGTH = 120

export type PostFormContentValidationError =
    | 'titleRequired'
    | 'titleTooLong'
    | 'titleContainsHtml'
    | 'contentTooLong'
    | 'tooManyTags'
    | 'tagRequired'
    | 'tagTooLong'
    | 'tooManyFiles'

function isPostContentTooLong(content: string): boolean {
    const sourceContent = expandSandboxedPostHtml(content) ?? content
    return sourceContent.length > POST_CONTENT_MAX_LENGTH
        || content.length > POST_STORED_CONTENT_MAX_LENGTH
}

export function containsUnsafePostTitleHtml(value: string): boolean {
    return /<[^>]+>|on\w+\s*=/i.test(value)
}

export function validatePostFormContent(input: {
    title: string
    content: string
    tags: string[]
    fileIds: number[]
}): PostFormContentValidationError | null {
    if (!input.title.trim()) return 'titleRequired'
    if (input.title.length > POST_TITLE_MAX_LENGTH) return 'titleTooLong'
    if (containsUnsafePostTitleHtml(input.title)) return 'titleContainsHtml'
    if (isPostContentTooLong(input.content)) return 'contentTooLong'
    if (input.tags.length > POST_TAG_MAX_COUNT) return 'tooManyTags'
    if (input.tags.some((tag) => !tag.trim())) return 'tagRequired'
    if (input.tags.some((tag) => tag.length > POST_TAG_MAX_LENGTH)) return 'tagTooLong'
    if (new Set(input.fileIds).size > POST_FILE_MAX_COUNT) return 'tooManyFiles'
    return null
}

export function validatePostDraftContent(input: {
    title: string
    content: string
    tags: string[]
    fileIds: number[]
}): Exclude<PostFormContentValidationError, 'titleRequired'> | null {
    if (input.title.length > POST_TITLE_MAX_LENGTH) return 'titleTooLong'
    if (containsUnsafePostTitleHtml(input.title)) return 'titleContainsHtml'
    if (isPostContentTooLong(input.content)) return 'contentTooLong'
    if (input.tags.length > POST_TAG_MAX_COUNT) return 'tooManyTags'
    if (input.tags.some((tag) => !tag.trim())) return 'tagRequired'
    if (input.tags.some((tag) => tag.length > POST_TAG_MAX_LENGTH)) return 'tagTooLong'
    if (new Set(input.fileIds).size > POST_FILE_MAX_COUNT) return 'tooManyFiles'
    return null
}

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
    includePoll?: boolean
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
    const expandedContent = expandSandboxedPostHtml(content) ?? content
    const doc = parser.parseFromString(expandedContent, 'text/html')

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

    doc.querySelectorAll('a[href]').forEach((link) => {
        const fileId = extractFileIdFromPostImageSrc(link.getAttribute('href') ?? '')
        if (fileId != null) {
            fileIds.add(fileId)
        }
    })

    return [...fileIds]
}

export function removePostFileReferencesFromContent(content: string, fileIds: number[]): string {
    if (!content || fileIds.length === 0 || typeof DOMParser === 'undefined') return content

    const contentWithUpdatedMarkers = mapSandboxedPostHtmlPayloads(
        content,
        (payload) => removePostFileReferencesFromHtml(payload, fileIds),
    )
    return removePostFileReferencesFromHtml(contentWithUpdatedMarkers, fileIds)
}

function removePostFileReferencesFromHtml(content: string, fileIds: number[]): string {
    const removedFileIds = new Set(fileIds)
    const parser = new DOMParser()
    const doc = parser.parseFromString(content, 'text/html')
    let changed = false

    doc.querySelectorAll<HTMLImageElement>('img').forEach((image) => {
        const dataFileId = image.getAttribute('data-file-id')
        const candidateIds = [
            dataFileId && /^\d+$/.test(dataFileId) ? Number(dataFileId) : null,
            extractFileIdFromPostImageSrc(image.getAttribute('data-server-src') ?? ''),
            extractFileIdFromPostImageSrc(image.getAttribute('src') ?? ''),
        ]
        if (!candidateIds.some((fileId) => fileId != null && removedFileIds.has(fileId))) return
        image.remove()
        changed = true
    })

    doc.querySelectorAll<HTMLAnchorElement>('a[href]').forEach((link) => {
        const fileId = extractFileIdFromPostImageSrc(link.getAttribute('href') ?? '')
        if (fileId == null || !removedFileIds.has(fileId)) return
        link.replaceWith(...Array.from(link.childNodes))
        changed = true
    })

    return changed ? doc.body.innerHTML : content
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
    includePoll,
}: BuildPostFormPayloadOptions) {
    const normalizedContents = requiresPreservedPostHtml(form.content)
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
    const poll = (includePoll ?? mode === 'create') ? normalizePostFormPoll(form.poll) : null

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
    const draftValidationError = validatePostDraftPoll(poll)
    if (draftValidationError) return draftValidationError
    if (!poll?.closesAt) return null

    // 두 값은 `datetime-local` 입력이 만든 서버 기준(KST) 벽시계다. offset이 없어
    // 그냥 `new Date`로 읽으면 기기 지역으로 해석되어, KST 밖 사용자는 실제로는
    // 미래인 마감 시각을 "현재보다 이전"이라며 거부당한다(UTC+14면 최대 5시간).
    const closesAt = new Date(withServerOffset(poll.closesAt)).getTime()
    if (!Number.isFinite(closesAt) || closesAt <= now) return 'closesAtFuture'

    const scheduledTime = scheduledAt ? new Date(withServerOffset(scheduledAt)).getTime() : Number.NaN
    if (Number.isFinite(scheduledTime) && closesAt <= scheduledTime + 60_000) {
        return 'closesAtAfterSchedule'
    }
    return null
}

export function validatePostDraftPoll(
    poll?: PostFormPoll | null,
): Exclude<PostFormPollValidationError, 'closesAtFuture' | 'closesAtAfterSchedule'> | null {
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

    return null
}

export function validatePostDraftPollContract(
    poll?: Pick<PostFormPoll, 'question' | 'options'> | null,
): Extract<PostFormPollValidationError, 'questionTooLong' | 'optionCount' | 'optionTooLong'> | null {
    if (!poll) return null
    if (poll.question.length > POST_POLL_QUESTION_MAX_LENGTH) return 'questionTooLong'
    if (poll.options.length > POST_POLL_MAX_OPTIONS) return 'optionCount'
    if (poll.options.some((option) => option.length > POST_POLL_OPTION_MAX_LENGTH)) return 'optionTooLong'
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
