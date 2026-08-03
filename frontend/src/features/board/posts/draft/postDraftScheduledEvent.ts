const DRAFT_SCHEDULED_EVENT_KEY = 'noviis:draft-scheduled-event'

export interface DraftScheduledEvent {
  type: 'draft-scheduled'
  ownerId: string
  draftId: number | null
  storageKey: string
  at: number
}

export function publishDraftScheduledEvent(
  ownerId: string | number,
  draftId: number | null,
  storageKey: string,
) {
  const message: DraftScheduledEvent = {
    type: 'draft-scheduled',
    ownerId: String(ownerId),
    draftId,
    storageKey,
    at: Date.now(),
  }
  try {
    localStorage.setItem(DRAFT_SCHEDULED_EVENT_KEY, JSON.stringify(message))
    localStorage.removeItem(DRAFT_SCHEDULED_EVENT_KEY)
  } catch {
    // The current tab still transitions locally; cross-tab delivery is best effort.
  }
}

export function parseDraftScheduledStorageEvent(event: StorageEvent): DraftScheduledEvent | null {
  if (event.key !== DRAFT_SCHEDULED_EVENT_KEY || !event.newValue) return null
  try {
    const value = JSON.parse(event.newValue) as Partial<DraftScheduledEvent>
    if (value.type !== 'draft-scheduled'
      || typeof value.ownerId !== 'string'
      || (value.draftId !== null && (typeof value.draftId !== 'number' || !Number.isInteger(value.draftId)))
      || typeof value.storageKey !== 'string'
      || typeof value.at !== 'number') return null
    return value as DraftScheduledEvent
  } catch {
    return null
  }
}

export function matchesDraftScheduledEvent(
  message: DraftScheduledEvent,
  ownerId: string | number | null | undefined,
  draftId: number | null,
  storageKey: string,
) {
  if (ownerId == null || message.ownerId !== String(ownerId)) return false
  return message.storageKey === storageKey
    || (message.draftId != null && message.draftId === draftId)
}
