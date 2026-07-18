import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  closeAuthBoundaryChannelForTest,
  publishAuthBoundary,
  registerAuthBoundaryListener,
} from '@/api/authBoundaryChannel'

describe('auth boundary channel', () => {
  afterEach(() => {
    closeAuthBoundaryChannelForTest()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('broadcasts a boundary without exposing an access token', () => {
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
    const peer = new FakeBroadcastChannel('noviis-auth-boundary')
    const messages: unknown[] = []
    peer.addEventListener('message', (event) => messages.push(event.data))

    publishAuthBoundary('account-switch')

    expect(messages).toHaveLength(1)
    expect(messages[0]).toMatchObject({ type: 'auth-boundary', boundary: 'account-switch' })
    expect(JSON.stringify(messages[0])).not.toContain('accessToken')
  })

  it('deduplicates the BroadcastChannel and storage copies of one boundary', () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    const listener = vi.fn()
    registerAuthBoundaryListener(listener)
    const message = {
      type: 'auth-boundary',
      boundary: 'logout',
      eventId: 'same-boundary',
      sourceId: 'peer-tab',
      at: Date.now(),
    }

    window.dispatchEvent(new StorageEvent('storage', {
      key: 'noviisAuthBoundaryEvent',
      newValue: JSON.stringify(message),
    }))
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'noviisAuthBoundaryEvent',
      newValue: JSON.stringify(message),
    }))

    expect(listener).toHaveBeenCalledTimes(1)
  })
})
