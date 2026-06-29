import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePostFormLeaveGuard } from '@/composables/usePostFormLeaveGuard'

const routeGuard = vi.hoisted(() => ({
    callback: undefined as undefined | ((to: unknown, from: unknown, next: (value?: boolean) => void) => void | Promise<void>),
}))

vi.mock('vue-router', () => ({
    onBeforeRouteLeave: vi.fn((callback) => {
        routeGuard.callback = callback
    }),
}))

const runGuard = async () => {
    const next = vi.fn()
    await routeGuard.callback?.({}, {}, next)
    return next
}

describe('usePostFormLeaveGuard', () => {
    it('continues when the form has no unsaved changes', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => false,
        })
        const confirmLeave = vi.fn()

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const next = await runGuard()

        expect(confirmLeave).not.toHaveBeenCalled()
        expect(next).toHaveBeenCalledWith()
    })

    it('blocks navigation when confirm rejects leaving', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
            getLeaveConfirmMessage: () => 'custom message',
        })
        const confirmLeave = vi.fn().mockResolvedValue(false)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const next = await runGuard()

        expect(confirmLeave).toHaveBeenCalledWith('custom message')
        expect(next).toHaveBeenCalledWith(false)
    })

    it('continues with fallback message when confirm accepts leaving', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
        })
        const confirmLeave = vi.fn().mockReturnValue(true)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const next = await runGuard()

        expect(confirmLeave).toHaveBeenCalledWith('fallback')
        expect(next).toHaveBeenCalledWith()
    })
})
