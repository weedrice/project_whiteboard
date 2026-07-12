import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useToastStore } from '../toast'

describe('Toast Store', () => {
    let store: ReturnType<typeof useToastStore>

    beforeEach(() => {
        vi.useFakeTimers()
        setActivePinia(createPinia())
        store = useToastStore()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    describe('addToast', () => {
        it('adds a toast with default values', () => {
            store.addToast('Test message')

            expect(store.toasts).toHaveLength(1)
            expect(store.toasts[0]).toMatchObject({
                message: 'Test message',
                type: 'info',
                duration: 3000,
                position: 'top-center'
            })
        })

        it('adds a toast with custom type', () => {
            store.addToast('Success!', 'success')

            expect(store.toasts[0].type).toBe('success')
        })

        it('adds a toast with custom duration', () => {
            store.addToast('Quick message', 'info', 1000)

            expect(store.toasts[0].duration).toBe(1000)
        })

        it('adds a toast with custom position', () => {
            store.addToast('Top message', 'warning', 3000, 'top-center')

            expect(store.toasts[0].position).toBe('top-center')
        })

        it('assigns unique IDs to toasts', () => {
            store.addToast('First')
            store.addToast('Second')
            store.addToast('Third')

            const ids = store.toasts.map(t => t.id)
            expect(new Set(ids).size).toBe(3)
        })

        it('auto-removes toast after duration', () => {
            store.addToast('Temporary', 'info', 2000)

            expect(store.toasts).toHaveLength(1)
            expect(vi.getTimerCount()).toBe(1)

            vi.advanceTimersByTime(2000)

            expect(store.toasts).toHaveLength(0)
            expect(vi.getTimerCount()).toBe(0)
        })

        it('does not auto-remove toast if duration is 0', () => {
            store.addToast('Permanent', 'info', 0)

            vi.advanceTimersByTime(10000)

            expect(store.toasts).toHaveLength(1)
        })

        it('pauses and resumes the remaining display duration', () => {
            store.addToast('Read me', 'info', 3000)
            const id = store.toasts[0].id

            vi.advanceTimersByTime(1000)
            store.pauseToast(id)
            vi.advanceTimersByTime(5000)
            expect(store.toasts).toHaveLength(1)

            store.resumeToast(id)
            vi.advanceTimersByTime(1999)
            expect(store.toasts).toHaveLength(1)
            vi.advanceTimersByTime(1)
            expect(store.toasts).toHaveLength(0)
        })

        it('debounces duplicate error messages within 5 seconds', () => {
            store.addToast('same error', 'error', 0)
            store.addToast('same error', 'error', 0)

            expect(store.toasts).toHaveLength(1)
            expect(store.toasts[0].message).toBe('same error')
        })

        it('allows stale error messages again after debounce window and prunes old cache entries', () => {
            store.addToast('old error', 'error', 0)
            vi.advanceTimersByTime(6000)

            store.addToast('new error', 'error', 0)
            store.addToast('old error', 'error', 0)

            expect(store.toasts.map((t) => t.message)).toEqual(['old error', 'new error', 'old error'])
        })
    })

    describe('removeToast', () => {
        it('removes a specific toast by ID', () => {
            store.addToast('First')
            store.addToast('Second')
            store.addToast('Third')

            const secondId = store.toasts[1].id
            store.removeToast(secondId)

            expect(store.toasts).toHaveLength(2)
            expect(store.toasts.map(t => t.message)).toEqual(['First', 'Third'])
            expect(vi.getTimerCount()).toBe(2)
        })

        it('clears pending removal timers when the store is disposed', () => {
            store.addToast('First')
            store.addToast('Second')

            expect(vi.getTimerCount()).toBe(2)

            store.$dispose()

            expect(vi.getTimerCount()).toBe(0)
        })

        it('does nothing if toast ID not found', () => {
            store.addToast('Only one')

            store.removeToast(999)

            expect(store.toasts).toHaveLength(1)
        })
    })

    describe('multiple toasts', () => {
        it('can handle multiple toasts of different types', () => {
            store.addToast('Info message', 'info')
            store.addToast('Success message', 'success')
            store.addToast('Warning message', 'warning')
            store.addToast('Error message', 'error')

            expect(store.toasts).toHaveLength(4)
            expect(store.toasts.map(t => t.type)).toEqual(['info', 'success', 'warning', 'error'])
        })
    })
})
