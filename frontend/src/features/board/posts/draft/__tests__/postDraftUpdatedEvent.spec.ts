import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  closeDraftUpdatedChannelForTest,
  publishDraftUpdatedEvent,
  registerDraftUpdatedListener,
} from '@/features/board/posts/draft/postDraftUpdatedEvent'

class FakeBroadcastChannel {
  static channels: FakeBroadcastChannel[] = []
  static posted: unknown[] = []
  listeners: Array<(event: MessageEvent) => void> = []

  constructor(readonly name: string) {
    FakeBroadcastChannel.channels.push(this)
  }

  addEventListener(_type: string, listener: (event: MessageEvent) => void) {
    this.listeners.push(listener)
  }

  removeEventListener(_type: string, listener: (event: MessageEvent) => void) {
    this.listeners = this.listeners.filter((candidate) => candidate !== listener)
  }

  postMessage(data: unknown) {
    FakeBroadcastChannel.posted.push(data)
    FakeBroadcastChannel.channels
      .filter((channel) => channel !== this && channel.name === this.name)
      .forEach((channel) => channel.listeners.forEach((listener) => listener({ data } as MessageEvent)))
  }

  close() {}
}

describe('draft updated event', () => {
  beforeEach(() => {
    closeDraftUpdatedChannelForTest()
    FakeBroadcastChannel.channels = []
    FakeBroadcastChannel.posted = []
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    localStorage.clear()
  })

  it('broadcasts metadata without editor content', () => {
    const stop = registerDraftUpdatedListener(vi.fn())
    new FakeBroadcastChannel('noviis-draft-updated')

    publishDraftUpdatedEvent(7, {
      draftId: 91,
      clientDraftKey: 'client-key',
      version: 3,
      updatedAt: '2026-08-05T12:00:00.000Z',
      contentFingerprint: 'a:b:c',
    })

    expect(FakeBroadcastChannel.posted).toHaveLength(1)
    expect(FakeBroadcastChannel.posted[0]).toEqual(expect.objectContaining({
      draftId: 91,
      contentFingerprint: 'a:b:c',
    }))
    expect(FakeBroadcastChannel.posted[0]).not.toHaveProperty('snapshot')
    expect(FakeBroadcastChannel.posted[0]).not.toHaveProperty('title')
    stop()
  })

  it('delivers only the compact update contract', () => {
    const listener = vi.fn()
    registerDraftUpdatedListener(listener)
    const peer = new FakeBroadcastChannel('noviis-draft-updated')
    peer.postMessage({
      type: 'draft-updated',
      eventId: 'event-1',
      sourceId: 'other-tab',
      at: Date.now(),
      ownerId: '7',
      draftId: 91,
      clientDraftKey: 'client-key',
      version: 3,
      updatedAt: '2026-08-05T12:00:00.000Z',
      contentFingerprint: 'a:b:c',
      title: 'must not be accepted',
    })

    expect(listener).toHaveBeenCalledWith(expect.objectContaining({
      draftId: 91,
      contentFingerprint: 'a:b:c',
    }))
    expect(listener.mock.calls[0]?.[0]).not.toHaveProperty('snapshot')
    expect(listener.mock.calls[0]?.[0]).not.toHaveProperty('title')
  })
})
