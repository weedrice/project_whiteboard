import { describe, expect, it } from 'vitest'
import {
  isValidDraftCrossTabEventEnvelope,
} from '@/features/board/posts/draft/postDraftCrossTabChannel'

const NOW = Date.UTC(2026, 7, 5, 10, 0, 0)
const VALID_EVENT = {
  type: 'draft-updated',
  eventId: 'event-1',
  sourceId: 'source-1',
  at: NOW,
}

describe('draft cross-tab event envelope', () => {
  it('accepts a current event with the expected type', () => {
    expect(isValidDraftCrossTabEventEnvelope(
      VALID_EVENT,
      'draft-updated',
      NOW,
    )).toBe(true)
  })

  it('rejects expired and excessively future-dated events', () => {
    expect(isValidDraftCrossTabEventEnvelope(
      { ...VALID_EVENT, at: NOW - 60_001 },
      'draft-updated',
      NOW,
    )).toBe(false)
    expect(isValidDraftCrossTabEventEnvelope(
      { ...VALID_EVENT, at: NOW + 5_001 },
      'draft-updated',
      NOW,
    )).toBe(false)
  })

  it('rejects events for another domain channel', () => {
    expect(isValidDraftCrossTabEventEnvelope(
      VALID_EVENT,
      'draft-scheduled',
      NOW,
    )).toBe(false)
  })

  it('rejects missing or oversized event identifiers', () => {
    expect(isValidDraftCrossTabEventEnvelope(
      { ...VALID_EVENT, eventId: '' },
      'draft-updated',
      NOW,
    )).toBe(false)
    expect(isValidDraftCrossTabEventEnvelope(
      { ...VALID_EVENT, sourceId: 'x'.repeat(129) },
      'draft-updated',
      NOW,
    )).toBe(false)
  })
})
