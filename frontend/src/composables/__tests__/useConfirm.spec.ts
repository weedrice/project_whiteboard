import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useConfirm } from '../useConfirm'
import { useConfirmStore } from '@/stores/confirm'

// Mock vue-i18n
vi.mock('vue-i18n', () => ({
    useI18n: vi.fn(() => ({
        t: vi.fn((key: string) => {
            const translations: Record<string, string> = {
                'common.confirm': 'Confirm',
                'common.yes': 'Yes',
                'common.no': 'No'
            }
            return translations[key] || key
        })
    }))
}))

describe('useConfirm', () => {
    let confirmStore: ReturnType<typeof useConfirmStore>

    beforeEach(() => {
        setActivePinia(createPinia())
        confirmStore = useConfirmStore()
    })

    describe('confirm function', () => {
        it('opens confirm dialog with message', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Are you sure?')

            expect(confirmStore.isOpen).toBe(true)
            expect(confirmStore.message).toBe('Are you sure?')

            confirmStore.cancel()
            await promise
        })

        it('uses default title from i18n', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Delete this?')

            expect(confirmStore.title).toBe('Confirm')

            confirmStore.cancel()
            await promise
        })

        it('uses custom title when provided', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Delete this?', 'Custom Title')

            expect(confirmStore.title).toBe('Custom Title')

            confirmStore.cancel()
            await promise
        })

        it('uses default button texts from i18n', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Continue?')

            expect(confirmStore.confirmText).toBe('Yes')
            expect(confirmStore.cancelText).toBe('No')

            confirmStore.cancel()
            await promise
        })

        it('uses custom button texts when provided', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Proceed?', 'Action', 'OK', 'Cancel')

            expect(confirmStore.confirmText).toBe('OK')
            expect(confirmStore.cancelText).toBe('Cancel')

            confirmStore.cancel()
            await promise
        })

        it('returns true when confirmed', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Confirm action?')
            confirmStore.confirm()

            const result = await promise
            expect(result).toBe(true)
        })

        it('returns false when cancelled', async () => {
            const { confirm } = useConfirm()

            const promise = confirm('Cancel action?')
            confirmStore.cancel()

            const result = await promise
            expect(result).toBe(false)
        })
    })
})
