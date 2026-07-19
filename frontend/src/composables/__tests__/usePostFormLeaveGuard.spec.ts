import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePostFormLeaveGuard } from '@/features/board/posts/form/usePostFormLeaveGuard'

const routeGuard = vi.hoisted(() => ({
    callback: undefined as undefined | (() => boolean | Promise<boolean>),
}))

vi.mock('vue-router', () => ({
    onBeforeRouteLeave: vi.fn((callback) => {
        routeGuard.callback = callback
    }),
}))

const runGuard = async () => {
    return routeGuard.callback?.()
}

describe('usePostFormLeaveGuard', () => {
    it('continues when the form has no unsaved changes', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => false,
        })
        const confirmLeave = vi.fn()

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runGuard()

        expect(confirmLeave).not.toHaveBeenCalled()
        expect(result).toBe(true)
    })

    it('blocks navigation without prompting while a server mutation is in progress', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
            isSubmissionInProgress: () => true,
        })
        const confirmLeave = vi.fn().mockResolvedValue(true)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runGuard()

        expect(confirmLeave).not.toHaveBeenCalled()
        expect(result).toBe(false)
    })

    it('allows the success navigation after the submitted snapshot is marked saved', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => false,
            isSubmissionInProgress: () => true,
            consumeSuccessfulSubmissionNavigation: vi.fn().mockReturnValue(true),
        })
        const confirmLeave = vi.fn()

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runGuard()

        expect(confirmLeave).not.toHaveBeenCalled()
        expect(result).toBe(true)
    })

    it('blocks navigation when confirm rejects leaving', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
            getLeaveConfirmMessage: () => 'custom message',
        })
        const confirmLeave = vi.fn().mockResolvedValue(false)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runGuard()

        expect(confirmLeave).toHaveBeenCalledWith('custom message')
        expect(result).toBe(false)
    })

    it('continues with fallback message when confirm accepts leaving', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
        })
        const confirmLeave = vi.fn().mockReturnValue(true)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runGuard()

        expect(confirmLeave).toHaveBeenCalledWith('fallback')
        expect(result).toBe(true)
    })
})
