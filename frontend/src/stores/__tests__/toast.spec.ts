import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useToastStore, type Toast } from '../toast'

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
                position: 'bottom-center'
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

            vi.advanceTimersByTime(2000)

            expect(store.toasts).toHaveLength(0)
        })

        it('does not auto-remove toast if duration is 0', () => {
            store.addToast('Permanent', 'info', 0)

            vi.advanceTimersByTime(10000)

            expect(store.toasts).toHaveLength(1)
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
