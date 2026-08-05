import {
  createDraftCrossTabChannel,
  isValidDraftCrossTabEventEnvelope,
  type DraftCrossTabEventBase,
} from '@/features/board/posts/draft/postDraftCrossTabChannel'
import {
  parseDraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftLifecycle'
import type {
  DraftRecoverySnapshot,
} from '@/features/board/posts/draft/postDraftRecovery'

const DRAFT_UPDATED_CHANNEL = 'noviis-draft-updated'
const DRAFT_UPDATED_EVENT_KEY = 'noviis:draft-updated-event'

export interface DraftUpdatedEvent extends DraftCrossTabEventBase {
  type: 'draft-updated'
  ownerId: string
  storageKey: string
  snapshot: DraftRecoverySnapshot
}

type DraftUpdatedListener = (message: DraftUpdatedEvent) => void

function parseDraftUpdatedEvent(value: unknown): DraftUpdatedEvent | null {
  if (!isValidDraftCrossTabEventEnvelope(value, 'draft-updated')) return null
  const message = value as Partial<DraftUpdatedEvent>
  const snapshot = parseDraftRecoverySnapshot(message.snapshot)
  if (typeof message.ownerId !== 'string'
    || message.ownerId.length === 0
    || message.ownerId.length > 64
    || typeof message.storageKey !== 'string'
    || message.storageKey.length === 0
    || message.storageKey.length > 512
    || !snapshot) return null
  return { ...message, snapshot } as DraftUpdatedEvent
}

const draftUpdatedChannel = createDraftCrossTabChannel<DraftUpdatedEvent>({
  channelName: DRAFT_UPDATED_CHANNEL,
  storageKey: DRAFT_UPDATED_EVENT_KEY,
  globalStateKey: '__noviisDraftUpdatedChannel__',
  parseEvent: parseDraftUpdatedEvent,
})

export function publishDraftUpdatedEvent(
  ownerId: string | number,
  storageKey: string,
  snapshot: DraftRecoverySnapshot,
) {
  draftUpdatedChannel.publish({
    type: 'draft-updated',
    ownerId: String(ownerId),
    storageKey,
    snapshot,
  })
}

export function registerDraftUpdatedListener(listener: DraftUpdatedListener): () => void {
  return draftUpdatedChannel.register(listener)
}

export function closeDraftUpdatedChannelForTest() {
  draftUpdatedChannel.closeForTest()
}
