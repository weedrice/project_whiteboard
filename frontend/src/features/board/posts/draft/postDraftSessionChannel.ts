import {
  createDraftCrossTabChannel,
  isValidDraftCrossTabEventEnvelope,
  type DraftCrossTabEventBase,
} from '@/features/board/posts/draft/postDraftCrossTabChannel'
import { isValidDraftClientIdentifier } from '@/features/board/posts/draft/postDraftContract'

const DRAFT_SESSION_CHANNEL = 'noviis-draft-session-v2'
const DRAFT_SESSION_EVENT_KEY = 'noviis:draft-session-event:v2'

interface DraftSessionEventCommon extends DraftCrossTabEventBase {
  ownerId: string
}

export interface DraftUpdatedSessionEvent extends DraftSessionEventCommon {
  type: 'draft-updated'
  draftId: number
  clientDraftKey: string
  version: number | null
  updatedAt: string
  contentFingerprint: string
}

export interface DraftDeletedSessionEvent extends DraftSessionEventCommon {
  type: 'draft-deleted'
  draftId: string
}

export interface DraftScheduledSessionEvent extends DraftSessionEventCommon {
  type: 'draft-scheduled'
  draftId: number | null
  clientDraftKey: string | null
}

export type DraftSessionEvent =
  | DraftUpdatedSessionEvent
  | DraftDeletedSessionEvent
  | DraftScheduledSessionEvent

type DraftSessionEventPayload = DraftSessionEvent extends infer TEvent
  ? TEvent extends DraftSessionEvent
    ? Omit<TEvent, keyof DraftCrossTabEventBase>
    : never
  : never

function isValidOwnerId(value: unknown): value is string {
  return typeof value === 'string' && value.length > 0 && value.length <= 64
}

function parseDraftSessionEvent(value: unknown): DraftSessionEvent | null {
  if (!value || typeof value !== 'object') return null
  const candidate = value as Partial<DraftSessionEvent>
  if (typeof candidate.type !== 'string'
    || !isValidDraftCrossTabEventEnvelope(value, candidate.type)
    || !isValidOwnerId(candidate.ownerId)) return null

  if (candidate.type === 'draft-updated') {
    const event = candidate as Partial<DraftUpdatedSessionEvent>
    const validVersion = event.version === null
      || (typeof event.version === 'number' && Number.isInteger(event.version) && event.version >= 0)
    if (typeof event.draftId !== 'number'
      || !Number.isInteger(event.draftId)
      || event.draftId <= 0
      || typeof event.clientDraftKey !== 'string'
      || !isValidDraftClientIdentifier(event.clientDraftKey)
      || !validVersion
      || typeof event.updatedAt !== 'string'
      || !Number.isFinite(Date.parse(event.updatedAt))
      || typeof event.contentFingerprint !== 'string'
      || event.contentFingerprint.length === 0
      || event.contentFingerprint.length > 128) return null
    return {
      type: 'draft-updated',
      eventId: event.eventId!,
      sourceId: event.sourceId!,
      at: event.at!,
      ownerId: event.ownerId!,
      draftId: event.draftId,
      clientDraftKey: event.clientDraftKey,
      version: event.version as number | null,
      updatedAt: event.updatedAt,
      contentFingerprint: event.contentFingerprint,
    }
  }

  if (candidate.type === 'draft-deleted') {
    const event = candidate as Partial<DraftDeletedSessionEvent>
    if (typeof event.draftId !== 'string'
      || event.draftId.length === 0
      || event.draftId.length > 64) return null
    return {
      type: 'draft-deleted',
      eventId: event.eventId!,
      sourceId: event.sourceId!,
      at: event.at!,
      ownerId: event.ownerId!,
      draftId: event.draftId,
    }
  }

  if (candidate.type === 'draft-scheduled') {
    const event = candidate as Partial<DraftScheduledSessionEvent>
    if ((event.draftId !== null
        && (typeof event.draftId !== 'number'
          || !Number.isInteger(event.draftId)
          || event.draftId <= 0))
      || (event.clientDraftKey !== null
        && (typeof event.clientDraftKey !== 'string'
          || !isValidDraftClientIdentifier(event.clientDraftKey)))) return null
    return {
      type: 'draft-scheduled',
      eventId: event.eventId!,
      sourceId: event.sourceId!,
      at: event.at!,
      ownerId: event.ownerId!,
      draftId: event.draftId,
      clientDraftKey: event.clientDraftKey,
    }
  }

  return null
}

const draftSessionChannel = createDraftCrossTabChannel<DraftSessionEvent>({
  channelName: DRAFT_SESSION_CHANNEL,
  storageKey: DRAFT_SESSION_EVENT_KEY,
  globalStateKey: '__noviisDraftSessionChannelV2__',
  parseEvent: parseDraftSessionEvent,
})

export function publishDraftSessionEvent(payload: DraftSessionEventPayload) {
  return draftSessionChannel.publish(payload)
}

export function registerDraftSessionListener(listener: (event: DraftSessionEvent) => void) {
  return draftSessionChannel.register(listener)
}

export function closeDraftSessionChannelForTest() {
  draftSessionChannel.closeForTest()
}
