import { sanitizeHtml } from '@/utils/sanitize'

const EMOTICON_PATTERN = /!\[emoticon\]\(([^)]+)\)/g
const EMOTICON_ONLY_PATTERN = /^!\[emoticon\]\([^)]+\)$/

function escapeHtmlAttribute(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
}

function normalizeEmoticonUrl(rawUrl: string): string | null {
    const trimmed = rawUrl.trim()
    if (!trimmed) return null
    if (trimmed.startsWith('/')) return trimmed
    if (/^https?:\/\//i.test(trimmed)) return trimmed
    return null
}

export function renderCommentContentHtml(content: string | undefined, className = 'comment-emoticon'): string {
    if (!content) return ''

    const replaced = content.replace(EMOTICON_PATTERN, (_match, rawUrl: string) => {
        const safeUrl = normalizeEmoticonUrl(rawUrl)
        if (!safeUrl) return ''
        return `<img src="${escapeHtmlAttribute(safeUrl)}" class="${escapeHtmlAttribute(className)}" alt="emoticon" loading="lazy" />`
    })

    return sanitizeHtml(replaced)
}

export function isEmoticonOnlyContent(content: string | undefined): boolean {
    if (!content) return false
    return EMOTICON_ONLY_PATTERN.test(content.trim())
}
