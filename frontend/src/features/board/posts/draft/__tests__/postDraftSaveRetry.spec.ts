import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createDraftSaveRetryController,
  SAVE_RETRY_MAX_ATTEMPTS,
} from '@/features/board/posts/draft/postDraftSaveRetry'

describe('draft save retry controller', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-05T11:00:00.000Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('runs the first retry after the existing one-second backoff', async () => {
    const retry = vi.fn().mockResolvedValue(undefined)
    const controller = createDraftSaveRetryController({
      canRetry: () => true,
      retry,
      random: () => 0.5,
      resolveRetryAfterMs: () => null,
    })

    controller.schedule()

    expect(controller.attempt.value).toBe(1)
    expect(controller.scheduled.value).toBe(true)
    await vi.advanceTimersByTimeAsync(999)
    expect(retry).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)

    expect(retry).toHaveBeenCalledTimes(1)
    expect(controller.scheduled.value).toBe(false)
  })

  it('keeps a long Retry-After deadline across bounded timer windows', async () => {
    const retry = vi.fn().mockResolvedValue(undefined)
    const controller = createDraftSaveRetryController({
      canRetry: () => true,
      retry,
      resolveRetryAfterMs: () => 60_000,
    })

    controller.schedule(new Error('throttled'))
    await vi.advanceTimersByTimeAsync(59_999)
    expect(retry).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(1)

    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('waits offline without consuming an attempt and resumes against the same deadline', async () => {
    let online = false
    const retry = vi.fn().mockResolvedValue(undefined)
    const controller = createDraftSaveRetryController({
      canRetry: () => true,
      retry,
      isOnline: () => online,
      random: () => 0.5,
      resolveRetryAfterMs: () => null,
    })

    controller.schedule()
    expect(controller.scheduled.value).toBe(true)
    expect(controller.attempt.value).toBe(0)
    await vi.advanceTimersByTimeAsync(10_000)
    expect(retry).not.toHaveBeenCalled()

    online = true
    controller.schedule()
    await vi.advanceTimersByTimeAsync(0)

    expect(controller.attempt.value).toBe(1)
    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('pauses an armed retry when connectivity drops', async () => {
    let online = true
    const retry = vi.fn().mockResolvedValue(undefined)
    const controller = createDraftSaveRetryController({
      canRetry: () => true,
      retry,
      isOnline: () => online,
      random: () => 0.5,
      resolveRetryAfterMs: () => null,
    })

    controller.schedule()
    expect(controller.attempt.value).toBe(1)

    online = false
    controller.pauseForOffline()
    expect(controller.attempt.value).toBe(0)
    await vi.advanceTimersByTimeAsync(10_000)
    expect(retry).not.toHaveBeenCalled()

    online = true
    controller.schedule()
    await vi.advanceTimersByTimeAsync(0)

    expect(retry).toHaveBeenCalledTimes(1)
  })

  it('marks retry exhaustion once after the fifth consumed attempt', async () => {
    const retry = vi.fn().mockResolvedValue(undefined)
    const onExhausted = vi.fn()
    const controller = createDraftSaveRetryController({
      canRetry: () => true,
      retry,
      random: () => 0.5,
      resolveRetryAfterMs: () => null,
      onExhausted,
    })

    for (const delay of [1_000, 2_000, 4_000, 8_000, 16_000]) {
      controller.schedule()
      await vi.advanceTimersByTimeAsync(delay)
    }
    expect(controller.attempt.value).toBe(SAVE_RETRY_MAX_ATTEMPTS)

    controller.schedule()
    controller.schedule()

    expect(controller.exhausted.value).toBe(true)
    expect(onExhausted).toHaveBeenCalledExactlyOnceWith(SAVE_RETRY_MAX_ATTEMPTS)

    controller.clear()
    expect(controller.attempt.value).toBe(0)
    expect(controller.scheduled.value).toBe(false)
    expect(controller.exhausted.value).toBe(false)
  })
})
