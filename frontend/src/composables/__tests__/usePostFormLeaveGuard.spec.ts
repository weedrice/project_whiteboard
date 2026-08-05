import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { usePostFormLeaveGuard } from '@/features/board/posts/form/usePostFormLeaveGuard'

const routeGuard = vi.hoisted(() => ({
    leaveCallback: undefined as undefined | (() => boolean | Promise<boolean>),
    updateCallback: undefined as undefined | (() => boolean | Promise<boolean>),
}))

vi.mock('vue-router', () => ({
    onBeforeRouteLeave: vi.fn((callback) => {
        routeGuard.leaveCallback = callback
    }),
    onBeforeRouteUpdate: vi.fn((callback) => {
        routeGuard.updateCallback = callback
    }),
}))

const runLeaveGuard = async () => {
    return routeGuard.leaveCallback?.()
}

const runUpdateGuard = async () => {
    return routeGuard.updateCallback?.()
}

describe('usePostFormLeaveGuard', () => {
    it('continues when the form has no unsaved changes', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => false,
        })
        const confirmLeave = vi.fn()

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runLeaveGuard()

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

        const result = await runLeaveGuard()

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

        const result = await runLeaveGuard()

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

        const result = await runLeaveGuard()

        expect(confirmLeave).toHaveBeenCalledWith('custom message')
        expect(result).toBe(false)
    })

    it('continues with fallback message when confirm accepts leaving', async () => {
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
        })
        const confirmLeave = vi.fn().mockReturnValue(true)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runLeaveGuard()

        expect(confirmLeave).toHaveBeenCalledWith('fallback')
        expect(result).toBe(true)
    })

    it('guards same-route updates and flushes the current draft before allowing them', async () => {
        const flushPendingDraft = vi.fn().mockReturnValue(true)
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
            flushPendingDraft,
        })
        const confirmLeave = vi.fn().mockResolvedValue(true)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runUpdateGuard()

        expect(confirmLeave).toHaveBeenCalledWith('fallback')
        expect(flushPendingDraft).toHaveBeenCalledOnce()
        expect(result).toBe(true)
    })

    it('blocks navigation when the latest local draft cannot be flushed', async () => {
        const flushPendingDraft = vi.fn().mockReturnValue(false)
        const postFormRef = ref({
            hasUnsavedChanges: () => true,
            flushPendingDraft,
        })
        const confirmLeave = vi.fn().mockResolvedValue(true)

        usePostFormLeaveGuard(postFormRef, 'fallback', confirmLeave)

        const result = await runLeaveGuard()

        expect(confirmLeave).toHaveBeenCalledWith('fallback')
        expect(flushPendingDraft).toHaveBeenCalledOnce()
        expect(result).toBe(false)
    })
})
