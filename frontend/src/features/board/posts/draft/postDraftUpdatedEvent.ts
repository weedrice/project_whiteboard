import {
  createDraftCrossTabChannel,
  isValidDraftCrossTabEventEnvelope,
  type DraftCrossTabEventBase,
} from '@/features/board/posts/draft/postDraftCrossTabChannel'

const DRAFT_UPDATED_CHANNEL = 'noviis-draft-updated'
const DRAFT_UPDATED_EVENT_KEY = 'noviis:draft-updated-event'

interface DraftUpdatedEvent extends DraftCrossTabEventBase {
  type: 'draft-updated'
  ownerId: string
  draftId: number
  clientDraftKey: string
  version: number | null
  updatedAt: string
  contentFingerprint: string
}

type DraftUpdatedListener = (message: DraftUpdatedEvent) => void
type DraftUpdatedPayload = Omit<DraftUpdatedEvent, keyof DraftCrossTabEventBase | 'type' | 'ownerId'>

function parseDraftUpdatedEvent(value: unknown): DraftUpdatedEvent | null {
  if (!isValidDraftCrossTabEventEnvelope(value, 'draft-updated')) return null
  const message = value as Partial<DraftUpdatedEvent>
  const validVersion = message.version === null
    || (typeof message.version === 'number' && Number.isInteger(message.version) && message.version >= 0)
  if (typeof message.ownerId !== 'string'
    || message.ownerId.length === 0
    || message.ownerId.length > 64
    || typeof message.draftId !== 'number'
    || !Number.isInteger(message.draftId)
    || message.draftId <= 0
    || typeof message.clientDraftKey !== 'string'
    || message.clientDraftKey.length === 0
    || message.clientDraftKey.length > 128
    || !validVersion
    || typeof message.updatedAt !== 'string'
    || !Number.isFinite(Date.parse(message.updatedAt))
    || typeof message.contentFingerprint !== 'string'
    || message.contentFingerprint.length === 0
    || message.contentFingerprint.length > 128) return null
  return {
    type: 'draft-updated',
    eventId: message.eventId!,
    sourceId: message.sourceId!,
    at: message.at!,
    ownerId: message.ownerId,
    draftId: message.draftId,
    clientDraftKey: message.clientDraftKey,
    version: message.version as number | null,
    updatedAt: message.updatedAt,
    contentFingerprint: message.contentFingerprint,
  }
}

const draftUpdatedChannel = createDraftCrossTabChannel<DraftUpdatedEvent>({
  channelName: DRAFT_UPDATED_CHANNEL,
  storageKey: DRAFT_UPDATED_EVENT_KEY,
  globalStateKey: '__noviisDraftUpdatedChannel__',
  parseEvent: parseDraftUpdatedEvent,
})

export function publishDraftUpdatedEvent(
  ownerId: string | number,
  payload: DraftUpdatedPayload,
) {
  draftUpdatedChannel.publish({
    type: 'draft-updated',
    ownerId: String(ownerId),
    ...payload,
  })
}

export function registerDraftUpdatedListener(listener: DraftUpdatedListener): () => void {
  return draftUpdatedChannel.register(listener)
}

export function closeDraftUpdatedChannelForTest() {
  draftUpdatedChannel.closeForTest()
}
