import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { defineComponent, h, nextTick, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { usePostDraft } from '../usePostDraft'
import type { DraftRecoverySnapshot } from '../usePostDraft'
import { Storage } from '@/utils/storage'

const mocks = vi.hoisted(() => {
    const saveDraftMutateAsync = vi.fn()
    const deleteDraftMutateAsync = vi.fn()
    const getDraft = vi.fn()
    const getMyDrafts = vi.fn()

    return {
        saveDraftMutateAsync,
        deleteDraftMutateAsync,
        getDraft,
        getMyDrafts,
    }
})

vi.mock('@/composables/usePost', () => ({
    usePost: () => ({
        useSaveDraft: () => ({
            isPending: ref(false),
            mutateAsync: mocks.saveDraftMutateAsync,
        }),
        useDeleteDraft: () => ({
            isPending: ref(false),
            mutateAsync: mocks.deleteDraftMutateAsync,
        }),
    }),
}))

vi.mock('@/api/post', () => ({
    postApi: {
        getDraft: mocks.getDraft,
    },
}))

vi.mock('@/api/user', () => ({
    userApi: {
        getMyDrafts: mocks.getMyDrafts,
    },
}))

vi.mock('@/utils/logger', () => ({
    default: { error: vi.fn() },
}))

function mountComposable(payloadRef = ref({
    boardUrl: 'free',
    title: 'Draft title',
    contents: 'Draft body',
    fileIds: [7],
    originalPostId: undefined as number | undefined,
}), storageKeyRef = ref('noviis:test:draft')) {
    const appliedDrafts: DraftRecoverySnapshot[] = []
    let composable: ReturnType<typeof usePostDraft> | null = null

    const TestHarness = defineComponent({
        setup() {
            composable = usePostDraft({
                enabled: ref(true),
                storageKey: storageKeyRef,
                buildPayload: () => payloadRef.value,
                applyDraft: (draft) => appliedDrafts.push(draft),
            })
            return () => h('div')
        },
    })

    const wrapper = mount(TestHarness)
    if (composable == null) {
        throw new Error('Composable failed to mount')
    }

    return { wrapper, composable: composable as ReturnType<typeof usePostDraft>, appliedDrafts, payloadRef }
}

describe('usePostDraft', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        Storage.clear()
        vi.clearAllMocks()
        mocks.saveDraftMutateAsync.mockResolvedValue({
            data: {
                data: {
                    draftId: 91,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Draft title',
                    contents: 'Draft body',
                    tags: [],
                    fileIds: [7],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-01T00:00:00.000Z',
                    modifiedAt: '2025-01-01T00:00:00.000Z',
                },
            },
        })
        mocks.deleteDraftMutateAsync.mockResolvedValue({ data: { data: null } })
        mocks.getMyDrafts.mockResolvedValue({
            data: {
                data: {
                    content: [],
                    page: 0,
                    size: 50,
                    totalElements: 0,
                    totalPages: 0,
                    hasNext: false,
                    hasPrevious: false,
                },
            },
        })
    })

    afterEach(() => {
        vi.useRealTimers()
        Storage.clear()
    })

    it('saves drafts immediately and stores the returned identifiers locally', async () => {
        const { composable } = mountComposable()

        const savedDraft = await composable.saveNow()

        expect(savedDraft?.draftId).toBe(91)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledWith({
            boardUrl: 'free',
            title: 'Draft title',
            contents: 'Draft body',
            fileIds: [7],
            originalPostId: undefined,
            draftId: undefined,
            updatedAt: undefined,
        })
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            fileIds: [7],
            updatedAt: '2025-01-01T00:00:00.000Z',
        }))
    })

    it('refreshes an outdated draft version and retries the current save once', async () => {
        const { composable, payloadRef } = mountComposable()

        await composable.saveNow()

        payloadRef.value = {
            ...payloadRef.value,
            title: 'Current editor title',
        }
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: {
                status: 409,
                data: {
                    error: {
                        code: 'P004',
                    },
                },
            },
        })
        mocks.getDraft.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 91,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Server title',
                    contents: 'Server body',
                    tags: [],
                    fileIds: [7],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-02T00:00:00.000Z',
                    modifiedAt: '2025-01-02T00:00:00.000Z',
                },
            },
        })
        mocks.saveDraftMutateAsync.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 91,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Current editor title',
                    contents: 'Draft body',
                    tags: [],
                    fileIds: [7],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-03T00:00:00.000Z',
                    modifiedAt: '2025-01-03T00:00:00.000Z',
                },
            },
        })

        const savedDraft = await composable.saveNow()

        expect(savedDraft?.updatedAt).toBe('2025-01-03T00:00:00.000Z')
        expect(mocks.getDraft).toHaveBeenCalledWith(91)
        expect(mocks.saveDraftMutateAsync).toHaveBeenNthCalledWith(2, expect.objectContaining({
            draftId: 91,
            title: 'Current editor title',
            updatedAt: '2025-01-01T00:00:00.000Z',
        }))
        expect(mocks.saveDraftMutateAsync).toHaveBeenNthCalledWith(3, expect.objectContaining({
            draftId: 91,
            title: 'Current editor title',
            updatedAt: '2025-01-02T00:00:00.000Z',
        }))
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Current editor title',
            updatedAt: '2025-01-03T00:00:00.000Z',
        }))
    })

    it('restores the newest server draft even when local storage has no draft id', async () => {
        const { composable, appliedDrafts, payloadRef } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            originalPostId: 7,
        }))

        mocks.getMyDrafts.mockResolvedValueOnce({
            data: {
                data: {
                    content: [{
                        draftId: 13,
                        boardId: 1,
                        boardUrl: 'free',
                        boardName: 'Free',
                        originalPostId: 7,
                        updatedAt: '2025-01-01T00:00:00.000Z',
                    }],
                    page: 0,
                    size: 50,
                    totalElements: 1,
                    totalPages: 1,
                    hasNext: false,
                    hasPrevious: false,
                },
            },
        })
        mocks.getDraft.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 13,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Recovered draft',
                    contents: '<p>Recovered</p>',
                    categoryId: 4,
                    tags: ['tag'],
                    fileIds: [21],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: true,
                    originalPostId: 7,
                    updatedAt: '2025-01-02T00:00:00.000Z',
                    modifiedAt: '2025-01-02T00:00:00.000Z',
                },
            },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(mocks.getMyDrafts).toHaveBeenCalledWith({ page: 0, size: 50 })
        expect(mocks.getDraft).toHaveBeenCalledWith(13)
        expect(appliedDrafts[0]).toEqual(expect.objectContaining({
            title: 'Recovered draft',
            fileIds: [21],
            originalPostId: 7,
        }))
        expect(composable.restoreSource.value).toBe('server')

        payloadRef.value = {
            ...payloadRef.value,
            title: 'Autosaved title',
        }
    })

    it('resets draft restoration tracking for a changed form identity', async () => {
        const storageKeyRef = ref('noviis:test:draft:first')
        Storage.set('noviis:test:draft:first', {
            boardUrl: 'free',
            title: 'First local draft',
            contents: 'First contents',
            fileIds: [],
        })
        Storage.set('noviis:test:draft:second', {
            boardUrl: 'free',
            title: 'Second local draft',
            contents: 'Second contents',
            fileIds: [],
        })
        const { composable, appliedDrafts } = mountComposable(undefined, storageKeyRef)

        await composable.restoreDraft()
        composable.resetSession()
        storageKeyRef.value = 'noviis:test:draft:second'
        await composable.restoreDraft()

        expect(appliedDrafts.map((draft) => draft.title)).toEqual([
            'First local draft',
            'Second local draft',
        ])
    })

    it('ignores an in-flight save result after the form identity resets', async () => {
        let resolveSave: (value: unknown) => void = () => undefined
        mocks.saveDraftMutateAsync.mockReturnValueOnce(new Promise((resolve) => {
            resolveSave = resolve
        }))
        const { composable } = mountComposable()

        const savePromise = composable.saveNow()
        composable.resetSession()
        resolveSave({
            data: {
                data: {
                    draftId: 91,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Stale draft',
                    contents: 'Stale body',
                    tags: [],
                    fileIds: [],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-01T00:00:00.000Z',
                    modifiedAt: '2025-01-01T00:00:00.000Z',
                },
            },
        })

        await expect(savePromise).resolves.toBeNull()
        expect(composable.draftId.value).toBeNull()
        expect(composable.lastSavedAt.value).toBeNull()
    })

    it('does not auto-restore create drafts when multiple server drafts match the same board', async () => {
        const { composable, appliedDrafts } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            originalPostId: undefined,
        }))

        mocks.getMyDrafts.mockResolvedValueOnce({
            data: {
                data: {
                    content: [{
                        draftId: 13,
                        boardId: 1,
                        boardUrl: 'free',
                        boardName: 'Free',
                        originalPostId: null,
                        updatedAt: '2025-01-01T00:00:00.000Z',
                    }],
                    page: 0,
                    size: 50,
                    totalElements: 2,
                    totalPages: 2,
                    hasNext: true,
                    hasPrevious: false,
                },
            },
        })
        mocks.getMyDrafts.mockResolvedValueOnce({
            data: {
                data: {
                    content: [{
                        draftId: 22,
                        boardId: 1,
                        boardUrl: 'free',
                        boardName: 'Free',
                        originalPostId: null,
                        updatedAt: '2025-01-02T00:00:00.000Z',
                    }],
                    page: 1,
                    size: 50,
                    totalElements: 2,
                    totalPages: 2,
                    hasNext: false,
                    hasPrevious: true,
                },
            },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(mocks.getMyDrafts).toHaveBeenNthCalledWith(1, { page: 0, size: 50 })
        expect(mocks.getMyDrafts).toHaveBeenNthCalledWith(2, { page: 1, size: 50 })
        expect(mocks.getDraft).not.toHaveBeenCalled()
        expect(appliedDrafts).toHaveLength(0)
        expect(composable.restoreSource.value).toBe('idle')
    })

    it('debounces autosave and cleans up local/server drafts on publish cleanup', async () => {
        const { composable, payloadRef } = mountComposable()

        payloadRef.value = {
            ...payloadRef.value,
            title: 'Autosave me',
        }
        composable.scheduleAutosave()
        await vi.advanceTimersByTimeAsync(1500)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)

        await composable.cleanupDraft()

        expect(mocks.deleteDraftMutateAsync).toHaveBeenCalledWith(91)
        expect(Storage.get('noviis:test:draft')).toBeNull()
    })

    it('preserves local recovery state when published draft cleanup fails', async () => {
        const { composable } = mountComposable()

        await composable.saveNow()
        mocks.deleteDraftMutateAsync.mockRejectedValueOnce(new Error('cleanup failed'))

        await expect(composable.cleanupDraft()).rejects.toThrow('cleanup failed')
        expect(composable.draftId.value).toBe(91)
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Draft title',
        }))
    })

    it('clears local recovery when the published draft was already deleted by the server', async () => {
        const { composable } = mountComposable()

        await composable.saveNow()
        mocks.deleteDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404 },
        })

        await composable.cleanupDraft()

        expect(mocks.deleteDraftMutateAsync).toHaveBeenCalledWith(91)
        expect(composable.draftId.value).toBeNull()
        expect(Storage.get('noviis:test:draft')).toBeNull()
    })

    it('deletes an existing server draft when all meaningful content is cleared', async () => {
        const { composable, payloadRef } = mountComposable()

        await composable.saveNow()
        payloadRef.value = {
            ...payloadRef.value,
            title: '',
            contents: '',
            fileIds: [],
        }

        await composable.saveNow()

        expect(mocks.deleteDraftMutateAsync).toHaveBeenCalledWith(91)
        expect(composable.draftId.value).toBeNull()
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')
    })

    it('keeps the draft tracking state when deleting an empty server draft fails', async () => {
        const { composable, payloadRef } = mountComposable()

        await composable.saveNow()
        mocks.deleteDraftMutateAsync.mockRejectedValueOnce(new Error('delete failed'))
        payloadRef.value = {
            ...payloadRef.value,
            title: '',
            contents: '',
            fileIds: [],
        }

        await expect(composable.saveNow()).rejects.toThrow('delete failed')
        expect(composable.draftId.value).toBe(91)
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Draft title',
        }))
    })

    it('drops a stale local draft id when the server draft was already deleted', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Local draft',
            contents: 'Local contents',
            draftId: 91,
            updatedAt: '2025-01-01T00:00:00.000Z',
        })

        const { composable, appliedDrafts } = mountComposable()
        mocks.getDraft.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404 },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(mocks.getDraft).toHaveBeenCalledWith(91)
        expect(composable.draftId.value).toBeNull()
        expect(appliedDrafts[0]).toEqual(expect.objectContaining({
            title: 'Local draft',
            contents: 'Local contents',
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')
    })

    it('falls back to a replacement server draft after a stale local draft id returns 404', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Local draft',
            contents: 'Local contents',
            draftId: 91,
            updatedAt: '2025-01-01T00:00:00.000Z',
            originalPostId: 7,
        })

        const { composable, appliedDrafts } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            originalPostId: 7,
        }))

        mocks.getDraft.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404 },
        })
        mocks.getMyDrafts.mockResolvedValueOnce({
            data: {
                data: {
                    content: [{
                        draftId: 13,
                        boardId: 1,
                        boardUrl: 'free',
                        boardName: 'Free',
                        originalPostId: 7,
                        updatedAt: '2025-01-03T00:00:00.000Z',
                    }],
                    page: 0,
                    size: 50,
                    totalElements: 1,
                    totalPages: 1,
                    hasNext: false,
                    hasPrevious: false,
                },
            },
        })
        mocks.getDraft.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 13,
                    boardId: 1,
                    boardUrl: 'free',
                    boardName: 'Free',
                    title: 'Recovered replacement',
                    contents: '<p>Recovered replacement</p>',
                    tags: [],
                    fileIds: [],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    originalPostId: 7,
                    updatedAt: '2025-01-03T00:00:00.000Z',
                    modifiedAt: '2025-01-03T00:00:00.000Z',
                },
            },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(mocks.getDraft).toHaveBeenNthCalledWith(1, 91)
        expect(mocks.getMyDrafts).toHaveBeenCalledWith({ page: 0, size: 50 })
        expect(mocks.getDraft).toHaveBeenNthCalledWith(2, 13)
        expect(appliedDrafts[0]).toEqual(expect.objectContaining({
            draftId: 13,
            title: 'Recovered replacement',
        }))
        expect(composable.restoreSource.value).toBe('server')
    })
})
