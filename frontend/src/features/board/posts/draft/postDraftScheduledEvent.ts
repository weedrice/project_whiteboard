import {
  closeDraftSessionChannelForTest,
  publishDraftSessionEvent,
  registerDraftSessionListener,
  type DraftScheduledSessionEvent,
} from '@/features/board/posts/draft/postDraftSessionChannel'

type DraftScheduledListener = (message: DraftScheduledSessionEvent) => void

export function publishDraftScheduledEvent(
  ownerId: string | number,
  draftId: number | null,
  clientDraftKey: string | null,
) {
  publishDraftSessionEvent({
    type: 'draft-scheduled',
    ownerId: String(ownerId),
    draftId,
    clientDraftKey,
  })
}

export function matchesDraftScheduledEvent(
  message: DraftScheduledSessionEvent,
  ownerId: string | number | null | undefined,
  draftId: number | null,
  clientDraftKey: string | null,
) {
  if (ownerId == null || message.ownerId !== String(ownerId)) return false
  if (message.draftId != null && message.draftId === draftId) return true
  if (message.clientDraftKey != null && clientDraftKey != null) {
    return message.clientDraftKey === clientDraftKey
  }
  return false
}

export function registerDraftScheduledListener(listener: DraftScheduledListener): () => void {
  return registerDraftSessionListener((event) => {
    if (event.type === 'draft-scheduled') listener(event)
  })
}

export function closeDraftScheduledChannelForTest() {
  closeDraftSessionChannelForTest()
}
