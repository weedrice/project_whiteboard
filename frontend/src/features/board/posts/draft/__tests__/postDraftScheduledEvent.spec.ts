import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  closeDraftScheduledChannelForTest,
  matchesDraftScheduledEvent,
  publishDraftScheduledEvent,
  registerDraftScheduledListener,
} from '@/features/board/posts/draft/postDraftScheduledEvent'

describe('draft scheduled cross-tab channel', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-04T00:00:00.000Z'))
  })

  afterEach(() => {
    closeDraftScheduledChannelForTest()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    localStorage.clear()
    vi.useRealTimers()
  })

  it('delivers through BroadcastChannel when localStorage is blocked', () => {
    const channels: FakeBroadcastChannel[] = []
    class FakeBroadcastChannel {
      listeners: Array<(event: MessageEvent) => void> = []
      constructor(readonly name: string) {
        channels.push(this)
      }
      addEventListener(_type: string, listener: (event: MessageEvent) => void) {
        this.listeners.push(listener)
      }
      postMessage(message: unknown) {
        channels.filter((candidate) => candidate !== this && candidate.name === this.name)
          .forEach((candidate) => candidate.listeners.forEach((listener) => listener({ data: message } as MessageEvent)))
      }
      close() {}
    }
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    registerDraftScheduledListener(() => undefined)
    const peer = new FakeBroadcastChannel('noviis-draft-scheduled')
    const received: unknown[] = []
    peer.addEventListener('message', (event) => received.push(event.data))
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })

    publishDraftScheduledEvent(1, 91, 'client-draft-key-1234', 'noviis:draft:1:edit:91')

    expect(received).toHaveLength(1)
    expect(received[0]).toMatchObject({
      type: 'draft-scheduled',
      ownerId: '1',
      draftId: 91,
      clientDraftKey: 'client-draft-key-1234',
    })
  })

  it('deduplicates storage copies and rejects expired events', () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    const listener = vi.fn()
    registerDraftScheduledListener(listener)
    const message = {
      type: 'draft-scheduled',
      eventId: 'scheduled-event-1',
      sourceId: 'peer-tab',
      ownerId: '1',
      draftId: 91,
      clientDraftKey: 'client-draft-key-1234',
      storageKey: 'noviis:draft:1:edit:91',
      at: Date.now(),
    } as const

    for (let index = 0; index < 2; index++) {
      window.dispatchEvent(new StorageEvent('storage', {
        key: 'noviis:draft-scheduled-event',
        newValue: JSON.stringify(message),
      }))
    }
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'noviis:draft-scheduled-event',
      newValue: JSON.stringify({
        ...message,
        eventId: 'expired-event',
        at: Date.now() - 60_001,
      }),
    }))

    expect(listener).toHaveBeenCalledExactlyOnceWith(message)
  })

  it('does not match a reused composer with a different client draft key', () => {
    const message = {
      type: 'draft-scheduled',
      eventId: 'scheduled-event-1',
      sourceId: 'peer-tab',
      ownerId: '1',
      draftId: null,
      clientDraftKey: 'original-draft-key',
      storageKey: 'noviis:draft:1:create:free',
      at: Date.now(),
    } as const

    expect(matchesDraftScheduledEvent(
      message,
      1,
      null,
      'different-draft-key',
      'noviis:draft:1:create:free',
    )).toBe(false)
  })
})
