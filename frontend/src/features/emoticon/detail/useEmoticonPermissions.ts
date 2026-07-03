import { computed } from 'vue'

interface UseEmoticonPermissionsOptions {
    isAuthenticated: () => boolean
    getCreatorId: () => number | null | undefined
    getUserId: () => number | null | undefined
}

export function useEmoticonPermissions(options: UseEmoticonPermissionsOptions) {
    const isOwner = computed(() => {
        const creatorId = options.getCreatorId()
        const userId = options.getUserId()
        return options.isAuthenticated() && typeof creatorId === 'number' && creatorId === userId
    })

    return {
        isOwner,
    }
}
