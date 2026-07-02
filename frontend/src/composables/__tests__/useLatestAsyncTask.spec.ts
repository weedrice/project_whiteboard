import { describe, expect, it, vi } from 'vitest'
import { effectScope } from 'vue'
import { useLatestAsyncTask } from '../useLatestAsyncTask'
import { createDeferred } from '@/test/async'

describe('useLatestAsyncTask', () => {
    it('keeps only the latest result and aborts previous work', async () => {
        const scope = effectScope()
        const first = createDeferred<string>()
        const second = createDeferred<string>()
        const signals: AbortSignal[] = []

        try {
            await scope.run(async () => {
                const task = useLatestAsyncTask<string>()

                const firstRun = task.run(({ signal }) => {
                    signals.push(signal)
                    return first.promise
                })
                const secondRun = task.run(({ signal }) => {
                    signals.push(signal)
                    return second.promise
                })

                expect(signals[0].aborted).toBe(true)
                expect(task.loading.value).toBe(true)

                second.resolve('second')
                await expect(secondRun).resolves.toBe('second')

                first.resolve('first')
                await expect(firstRun).resolves.toBeUndefined()
                expect(task.loading.value).toBe(false)
            })
        } finally {
            scope.stop()
        }
    })

    it('maps non-aborted errors and clears loading', async () => {
        const onError = vi.fn()
        const scope = effectScope()

        try {
            await scope.run(async () => {
                const task = useLatestAsyncTask({
                    getErrorValue: () => 'failed',
                    onError,
                })

                await expect(task.run(async () => {
                    throw new Error('boom')
                })).resolves.toBeUndefined()

                expect(onError).toHaveBeenCalledTimes(1)
                expect(task.error.value).toBe('failed')
                expect(task.loading.value).toBe(false)
            })
        } finally {
            scope.stop()
        }
    })
})
