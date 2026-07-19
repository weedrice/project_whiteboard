import { describe, expect, it, vi } from 'vitest'
import type { Notification } from '@/types'
import { emitMessageStreamEvent, subscribeMessageStreamEvents } from '../messageStreamEvents'

const notification = (notificationType: Notification['notificationType']) => ({
    notificationId: 1,
    notificationType,
    sourceType: notificationType === 'MESSAGE' ? 'MESSAGE' : 'POST',
} as Notification)

describe('messageStreamEvents', () => {
    it('emits only message notifications and supports unsubscribe', () => {
        const listener = vi.fn()
        const unsubscribe = subscribeMessageStreamEvents(listener)

        emitMessageStreamEvent(notification('LIKE'))
        emitMessageStreamEvent(notification('MESSAGE'))
        unsubscribe()
        emitMessageStreamEvent(notification('MESSAGE'))

        expect(listener).toHaveBeenCalledTimes(1)
        expect(listener).toHaveBeenCalledWith(expect.objectContaining({ notificationType: 'MESSAGE' }))
    })
})
