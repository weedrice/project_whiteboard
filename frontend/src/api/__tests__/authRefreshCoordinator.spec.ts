import { afterEach, describe, expect, it, vi } from 'vitest'
import {
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
})
