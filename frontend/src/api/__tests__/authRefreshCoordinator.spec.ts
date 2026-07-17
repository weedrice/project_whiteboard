import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  cancelAuthRefreshCoordinator,
  closeAuthRefreshCoordinatorForTest,
  coordinateAuthRefresh,
  runWithAuthRefreshLock,
} from '@/api/authRefreshCoordinator'
import { createDeferred } from '@/test/async'

describe('auth refresh coordinator', () => {
  afterEach(() => {
    closeAuthRefreshCoordinatorForTest()
    vi.unstubAllGlobals()
  })

  it('shares one refresh request across same-tab callers', async () => {
    const pending = createDeferred<string>()
    const refresh = vi.fn(() => pending.promise)

    const first = coordinateAuthRefresh(refresh)
    const second = coordinateAuthRefresh(refresh)
    pending.resolve('next-access')

    await expect(Promise.all([first, second])).resolves.toEqual(['next-access', 'next-access'])
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('uses the browser auth lock when Web Locks are available', async () => {
    const request = vi.fn(async (_name: string, callback: () => Promise<string>) => callback())
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })

    await expect(runWithAuthRefreshLock(async () => 'locked-access')).resolves.toBe('locked-access')
    expect(request).toHaveBeenCalledWith('noviis-auth-refresh', expect.any(Function))
  })

  it('reuses a matching refresh result announced by another tab', async () => {
    const channels: FakeBroadcastChannel[] = []
    class FakeBroadcastChannel {
      listeners: Array<(event: MessageEvent) => void> = []
      constructor(readonly name: string) {
        channels.push(this)
      }
      addEventListener(_type: string, listener: (event: MessageEvent) => void) {
        this.listeners.push(listener)
      }
      postMessage(message: Record<string, unknown> & { type: string, requestId?: string, previousToken?: string | null }) {
        channels.filter((candidate) => candidate !== this && candidate.name === this.name)
          .forEach((candidate) => candidate.listeners.forEach((listener) => listener({ data: message } as MessageEvent)))
      }
      close() {}
    }
    vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
    const peer = new FakeBroadcastChannel('noviis-auth-session')
    peer.addEventListener('message', (event) => {
      if (event.data.type !== 'refresh-request') return
      peer.postMessage({
        type: 'refresh-request',
        requestId: 'peer-request',
        previousToken: event.data.previousToken,
        sourceId: '000-peer-tab',
        at: Date.now(),
      })
      setTimeout(() => peer.postMessage({
          type: 'refresh-result',
          requestId: event.data.requestId,
          previousToken: event.data.previousToken,
          accessToken: 'peer-access',
          at: Date.now(),
          sourceId: '000-peer-tab',
      }), 10)
    })
    const refresh = vi.fn(async () => 'local-access')

    await expect(coordinateAuthRefresh(refresh, { previousToken: 'old-access' }))
      .resolves.toBe('peer-access')
    expect(refresh).not.toHaveBeenCalled()
  })

  it('uses a storage lease without writing an access token when BroadcastChannel is unavailable', async () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    localStorage.setItem('noviisAuthRefreshLease', JSON.stringify({
      ownerId: 'other-tab',
      expiresAt: Date.now() + 5000,
    }))
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const refresh = vi.fn(async () => 'sequential-access')

    const result = coordinateAuthRefresh(refresh, { previousToken: 'old-access' })
    await Promise.resolve()
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'noviisAuthRefreshEvent',
      newValue: JSON.stringify({
        type: 'refresh-result',
        sourceId: 'other-tab',
        previousToken: 'different-access',
        at: Date.now(),
      }),
    }))
    await Promise.resolve()
    expect(refresh).not.toHaveBeenCalled()
    window.dispatchEvent(new StorageEvent('storage', {
      key: 'noviisAuthRefreshEvent',
      newValue: JSON.stringify({
        type: 'refresh-result',
        sourceId: 'other-tab',
        previousToken: 'old-access',
        at: Date.now(),
      }),
    }))

    await expect(result).resolves.toBe('sequential-access')
    expect(refresh).toHaveBeenCalledTimes(1)
    const eventWrites = setItem.mock.calls
      .filter(([key]) => key === 'noviisAuthRefreshEvent')
      .map(([, value]) => value)
    expect(eventWrites.every((value) => !value.includes('sequential-access'))).toBe(true)
  })

  it('does not refresh after another tab logs out while waiting on a storage lease', async () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    localStorage.setItem('noviisAuthRefreshLease', JSON.stringify({
      ownerId: 'other-tab',
      expiresAt: Date.now() + 5000,
    }))
    const refresh = vi.fn(async () => 'must-not-run')
    const result = coordinateAuthRefresh(refresh, { previousToken: 'old-access' })
    await Promise.resolve()

    window.dispatchEvent(new StorageEvent('storage', {
      key: 'noviisAuthRefreshEvent',
      newValue: JSON.stringify({
        type: 'refresh-cancelled',
        sourceId: 'other-tab',
        at: Date.now(),
      }),
    }))

    await expect(result).rejects.toMatchObject({ name: 'AbortError' })
    expect(refresh).not.toHaveBeenCalled()
  })

  it('aborts the underlying refresh when the session changes', async () => {
    vi.stubGlobal('BroadcastChannel', undefined)
    let requestSignal: AbortSignal | undefined
    const refresh = coordinateAuthRefresh((signal) => {
      requestSignal = signal
      return new Promise<string>((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError')))
      })
    })
    await vi.waitFor(() => expect(requestSignal).toBeInstanceOf(AbortSignal))

    cancelAuthRefreshCoordinator()

    expect(requestSignal?.aborted).toBe(true)
    await expect(refresh).rejects.toMatchObject({ name: 'AbortError' })
  })

  it('aborts an active request immediately when another tab broadcasts cancellation', async () => {
    const channels: PeerCancelBroadcastChannel[] = []
    class PeerCancelBroadcastChannel {
      listeners: Array<(event: MessageEvent) => void> = []
      constructor(readonly name: string) {
        channels.push(this)
      }
      addEventListener(_type: string, listener: (event: MessageEvent) => void) {
        this.listeners.push(listener)
      }
      postMessage(message: Record<string, unknown>) {
        channels.filter((candidate) => candidate !== this && candidate.name === this.name)
          .forEach((candidate) => candidate.listeners.forEach((listener) => listener({ data: message } as MessageEvent)))
      }
      close() {}
    }
    vi.stubGlobal('BroadcastChannel', PeerCancelBroadcastChannel)
    const peer = new PeerCancelBroadcastChannel('noviis-auth-session')
    const messagesSeenByPeer: Array<Record<string, unknown>> = []
    peer.addEventListener('message', (event) => messagesSeenByPeer.push(event.data))
    let requestSignal: AbortSignal | undefined
    const refresh = coordinateAuthRefresh((signal) => {
      requestSignal = signal
      return new Promise<string>((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('cancelled', 'AbortError')))
      })
    }, { previousToken: 'old-access' })
    await vi.waitFor(() => expect(requestSignal).toBeInstanceOf(AbortSignal))

    peer.postMessage({ type: 'refresh-cancelled', sourceId: 'peer-tab', at: Date.now() })

    expect(requestSignal?.aborted).toBe(true)
    await expect(refresh).rejects.toMatchObject({ name: 'AbortError' })
    expect(messagesSeenByPeer.some((message) => message.type === 'refresh-error')).toBe(false)
    expect(messagesSeenByPeer.filter((message) => message.type === 'refresh-cancelled')).toHaveLength(0)
  })
})
