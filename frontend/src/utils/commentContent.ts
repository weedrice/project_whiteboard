import { asSanitizedHtml, sanitizeHtml } from '@/utils/sanitize'
import { normalizeFileUrl } from '@/utils/fileUrl'
import { escapeHtmlAttribute, escapeHtmlText } from '@/utils/htmlEscape'
import type { SanitizedHtml } from '@/utils/sanitize'
import type { CommentMention } from '@/types'

const EMOTICON_PATTERN = /!\[emoticon\]\(([^)]+)\)/g
const EMOTICON_ONLY_PATTERN = /^!\[emoticon\]\([^)]+\)$/

function normalizeEmoticonUrl(rawUrl: string): string | null {
    const trimmed = rawUrl.trim()
    if (!trimmed) return null
    if (trimmed.startsWith('/')) return normalizeFileUrl(trimmed)
    if (/^https?:\/\//i.test(trimmed)) return trimmed
    return null
}

function sanitizeCommentText(value: string): string {
    return String(sanitizeHtml(value, {
        ALLOWED_TAGS: [],
        ALLOWED_ATTR: [],
    }))
}

function renderMentionHtml(mention: CommentMention): string {
    return `<span class="comment-mention" role="link" tabindex="0" data-mention-user-id="${escapeHtmlAttribute(String(mention.userId))}">@${escapeHtmlText(mention.displayName)}</span>`
}

function renderTextWithMentions(text: string, mentions: CommentMention[]): string {
    const safeText = sanitizeCommentText(text)
    const sortedMentions = [...mentions]
        .filter((mention) => mention.userId > 0 && mention.displayName.trim())
        .sort((first, second) => second.displayName.length - first.displayName.length)
    if (sortedMentions.length === 0) return escapeHtmlText(safeText)

    let index = 0
    let html = ''
    while (index < safeText.length) {
        const match = sortedMentions.find((mention) => safeText.startsWith(`@${mention.displayName}`, index))
        if (!match) {
            html += escapeHtmlText(safeText[index] ?? '')
            index += 1
            continue
        }
        html += renderMentionHtml(match)
        index += match.displayName.length + 1
    }
    return html
}

export function renderCommentContentHtml(
    content: string | null | undefined,
    className = 'comment-emoticon',
    mentions: CommentMention[] = []
): SanitizedHtml {
    if (!content) return asSanitizedHtml('')

    let rendered = ''
    let lastIndex = 0

    content.replace(EMOTICON_PATTERN, (match, rawUrl: string, offset: number) => {
        rendered += renderTextWithMentions(content.slice(lastIndex, offset), mentions)
        const safeUrl = normalizeEmoticonUrl(rawUrl)
        if (safeUrl) {
            rendered += `<img src="${escapeHtmlAttribute(safeUrl)}" class="${escapeHtmlAttribute(className)}" alt="emoticon" loading="lazy" />`
        }
        lastIndex = offset + match.length
        return match
    })
    rendered += renderTextWithMentions(content.slice(lastIndex), mentions)

    const withLineBreaks = rendered.replace(/\r\n|\r|\n/g, '<br />')

    return sanitizeHtml(withLineBreaks)
}

export function isEmoticonOnlyContent(content: string | null | undefined): boolean {
    if (!content) return false
    return EMOTICON_ONLY_PATTERN.test(content.trim())
}
