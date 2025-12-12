import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useConfirmStore } from '../confirm'

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
    })

    describe('multiple dialog flow', () => {
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
