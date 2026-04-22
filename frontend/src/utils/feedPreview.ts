import type { FeedPost } from '@/types'
import { sanitizeQuillHtml } from '@/utils/sanitize'

export const getFeedBodyHtml = (post: Pick<FeedPost, 'contentsExcerpt'>) => {
  const excerpt = post.contentsExcerpt
  if (!excerpt) return null

  let html = sanitizeQuillHtml(excerpt)
  html = html.replace(/<img[^>]*>/gi, '')
  html = html.replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
  html = html.replace(/<div[^>]*\bclass="[^"]*tiptap-video-wrapper[^"]*"[^>]*>[\s\S]*?<\/div>/gi, '')

  const textOnly = html.replace(/<[^>]+>/g, '').trim()
  if (!textOnly) return null
  return html
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
