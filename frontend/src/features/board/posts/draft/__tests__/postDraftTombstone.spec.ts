import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  closeDraftDeletedChannelForTest,
  markDraftDeletedLocally,
} from '@/features/board/posts/draft/postDraftTombstone'

describe('draft deletion cross-tab channel', () => {
  afterEach(() => {
    closeDraftDeletedChannelForTest()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('broadcasts a deletion even when the tombstone cannot be written', () => {
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
    const peer = new FakeBroadcastChannel('noviis-draft-deleted')
    const received: unknown[] = []
    peer.addEventListener('message', (event) => received.push(event.data))
    const storageProto = Object.getPrototypeOf(window.localStorage) as globalThis.Storage
    vi.spyOn(storageProto, 'setItem').mockImplementation(() => {
      throw new DOMException('blocked', 'SecurityError')
    })

    expect(markDraftDeletedLocally(7, 91)).toBe(false)
    expect(received).toEqual([
      expect.objectContaining({
        type: 'draft-deleted',
        ownerId: '7',
        draftId: '91',
      }),
    ])
  })
})
