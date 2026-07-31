import { useLatestRequestGate } from '@/composables/useLatestAsyncTask'

interface TrackedRequest {
    signal: AbortSignal
    isCurrent: () => boolean
    finish: () => void
}

export function useMailboxRequestLifecycle(getSessionGeneration: () => number) {
    const createSessionBoundGate = () => useLatestRequestGate<number>({
        captureContext: getSessionGeneration,
        isContextCurrent: (generation) => generation === getSessionGeneration(),
    })

    const messageDetail = createSessionBoundGate()
    const initialConversation = createSessionBoundGate()
    const conversationPage = createSessionBoundGate()
    const conversationRefresh = createSessionBoundGate()
    const deleteMessages = createSessionBoundGate()
    const markAsReadControllers = new Set<AbortController>()

    function startMarkAsRead(): TrackedRequest {
        const controller = new AbortController()
        const generation = getSessionGeneration()
        markAsReadControllers.add(controller)
        return {
            signal: controller.signal,
            isCurrent: () => !controller.signal.aborted
                && generation === getSessionGeneration(),
            finish: () => {
                markAsReadControllers.delete(controller)
            },
        }
    }

    function cancelMarkAsRead() {
        markAsReadControllers.forEach((controller) => controller.abort())
        markAsReadControllers.clear()
    }

    function cancelConversationRequests() {
        initialConversation.cancel()
        conversationPage.cancel()
        conversationRefresh.cancel()
    }

    function cancelAll() {
        messageDetail.cancel()
        cancelConversationRequests()
        deleteMessages.cancel()
        cancelMarkAsRead()
    }

    return {
        messageDetail,
        initialConversation,
        conversationPage,
        conversationRefresh,
        deleteMessages,
        startMarkAsRead,
        cancelMarkAsRead,
        cancelConversationRequests,
        cancelAll,
    }
}
