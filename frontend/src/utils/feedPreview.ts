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

const getYouTubeVideoId = (url: URL): string | null => {
  if (url.hostname === 'youtu.be') {
    return url.pathname.split('/').filter(Boolean)[0] ?? null
  }

  if (!url.hostname.endsWith('youtube.com') && !url.hostname.endsWith('youtube-nocookie.com')) {
    return null
  }

  if (url.pathname.startsWith('/embed/')) {
    return url.pathname.split('/').filter(Boolean)[1] ?? null
  }

  if (url.pathname.startsWith('/shorts/')) {
    return url.pathname.split('/').filter(Boolean)[1] ?? null
  }

  return url.searchParams.get('v')
}

const getVimeoVideoId = (url: URL): string | null => {
  if (!url.hostname.endsWith('vimeo.com')) {
    return null
  }

  const parts = url.pathname.split('/').filter(Boolean)
  if (parts[0] === 'video' && parts[1]) {
    return parts[1]
  }

  return parts.find((part) => /^\d+$/.test(part)) ?? null
}

export const toEmbeddableVideoUrl = (source?: string | null): string | null => {
  if (!source) return null

  try {
    const url = new URL(source)
    if (url.protocol !== 'https:' && url.protocol !== 'http:') {
      return null
    }

    const youtubeId = getYouTubeVideoId(url)
    if (youtubeId) {
      return `https://www.youtube-nocookie.com/embed/${encodeURIComponent(youtubeId)}`
    }

    const vimeoId = getVimeoVideoId(url)
    if (vimeoId) {
      return `https://player.vimeo.com/video/${encodeURIComponent(vimeoId)}`
    }

    if (url.pathname.includes('/embed/')) {
      return url.toString()
    }
  } catch {
    return null
  }

  return null
}

export const getFeedMediaPreview = (post: Pick<FeedPost, 'firstMediaType' | 'firstMediaUrl' | 'thumbnailUrl'>) => {
  const videoUrl = post.firstMediaType === 'video'
    ? toEmbeddableVideoUrl(post.firstMediaUrl)
    : null

  return {
    showFirstVideo: !!videoUrl,
    videoUrl,
    imageUrl: post.firstMediaType === 'image' && post.firstMediaUrl ? post.firstMediaUrl : post.thumbnailUrl,
  }
}

export const isFeedSpoiler = (post: Pick<FeedPost, 'isSpoiler'> & { spoiler?: boolean }) => {
  return Boolean(post.isSpoiler ?? post.spoiler)
}

export const buildPostDetailPath = (boardUrl: string | number, postId: string | number, hash = '') => {
  return `/board/${boardUrl}/post/${postId}${hash}`
}
