import type { CommentStreamEvent } from '@/api/notification'

export const COMMENT_STREAM_EVENT_NAME = 'noviis:comment-stream-event'

export function emitCommentStreamEvent(event: CommentStreamEvent) {
    if (typeof window === 'undefined') return
    window.dispatchEvent(new CustomEvent<CommentStreamEvent>(COMMENT_STREAM_EVENT_NAME, { detail: event }))
}

export function onCommentStreamEvent(handler: (event: CommentStreamEvent) => void) {
    if (typeof window === 'undefined') {
        return () => {}
    }

    const listener = (event: Event) => {
        handler((event as CustomEvent<CommentStreamEvent>).detail)
    }
    window.addEventListener(COMMENT_STREAM_EVENT_NAME, listener)
    return () => window.removeEventListener(COMMENT_STREAM_EVENT_NAME, listener)
}
