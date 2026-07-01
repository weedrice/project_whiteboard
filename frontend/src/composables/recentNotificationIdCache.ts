export const createRecentNotificationIdCache = (limit: number) => {
    const ids = new Set<number>()

    const remember = (id: number) => {
        ids.add(id)
        if (ids.size <= limit) return

        const oldestId = ids.values().next().value
        if (typeof oldestId === 'number') {
            ids.delete(oldestId)
        }
    }

    return {
        has: (id: number) => ids.has(id),
        remember,
        clear: () => ids.clear(),
    }
}
