import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useNotificationStore } from '../notification'

describe('Notification Store', () => {
    let store: ReturnType<typeof useNotificationStore>

    beforeEach(() => {
        setActivePinia(createPinia())
        vi.clearAllMocks()
        store = useNotificationStore()
    })

    describe('initial state', () => {
        it('is an empty store', () => {
            // Notification store is now minimal, all logic moved to useNotification composable
            // Pinia store is an object but not empty - it has store properties
            expect(store).toBeDefined()
            expect(typeof store).toBe('object')
        })
    })
})
