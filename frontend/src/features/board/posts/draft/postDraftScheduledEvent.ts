import { isValidDraftClientIdentifier } from '@/features/board/posts/draft/postDraftContract'
import {
  createDraftCrossTabChannel,
  isValidDraftCrossTabEventEnvelope,
  type DraftCrossTabEventBase,
} from '@/features/board/posts/draft/postDraftCrossTabChannel'

const DRAFT_SCHEDULED_CHANNEL = 'noviis-draft-scheduled'
const DRAFT_SCHEDULED_EVENT_KEY = 'noviis:draft-scheduled-event'

export interface DraftScheduledEvent extends DraftCrossTabEventBase {
  type: 'draft-scheduled'
  ownerId: string
  draftId: number | null
  clientDraftKey: string | null
  storageKey: string
}

type DraftScheduledListener = (message: DraftScheduledEvent) => void

function parseDraftScheduledEvent(value: unknown): DraftScheduledEvent | null {
  if (!isValidDraftCrossTabEventEnvelope(value, 'draft-scheduled')) return null
  const message = value as Partial<DraftScheduledEvent>
  if (typeof message.ownerId !== 'string'
    || message.ownerId.length === 0
    || message.ownerId.length > 64
    || (message.draftId !== null
      && (typeof message.draftId !== 'number'
        || !Number.isInteger(message.draftId)
        || message.draftId <= 0))
    || (message.clientDraftKey !== null
      && (typeof message.clientDraftKey !== 'string'
        || !isValidDraftClientIdentifier(message.clientDraftKey)))
    || typeof message.storageKey !== 'string'
    || message.storageKey.length === 0
    || message.storageKey.length > 512) return null
  return message as DraftScheduledEvent
}

const draftScheduledChannel = createDraftCrossTabChannel<DraftScheduledEvent>({
  channelName: DRAFT_SCHEDULED_CHANNEL,
  storageKey: DRAFT_SCHEDULED_EVENT_KEY,
  globalStateKey: '__noviisDraftScheduledChannel__',
  parseEvent: parseDraftScheduledEvent,
})

export function publishDraftScheduledEvent(
  ownerId: string | number,
  draftId: number | null,
  clientDraftKey: string | null,
  storageKey: string,
) {
  draftScheduledChannel.publish({
    type: 'draft-scheduled',
    ownerId: String(ownerId),
    draftId,
    clientDraftKey,
    storageKey,
  })
}

export function parseDraftScheduledStorageEvent(event: StorageEvent): DraftScheduledEvent | null {
  return draftScheduledChannel.parseStorageEvent(event)
}

export function matchesDraftScheduledEvent(
  message: DraftScheduledEvent,
  ownerId: string | number | null | undefined,
  draftId: number | null,
  clientDraftKey: string | null,
  storageKey: string,
) {
  if (ownerId == null || message.ownerId !== String(ownerId)) return false
  if (message.draftId != null && message.draftId === draftId) return true
  if (message.clientDraftKey != null && clientDraftKey != null) {
    return message.clientDraftKey === clientDraftKey
  }
  return message.storageKey === storageKey
}

export function registerDraftScheduledListener(listener: DraftScheduledListener): () => void {
  return draftScheduledChannel.register(listener)
}

export function closeDraftScheduledChannelForTest() {
  draftScheduledChannel.closeForTest()
}
