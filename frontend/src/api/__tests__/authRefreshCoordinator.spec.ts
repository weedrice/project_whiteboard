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
    vi.useRealTimers()
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

  it('rejects an already-aborted waiter without starting a refresh', async () => {
    const controller = new AbortController()
    controller.abort()
    const refresh = vi.fn(async () => 'must-not-run')

    await expect(coordinateAuthRefresh(refresh, { signal: controller.signal }))
      .rejects.toMatchObject({ name: 'AbortError' })
    expect(refresh).not.toHaveBeenCalled()
  })

  it('cancels one waiter without aborting the shared refresh flight', async () => {
    const pending = createDeferred<string>()
    const refresh = vi.fn(() => pending.promise)
    const owner = coordinateAuthRefresh(refresh)
    const waiterController = new AbortController()
    const waiter = coordinateAuthRefresh(refresh, { signal: waiterController.signal })

    waiterController.abort()
    await expect(waiter).rejects.toMatchObject({ name: 'AbortError' })
    pending.resolve('next-access')
    await expect(owner).resolves.toBe('next-access')
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('uses the browser auth lock when Web Locks are available', async () => {
    const request = vi.fn(async (_name: string, callback: () => Promise<string>) => callback())
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })

    await expect(runWithAuthRefreshLock(async () => 'locked-access')).resolves.toBe('locked-access')
    expect(request).toHaveBeenCalledWith('noviis-auth-refresh', expect.any(Function))
  })

  it('does not reuse a recent result for a newer access-token refresh generation', async () => {
    const request = vi.fn(async (_name: string, callback: () => Promise<string>) => callback())
    vi.stubGlobal('navigator', { ...navigator, locks: { request } })
    vi.stubGlobal('BroadcastChannel', undefined)
    const refresh = vi.fn()
      .mockResolvedValueOnce('token-2')
      .mockResolvedValueOnce('token-3')

    await expect(coordinateAuthRefresh(refresh, { previousToken: 'token-1' })).resolves.toBe('token-2')
    await expect(coordinateAuthRefresh(refresh, { previousToken: 'token-2' })).resolves.toBe('token-3')

    expect(refresh).toHaveBeenCalledTimes(2)
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
      postMessage(message: Record<string, unknown> & { type: string, requestId?: string, previousToken?: string | null, sessionId?: string }) {
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
        sessionId: event.data.sessionId,
        previousToken: event.data.previousToken,
        sourceId: '000-peer-tab',
        at: Date.now(),
      })
      setTimeout(() => peer.postMessage({
          type: 'refresh-result',
          requestId: event.data.requestId,
          sessionId: event.data.sessionId,
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
    localStorage.setItem('noviisAuthRefreshSession', 'shared-session')
    localStorage.setItem('noviisAuthRefreshLease', JSON.stringify({
      ownerId: 'other-tab',
      sessionId: 'shared-session',
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
        sessionId: 'other-session',
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
        sessionId: 'shared-session',
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
    localStorage.setItem('noviisAuthRefreshSession', 'shared-session')
    localStorage.setItem('noviisAuthRefreshLease', JSON.stringify({
      ownerId: 'other-tab',
      sessionId: 'shared-session',
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

  it('renews a storage lease while a slow refresh is still active', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(1_000)
    vi.spyOn(Math, 'random').mockReturnValue(0)
    vi.stubGlobal('BroadcastChannel', undefined)
    localStorage.setItem('noviisAuthRefreshSession', 'shared-session')
    const pending = createDeferred<string>()
    const result = coordinateAuthRefresh(() => pending.promise, { previousToken: 'old-access' })

    await vi.advanceTimersByTimeAsync(40)
    const firstLease = JSON.parse(localStorage.getItem('noviisAuthRefreshLease') ?? '{}') as { expiresAt: number, fence: number }
    expect(firstLease.fence).toBeGreaterThan(0)

    await vi.advanceTimersByTimeAsync(5_000)
    const renewedLease = JSON.parse(localStorage.getItem('noviisAuthRefreshLease') ?? '{}') as { expiresAt: number, fence: number }
    expect(renewedLease.fence).toBe(firstLease.fence)
    expect(renewedLease.expiresAt).toBeGreaterThan(firstLease.expiresAt)

    pending.resolve('next-access')
    await expect(result).resolves.toBe('next-access')
  })

  it('discards a stale owner result after a higher-fenced tab takes over', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(10_000)
    vi.spyOn(Math, 'random').mockReturnValue(0)
    vi.stubGlobal('BroadcastChannel', undefined)
    localStorage.setItem('noviisAuthRefreshSession', 'shared-session')
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    const result = coordinateAuthRefresh((signal) => new Promise<string>((_resolve, reject) => {
      signal.addEventListener('abort', () => reject(signal.reason), { once: true })
    }), { previousToken: 'old-access' })

    await vi.advanceTimersByTimeAsync(40)
    const owned = JSON.parse(localStorage.getItem('noviisAuthRefreshLease') ?? '{}') as { fence: number }
    setItem.mockClear()
    localStorage.setItem('noviisAuthRefreshLease', JSON.stringify({
      ownerId: 'new-owner',
      sessionId: 'shared-session',
      fence: owned.fence + 1,
      expiresAt: Date.now() + 30_000,
    }))
    await vi.advanceTimersByTimeAsync(5_000)

    await expect(result).rejects.toMatchObject({ name: 'StorageLeaseLostError' })
    const emittedResults = setItem.mock.calls
      .filter(([key]) => key === 'noviisAuthRefreshEvent')
      .map(([, value]) => value)
      .filter((value) => value.includes('refresh-result'))
    expect(emittedResults).toHaveLength(0)
  })

  it('re-enters election so three different tab tokens refresh serially without Web Locks or storage', async () => {
    const channels: BlockedStorageBroadcastChannel[] = []
    class BlockedStorageBroadcastChannel {
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
    vi.stubGlobal('BroadcastChannel', BlockedStorageBroadcastChannel)
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    const peer = new BlockedStorageBroadcastChannel('noviis-auth-session')
    let peerCompletions = 0
    peer.addEventListener('message', (event) => {
      if (event.data.type !== 'refresh-request') return
      expect(event.data.sessionId).toBe('shared-origin-cookie-session')
      if (peerCompletions >= 2) return
      const wave = peerCompletions + 1
      peer.postMessage({
        type: 'refresh-request',
        requestId: `peer-request-${wave}`,
        sessionId: 'shared-origin-cookie-session',
        previousToken: `peer-token-${wave}`,
        sourceId: `000-peer-tab-${wave}`,
        at: Date.now(),
      })
      setTimeout(() => {
        peerCompletions += 1
        peer.postMessage({
          type: 'refresh-result',
          requestId: event.data.requestId,
          sessionId: 'shared-origin-cookie-session',
          previousToken: `peer-token-${wave}`,
          accessToken: `peer-access-${wave}`,
          sourceId: `000-peer-tab-${wave}`,
          at: Date.now(),
        })
      }, 10)
    })
    const refresh = vi.fn(async () => {
      expect(peerCompletions).toBe(2)
      return 'local-access'
    })

    await expect(coordinateAuthRefresh(refresh, { previousToken: 'tab-specific-access' }))
      .resolves.toBe('local-access')
    expect(peerCompletions).toBe(2)
    expect(refresh).toHaveBeenCalledTimes(1)
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
