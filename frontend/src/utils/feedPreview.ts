import type { FeedPost } from '@/types'
import { asSanitizedHtml, sanitizeQuillHtml, type SanitizedHtml } from '@/utils/sanitize'

const HTML_TAG_PATTERN = /<[a-z][\s\S]*>/i

const escapeHtml = (value: string) =>
  value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

const normalizePlainTextExcerpt = (value: string) => {
  if (HTML_TAG_PATTERN.test(value)) {
    return value
  }

  return value
    .trim()
    .split(/(?:\r?\n){2,}/)
    .map((paragraph) => paragraph.trim())
    .filter(Boolean)
    .map((paragraph) => `<p>${escapeHtml(paragraph).replace(/\r?\n/g, '<br>')}</p>`)
    .join('')
}

export const getFeedBodyHtml = (post: Pick<FeedPost, 'contentsExcerpt' | 'summary'>): SanitizedHtml | null => {
  const excerpt = post.contentsExcerpt || post.summary
  if (!excerpt) return null

  let html: string = sanitizeQuillHtml(normalizePlainTextExcerpt(excerpt))
  html = html.replace(/<img[^>]*>/gi, '')
  html = html.replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
  html = html.replace(/<div[^>]*\bclass="[^"]*tiptap-video-wrapper[^"]*"[^>]*>[\s\S]*?<\/div>/gi, '')

  const textOnly = html.replace(/<[^>]+>/g, '').trim()
  if (!textOnly) return null
  return asSanitizedHtml(html)
}

export const getFeedMediaPreview = (post: Pick<FeedPost, 'firstMediaType' | 'firstMediaUrl' | 'thumbnailUrl'>) => {
  return {
    showFirstVideo: post.firstMediaType === 'video' && !!post.firstMediaUrl,
    imageUrl: post.firstMediaType === 'image' && post.firstMediaUrl ? post.firstMediaUrl : post.thumbnailUrl,
  }
}

export const isFeedSpoiler = (post: Pick<FeedPost, 'isSpoiler'> & { spoiler?: boolean }) => {
  return Boolean(post.isSpoiler ?? post.spoiler)
}

export const buildPostDetailPath = (boardUrl: string | number, postId: string | number, hash = '') => {
  return `/board/${boardUrl}/post/${postId}${hash}`
}
