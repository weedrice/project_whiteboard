export interface NotificationRevision {
    groupCount?: number
    lastEventAt?: string
}

export function isNewerNotificationRevision(incoming: NotificationRevision, current: NotificationRevision): boolean {
    const incomingCount = incoming.groupCount ?? 1
    const currentCount = current.groupCount ?? 1
    if (incomingCount !== currentCount) return incomingCount > currentCount

    const incomingTime = Date.parse(incoming.lastEventAt ?? '')
    const currentTime = Date.parse(current.lastEventAt ?? '')
    return Number.isFinite(incomingTime) && (!Number.isFinite(currentTime) || incomingTime > currentTime)
}

export const createRecentNotificationIdCache = (limit: number) => {
    const revisions = new Map<number, NotificationRevision>()

    const remember = (id: number, revision: NotificationRevision = {}) => {
        const current = revisions.get(id)
        if (current && !isNewerNotificationRevision(revision, current)) return
        revisions.set(id, { groupCount: revision.groupCount, lastEventAt: revision.lastEventAt })
        if (revisions.size <= limit) return

        const oldestId = revisions.keys().next().value
        if (typeof oldestId === 'number') {
            revisions.delete(oldestId)
        }
    }

    return {
        has: (id: number, revision?: NotificationRevision) => {
            const current = revisions.get(id)
            return current !== undefined && (revision === undefined || !isNewerNotificationRevision(revision, current))
        },
        remember,
        clear: () => revisions.clear(),
    }
}
