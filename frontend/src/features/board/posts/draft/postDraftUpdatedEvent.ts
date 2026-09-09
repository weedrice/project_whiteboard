import {
  closeDraftSessionChannelForTest,
  publishDraftSessionEvent,
  registerDraftSessionListener,
  type DraftUpdatedSessionEvent,
} from '@/features/board/posts/draft/postDraftSessionChannel'

type DraftUpdatedListener = (message: DraftUpdatedSessionEvent) => void
type DraftUpdatedPayload = Omit<DraftUpdatedSessionEvent,
  'type' | 'ownerId' | 'eventId' | 'sourceId' | 'at'>

export function publishDraftUpdatedEvent(
  ownerId: string | number,
  payload: DraftUpdatedPayload,
) {
  publishDraftSessionEvent({
    type: 'draft-updated',
    ownerId: String(ownerId),
    ...payload,
  })
}

export function registerDraftUpdatedListener(listener: DraftUpdatedListener): () => void {
  return registerDraftSessionListener((event) => {
    if (event.type === 'draft-updated') listener(event)
  })
}

export function closeDraftUpdatedChannelForTest() {
  closeDraftSessionChannelForTest()
}
