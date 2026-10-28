import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useConfirmStore } from '../confirm'
import { useAuthStore } from '../auth'

describe('Confirm Store', () => {
    let store: ReturnType<typeof useConfirmStore>

    beforeEach(() => {
        setActivePinia(createPinia())
        store = useConfirmStore()
    })

    describe('initial state', () => {
        it('starts with dialog closed', () => {
            expect(store.isOpen).toBe(false)
            expect(store.title).toBe('')
            expect(store.message).toBe('')
        })
    })

    describe('open', () => {
        it('opens dialog with message', async () => {
            // Don't await - just call open
            const promise = store.open('Are you sure?')

            expect(store.isOpen).toBe(true)
            expect(store.message).toBe('Are you sure?')
            expect(store.title).toBe('Confirm')

            // Resolve to prevent unhandled promise
            store.cancel()
            await promise
        })

        it('opens dialog with custom title', async () => {
            const promise = store.open('Delete this item?', 'Delete Confirmation')

            expect(store.title).toBe('Delete Confirmation')
            expect(store.message).toBe('Delete this item?')

            store.cancel()
            await promise
        })

        it('opens dialog with custom button texts', async () => {
            const promise = store.open('Proceed?', 'Action', 'Yes', 'No')

            expect(store.confirmText).toBe('Yes')
            expect(store.cancelText).toBe('No')

            store.cancel()
            await promise
        })

        it('uses default button texts', async () => {
            const promise = store.open('Question?')

            expect(store.confirmText).toBe('Confirm')
            expect(store.cancelText).toBe('Cancel')

            store.cancel()
            await promise
        })
    })

    describe('caller cancellation', () => {
        it('settles and closes its pending dialog when the caller aborts', async () => {
            const controller = new AbortController()
            const pending = store.open('Discard?', undefined, undefined, undefined, { signal: controller.signal })
            controller.abort()
            await expect(pending).resolves.toBe(false)
            expect(store.isOpen).toBe(false)
        })

        it('does not replace an unrelated dialog with an already aborted request', async () => {
            const active = store.open('Current action')
            const controller = new AbortController()
            controller.abort()
            await expect(store.open('Stale action', undefined, undefined, undefined, { signal: controller.signal }))
                .resolves.toBe(false)
            expect(store.isOpen).toBe(true)
            expect(store.message).toBe('Current action')
            store.confirm()
            await expect(active).resolves.toBe(true)
        })

        it('detaches the replaced caller signal without dismissing the newer dialog', async () => {
            const controller = new AbortController()
            const remove = vi.spyOn(controller.signal, 'removeEventListener')
            const old = store.open('Old action', undefined, undefined, undefined, { signal: controller.signal })
            const current = store.open('New action')
            await expect(old).resolves.toBe(false)
            expect(remove).toHaveBeenCalledWith('abort', expect.any(Function))
            controller.abort()
            expect(store.isOpen).toBe(true)
            expect(store.message).toBe('New action')
            store.confirm()
            await expect(current).resolves.toBe(true)
        })

        it.each(['confirm', 'cancel'] as const)('detaches the caller signal after %s', async (action) => {
            const controller = new AbortController()
            const remove = vi.spyOn(controller.signal, 'removeEventListener')
            const pending = store.open('Action', undefined, undefined, undefined, { signal: controller.signal })
            store[action]()
            await expect(pending).resolves.toBe(action === 'confirm')
            expect(remove).toHaveBeenCalledWith('abort', expect.any(Function))
            const next = store.open('Next action')
            controller.abort()
            expect(store.message).toBe('Next action')
            store.cancel()
            await expect(next).resolves.toBe(false)
        })
    })

    describe('confirm', () => {
        it('resolves promise with true when confirmed', async () => {
            const promise = store.open('Confirm action?')

            store.confirm()

            const result = await promise
            expect(result).toBe(true)
        })

        it('closes dialog after confirm', async () => {
            const promise = store.open('Confirm action?')

            store.confirm()
            await promise

            expect(store.isOpen).toBe(false)
            expect(store.message).toBe('')
            expect(store.title).toBe('')
        })

        it('just closes when confirm is called without pending promise', () => {
            store.confirm()

            expect(store.isOpen).toBe(false)
            expect(store.title).toBe('')
            expect(store.message).toBe('')
        })
    })

    describe('cancel', () => {
        it('resolves promise with false when cancelled', async () => {
            const promise = store.open('Cancel action?')

            store.cancel()

            const result = await promise
            expect(result).toBe(false)
        })

        it('closes dialog after cancel', async () => {
            const promise = store.open('Cancel action?')

            store.cancel()
            await promise

            expect(store.isOpen).toBe(false)
            expect(store.message).toBe('')
        })

        it('just closes when cancel is called without pending promise', () => {
            store.cancel()

            expect(store.isOpen).toBe(false)
            expect(store.title).toBe('')
            expect(store.message).toBe('')
        })
    })

    describe('multiple dialog flow', () => {
        it('cancels an open confirmation on session change without approving a later dialog', async () => {
            const auth = useAuthStore()
            const old = store.open('Old account action')
            auth.sessionGeneration++
            await expect(old).resolves.toBe(false)
            expect(store.isOpen).toBe(false)
            const current = store.open('New account action')
            store.confirm()
            await expect(current).resolves.toBe(true)
        })

        it('settles a pending confirmation when the store is disposed', async () => {
            const pending = store.open('Pending')
            store.$dispose()
            await expect(pending).resolves.toBe(false)
        })

        it('resolves the previous pending dialog as cancelled when another dialog opens', async () => {
            const promise1 = store.open('First question?')
            const promise2 = store.open('Second question?')

            await expect(promise1).resolves.toBe(false)
            expect(store.isOpen).toBe(true)
            expect(store.message).toBe('Second question?')

            store.confirm()

            await expect(promise2).resolves.toBe(true)
        })

        it('can open new dialog after closing previous', async () => {
            // First dialog
            const promise1 = store.open('First question?')
            store.confirm()
            const result1 = await promise1
            expect(result1).toBe(true)

            // Second dialog
            const promise2 = store.open('Second question?')
            expect(store.isOpen).toBe(true)
            expect(store.message).toBe('Second question?')

            store.cancel()
            const result2 = await promise2
            expect(result2).toBe(false)
        })
    })
})
