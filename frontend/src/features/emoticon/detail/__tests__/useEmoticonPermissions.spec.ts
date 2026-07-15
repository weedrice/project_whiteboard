import { describe, expect, it } from 'vitest'
import { ref } from 'vue'
import { useEmoticonPermissions } from '../useEmoticonPermissions'

describe('useEmoticonPermissions', () => {
    it('allows ownership only for authenticated matching users', () => {
        const isAuthenticated = ref(true)
        const creatorId = ref<number | null>(7)
        const userId = ref<number | null>(7)
        const { isOwner } = useEmoticonPermissions({
            isAuthenticated: () => isAuthenticated.value,
            getCreatorId: () => creatorId.value,
            getUserId: () => userId.value,
        })

        expect(isOwner.value).toBe(true)

        userId.value = 8
        expect(isOwner.value).toBe(false)

        userId.value = 7
        isAuthenticated.value = false
        expect(isOwner.value).toBe(false)

        isAuthenticated.value = true
        creatorId.value = null
        expect(isOwner.value).toBe(false)
    })
})
