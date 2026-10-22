import { flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createDeferred } from '@/test/async'
import { createImageDownloadQueue } from '../imageDownloadQueue'

function setupRequests() {
  const queue = createImageDownloadQueue()
  const responses = Array.from({ length: 7 }, () => createDeferred<number>())
  const controllers = Array.from({ length: 7 }, () => new AbortController())
  const started: number[] = []
  let active = 0
  let maxActive = 0
  const requests = responses.map((response, index) => queue.run(() => {
    started.push(index)
    active += 1
    maxActive = Math.max(maxActive, active)
    return response.promise.finally(() => { active -= 1 })
  }, controllers[index]!.signal, index === 6))
  // Observe every rejection before any abort or response can settle.
  const settled = Promise.allSettled(requests)
  return { responses, controllers, started, settled, maxActive: () => maxActive }
}

describe('image download queue', () => {
  it('starts a retry before existing waiters while retaining the four-request limit', async () => {
    const batch = setupRequests()
    await flushPromises()
    expect(batch.started).toEqual([0, 1, 2, 3])

    batch.responses[0]!.resolve(0)
    await flushPromises()
    expect(batch.started).toEqual([0, 1, 2, 3, 6])

    batch.responses[1]!.resolve(1)
    await flushPromises()
    expect(batch.started).toEqual([0, 1, 2, 3, 6, 4])
    batch.responses[2]!.resolve(2)
    await flushPromises()
    expect(batch.started).toEqual([0, 1, 2, 3, 6, 4, 5])
    expect(batch.maxActive()).toBe(4)

    batch.responses.forEach((response, index) => response.resolve(index))
    expect(await batch.settled).toEqual(Array.from({ length: 7 }, (_, value) => ({ status: 'fulfilled', value })))
  })

  it('removes a cancelled queued retry and continues ordinary waiters in FIFO order', async () => {
    const batch = setupRequests()
    await flushPromises()
    batch.controllers[6]!.abort()
    batch.responses[0]!.resolve(0)
    await flushPromises()
    expect(batch.started).toEqual([0, 1, 2, 3, 4])
    batch.responses[1]!.resolve(1)
    await flushPromises()
    expect(batch.started).toEqual([0, 1, 2, 3, 4, 5])
    expect(batch.maxActive()).toBe(4)

    batch.responses.forEach((response, index) => response.resolve(index))
    const results = await batch.settled
    expect(results.slice(0, 6)).toEqual(Array.from({ length: 6 }, (_, value) => ({ status: 'fulfilled', value })))
    expect(results[6]).toEqual({ status: 'rejected', reason: expect.objectContaining({ name: 'AbortError' }) })
  })

  it.each(['rejection', 'synchronous throw'])('returns the transport slot after a task %s', async (failure) => {
    const queue = createImageDownloadQueue()
    const error = new Error('download failed')
    const failedTask = vi.fn(() => {
      if (failure === 'synchronous throw') throw error
      return Promise.reject(error)
    })
    const responses = Array.from({ length: 5 }, () => createDeferred<number>())
    const started: number[] = []
    const requests: Promise<number>[] = [queue.run<number>(failedTask, new AbortController().signal)]
    responses.forEach((response, index) => {
      requests.push(queue.run(() => {
        started.push(index)
        return response.promise
      }, new AbortController().signal))
    })
    const settled = Promise.allSettled(requests)
    await flushPromises()
    expect(failedTask).toHaveBeenCalledTimes(1)
    // Three initial transports plus the waiter that took the failed task's slot.
    expect(started).toEqual([0, 1, 2, 3])
    responses[0]!.resolve(0)
    await flushPromises()
    expect(started).toEqual([0, 1, 2, 3, 4])
    responses.forEach((response, index) => response.resolve(index))
    const results = await settled
    expect(results[0]).toEqual({ status: 'rejected', reason: error })
    expect(results.slice(1)).toEqual(Array.from({ length: 5 }, (_, value) => ({ status: 'fulfilled', value })))
  })
})
