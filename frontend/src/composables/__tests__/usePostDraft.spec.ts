import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { defineComponent, h, nextTick, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { usePostDraft } from '@/features/board/posts/draft/usePostDraft'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/usePostDraft'
import type { PostDraftData } from '@/api/post'
import { Storage } from '@/utils/storage'
import { markDraftDeletedLocally } from '@/features/board/posts/draft/postDraftTombstone'

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
        saveDraftConfig: undefined as (() => { signal?: AbortSignal } | undefined) | undefined,
    }
})

vi.mock('@/features/board/posts/queries/usePost', () => ({
    usePost: () => ({
        useSaveDraft: (resolveConfig: () => { signal?: AbortSignal } | undefined) => {
            mocks.saveDraftConfig = resolveConfig
            return {
            isPending: ref(false),
            mutateAsync: mocks.saveDraftMutateAsync,
            }
        },
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

function mountComposable(payloadRef: Ref<PostDraftData> = ref({
    boardUrl: 'free',
    title: 'Draft title',
    contents: 'Draft body',
    fileIds: [7],
    originalPostId: undefined as number | undefined,
}), storageKeyRef = ref('noviis:test:draft'), enabledRef = ref(true), ownerIdRef = ref<number | null>(null), onServerSaved?: (payload: PostDraftData) => void) {
    const appliedDrafts: DraftRecoverySnapshot[] = []
    let composable: ReturnType<typeof usePostDraft> | null = null

    const TestHarness = defineComponent({
        setup() {
            composable = usePostDraft({
                enabled: enabledRef,
                storageKey: storageKeyRef,
                ownerId: ownerIdRef,
                buildPayload: () => payloadRef.value,
                applyDraft: (draft) => appliedDrafts.push(draft),
                onServerSaved,
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
        vi.setSystemTime(new Date('2026-07-07T12:00:00.000Z'))
        Storage.clear()
        vi.clearAllMocks()
        mocks.saveDraftConfig = undefined
        mocks.saveDraftMutateAsync.mockResolvedValue({
            data: {
                data: {
                    draftId: 91,
                    clientDraftKey: 'client-draft-key-1234',
                    version: 0,
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
            clientDraftKey: expect.any(String),
            version: undefined,
            updatedAt: undefined,
        })
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            clientDraftKey: 'client-draft-key-1234',
            version: 0,
            fileIds: [7],
            updatedAt: '2025-01-01T00:00:00.000Z',
            clientModifiedAt: '2026-07-07T12:00:00.000Z',
            hasLocalChanges: false,
        }))
    })

    it('sends the numeric version returned by the previous save', async () => {
        const { composable, payloadRef } = mountComposable()
        await composable.saveNow()
        payloadRef.value = { ...payloadRef.value, title: 'Second revision' }

        await composable.saveNow()

        expect(mocks.saveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
            draftId: 91,
            clientDraftKey: 'client-draft-key-1234',
            version: 0,
            title: 'Second revision',
        }))
    })

    it('reports the exact payload whose files were transferred to a server draft', async () => {
        const onServerSaved = vi.fn()
        const payload = ref<PostDraftData>({
            boardUrl: 'free',
            title: 'Draft with upload',
            contents: '<img src="/api/v1/files/7">',
            fileIds: [7],
        })
        const { composable } = mountComposable(
            payload,
            ref('noviis:test:server-owned-upload'),
            ref(true),
            ref(1),
            onServerSaved,
        )

        await composable.saveNow()

        expect(onServerSaved).toHaveBeenCalledExactlyOnceWith(payload.value)
    })

    it('aborts an in-flight draft request when the draft session resets', async () => {
        let resolveSave!: (value: unknown) => void
        mocks.saveDraftMutateAsync.mockImplementationOnce(() => new Promise((resolve) => {
            resolveSave = resolve
        }))
        const { composable } = mountComposable()

        const pendingSave = composable.saveNow()
        await Promise.resolve()
        const signal = mocks.saveDraftConfig?.()?.signal
        expect(signal).toBeInstanceOf(AbortSignal)

        composable.resetSession()
        expect(signal?.aborted).toBe(true)
        resolveSave({ data: { data: { draftId: 91 } } })
        await expect(pendingSave).resolves.toBeNull()
    })

    it('uses the current time when a saved draft response omits version timestamps', async () => {
        mocks.saveDraftMutateAsync.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 92,
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
                },
            },
        })
        const { composable } = mountComposable()

        await composable.saveNow()

        expect(composable.lastSavedAt.value).toBe('2026-07-07T12:00:00.000Z')
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 92,
            updatedAt: '2026-07-07T12:00:00.000Z',
        }))
    })

    it('stops autosave on an outdated draft and reloads the server copy explicitly', async () => {
        const { composable, payloadRef, appliedDrafts } = mountComposable()

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
        await expect(composable.saveNow()).rejects.toMatchObject({
            response: { status: 409 },
        })

        expect(composable.draftConflict.value).toBe(true)
        expect(mocks.getDraft).not.toHaveBeenCalled()
        expect(mocks.saveDraftMutateAsync).toHaveBeenNthCalledWith(2, expect.objectContaining({
            draftId: 91,
            title: 'Current editor title',
            updatedAt: '2025-01-01T00:00:00.000Z',
        }))
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)

        expect(await composable.reloadServerDraft()).toBe(true)
        expect(composable.draftConflict.value).toBe(false)
        expect(appliedDrafts[0]).toEqual(expect.objectContaining({
            title: 'Server title',
            updatedAt: '2025-01-02T00:00:00.000Z',
        }))
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Server title',
            updatedAt: '2025-01-02T00:00:00.000Z',
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
                    poll: {
                        question: 'Pick one',
                        options: ['A', 'B'],
                        multipleChoiceEnabled: true,
                        anonymousEnabled: false,
                        closesAt: null,
                    },
                    seriesId: 42,
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
            poll: expect.objectContaining({ options: ['A', 'B'] }),
            seriesId: 42,
            originalPostId: 7,
        }))
        expect(composable.restoreSource.value).toBe('server')

        payloadRef.value = {
            ...payloadRef.value,
            title: 'Autosaved title',
        }
    })

    it('stops saving when a scheduled publication protects the draft', async () => {
        const { composable } = mountComposable()
        await composable.saveNow()
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 409, data: { error: { code: 'P005' } } },
        })

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 409 } })

        expect(composable.draftProtected.value).toBe(true)
        expect(composable.lastSaveFailed.value).toBe(false)
        await expect(composable.saveNow()).resolves.toBeNull()
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
    })

    it('preserves local changes when the server advanced and lets the user overwrite explicitly', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Unsaved local title',
            contents: 'Unsaved local body',
            fileIds: [],
            draftId: 91,
            updatedAt: '2025-01-01T00:00:00.000Z',
            clientModifiedAt: '2026-07-06T00:00:00.000Z',
            hasLocalChanges: true,
        })
        const payload = ref<PostDraftData>({
            boardUrl: 'free',
            title: 'Unsaved local title',
            contents: 'Unsaved local body',
            fileIds: [],
        })
        const serverDraft = {
            draftId: 91,
            boardId: 1,
            boardUrl: 'free',
            boardName: 'Free',
            title: 'New server title',
            contents: 'New server body',
            tags: [],
            fileIds: [],
            isNotice: false,
            isNsfw: false,
            isSpoiler: false,
            isSecret: false,
            updatedAt: '2025-01-02T00:00:00.000Z',
            modifiedAt: '2025-01-02T00:00:00.000Z',
        }
        mocks.getDraft.mockResolvedValue({ data: { data: serverDraft } })
        mocks.saveDraftMutateAsync.mockResolvedValueOnce({
            data: { data: { ...serverDraft, ...payload.value, updatedAt: '2025-01-04T00:00:00.000Z' } },
        })
        const { composable, appliedDrafts } = mountComposable(payload)

        await composable.restoreDraft()

        expect(appliedDrafts[0]).toEqual(expect.objectContaining({ title: 'Unsaved local title' }))
        expect(composable.draftConflict.value).toBe(true)
        expect(composable.restoreSource.value).toBe('local')
        expect(composable.updatedAt.value).toBe('2025-01-01T00:00:00.000Z')

        await expect(composable.keepLocalDraft()).resolves.toBe(true)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({
            draftId: 91,
            title: 'Unsaved local title',
            updatedAt: '2025-01-02T00:00:00.000Z',
        }))
        expect(composable.draftConflict.value).toBe(false)
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            hasLocalChanges: false,
            updatedAt: '2025-01-04T00:00:00.000Z',
        }))
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

    it('does not resurrect recovery state when it is cleared during an in-flight save', async () => {
        let resolveSave: (value: unknown) => void = () => undefined
        mocks.saveDraftMutateAsync.mockReturnValueOnce(new Promise((resolve) => {
            resolveSave = resolve
        }))
        const { composable } = mountComposable()

        const savePromise = composable.saveNow()
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({ title: 'Draft title' }))

        composable.clearRecovery()
        resolveSave({
            data: {
                data: {
                    draftId: 91,
                    boardUrl: 'free',
                    title: 'Late server draft',
                    contents: 'Late body',
                    tags: [],
                    fileIds: [],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-01T00:00:00.000Z',
                },
            },
        })

        await expect(savePromise).resolves.toBeNull()
        expect(Storage.get('noviis:test:draft')).toBeNull()
        expect(composable.draftId.value).toBeNull()
        expect(composable.updatedAt.value).toBeNull()
        expect(composable.lastSavedAt.value).toBeNull()
    })

    it('does not let a rejected save from an old form identity set failure or conflict state', async () => {
        let rejectSave: (reason: unknown) => void = () => undefined
        mocks.saveDraftMutateAsync.mockReturnValueOnce(new Promise((_resolve, reject) => {
            rejectSave = reject
        }))
        const { composable } = mountComposable()

        const savePromise = composable.saveNow()
        composable.resetSession()
        rejectSave({
            isAxiosError: true,
            response: { status: 409, data: { error: { code: 'P004' } } },
        })

        await expect(savePromise).rejects.toMatchObject({ response: { status: 409 } })
        expect(composable.draftConflict.value).toBe(false)
        expect(composable.lastSaveFailed.value).toBe(false)
    })

    it('ignores a server reload that finishes after the form identity changes', async () => {
        const { composable, appliedDrafts } = mountComposable()
        await composable.saveNow()
        let resolveDraft: (value: unknown) => void = () => undefined
        mocks.getDraft.mockReturnValueOnce(new Promise((resolve) => {
            resolveDraft = resolve
        }))

        const reloadPromise = composable.reloadServerDraft()
        composable.resetSession()
        resolveDraft({
            data: {
                data: {
                    draftId: 91,
                    boardUrl: 'free',
                    title: 'Stale server title',
                    contents: 'Stale server body',
                    tags: [],
                    fileIds: [],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-02T00:00:00.000Z',
                },
            },
        })

        await expect(reloadPromise).resolves.toBe(false)
        expect(composable.draftId.value).toBeNull()
        expect(appliedDrafts).toHaveLength(0)
    })

    it('queues the latest edit while a previous save is still in flight', async () => {
        let resolveFirstSave: (value: unknown) => void = () => undefined
        mocks.saveDraftMutateAsync.mockReturnValueOnce(new Promise((resolve) => {
            resolveFirstSave = resolve
        }))
        mocks.saveDraftMutateAsync.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 91,
                    clientDraftKey: 'client-draft-key-1234',
                    version: 1,
                    boardUrl: 'free',
                    title: 'Latest editor title',
                    contents: 'Draft body',
                    tags: [],
                    fileIds: [7],
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    updatedAt: '2025-01-01T00:00:01.000Z',
                    modifiedAt: '2025-01-01T00:00:01.000Z',
                },
            },
        })
        const { composable, payloadRef } = mountComposable()

        const firstSave = composable.saveNow()
        payloadRef.value = { ...payloadRef.value, title: 'Latest editor title' }
        composable.writeLocalSnapshot()
        const queuedSave = composable.saveNow()
        resolveFirstSave({
            data: {
                data: {
                    draftId: 91,
                    boardUrl: 'free',
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

        await Promise.all([firstSave, queuedSave])

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
        expect(mocks.saveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
            draftId: 91,
            title: 'Latest editor title',
        }))
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Latest editor title',
        }))
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

    it('cancels a pending autosave when drafts are disabled', async () => {
        const enabled = ref(true)
        const { composable } = mountComposable(undefined, ref('noviis:test:draft'), enabled)

        composable.scheduleAutosave()
        enabled.value = false
        await nextTick()
        await vi.advanceTimersByTimeAsync(1500)

        expect(mocks.saveDraftMutateAsync).not.toHaveBeenCalled()
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
        expect(composable.restoreSource.value).toBe('local')
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')
    })

    it('updates an existing server draft when only its category remains', async () => {
        const { composable, payloadRef } = mountComposable()
        await composable.saveNow()
        payloadRef.value = {
            ...payloadRef.value,
            title: '',
            contents: '',
            fileIds: [],
            categoryId: 5,
        }

        await composable.saveNow()

        expect(mocks.deleteDraftMutateAsync).not.toHaveBeenCalled()
        expect(mocks.saveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
            draftId: 91,
            categoryId: 5,
        }))
        expect(composable.draftId.value).toBe(91)
    })

    it('keeps a new category-only draft in the browser without creating a server draft', async () => {
        const { composable } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            categoryId: 5,
            originalPostId: undefined,
        }))

        await composable.saveNow()

        expect(mocks.saveDraftMutateAsync).not.toHaveBeenCalled()
        expect(mocks.deleteDraftMutateAsync).not.toHaveBeenCalled()
        expect(composable.lastSaveScope.value).toBe('browser')
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({ categoryId: 5 }))
    })

    it('persists poll and series settings in a server draft', async () => {
        const { composable } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            seriesId: 7,
            poll: { question: 'Pick', options: ['A', 'B'] },
            originalPostId: undefined,
        }))

        await composable.saveNow()

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({
            seriesId: 7,
            poll: { question: 'Pick', options: ['A', 'B'] },
        }))
        expect(composable.lastSaveScope.value).toBe('server')
    })

    it('does not restore a locally deleted server draft from browser recovery storage', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Deleted local copy',
            contents: 'Should not return',
            draftId: 91,
            updatedAt: '2025-01-01T00:00:00.000Z',
        })
        markDraftDeletedLocally(7, 91)
        const { composable, appliedDrafts } = mountComposable(
            undefined,
            ref('noviis:test:draft'),
            ref(true),
            ref(7),
        )

        await composable.restoreDraft()

        expect(mocks.getDraft).not.toHaveBeenCalled()
        expect(appliedDrafts).toHaveLength(0)
        expect(Storage.get('noviis:test:draft')).toBeNull()
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
            title: 'Local draft',
            contents: 'Local contents',
        }))
        expect(composable.draftId.value).toBe(13)
        expect(composable.draftConflict.value).toBe(true)
        expect(composable.restoreSource.value).toBe('local')
    })

    it('does not overwrite edits made while the initial server recovery is in flight', async () => {
        let resolveDraft: (value: unknown) => void = () => undefined
        mocks.getMyDrafts.mockResolvedValueOnce({
            data: { data: { content: [{ draftId: 91, boardUrl: 'free', originalPostId: null }], hasNext: false } },
        })
        mocks.getDraft.mockReturnValueOnce(new Promise((resolve) => {
            resolveDraft = resolve
        }))
        const { composable, payloadRef, appliedDrafts } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
        }))

        const restoring = composable.restoreDraft()
        await Promise.resolve()
        payloadRef.value = { ...payloadRef.value, title: 'Typed while restoring' }
        composable.writeLocalSnapshot()
        resolveDraft({
            data: { data: {
                draftId: 91,
                boardUrl: 'free',
                title: 'Server title',
                contents: 'Server body',
                fileIds: [],
                updatedAt: '2026-07-07T11:00:00.000Z',
            } },
        })
        await restoring

        expect(appliedDrafts).toHaveLength(0)
        expect(composable.draftConflict.value).toBe(true)
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            title: 'Typed while restoring',
            hasLocalChanges: true,
        }))
    })

    it('does not overwrite edits made while reloading the server conflict copy', async () => {
        const { composable, payloadRef, appliedDrafts } = mountComposable()
        await composable.saveNow()
        let resolveDraft: (value: unknown) => void = () => undefined
        mocks.getDraft.mockReturnValueOnce(new Promise((resolve) => {
            resolveDraft = resolve
        }))

        const reloading = composable.reloadServerDraft()
        payloadRef.value = { ...payloadRef.value, title: 'New edit during reload' }
        composable.writeLocalSnapshot()
        resolveDraft({
            data: { data: {
                draftId: 91,
                boardUrl: 'free',
                title: 'Server title',
                contents: 'Server body',
                fileIds: [],
                updatedAt: '2026-07-07T11:00:00.000Z',
            } },
        })

        await expect(reloading).resolves.toBe(false)
        expect(appliedDrafts).toHaveLength(0)
        expect(composable.draftConflict.value).toBe(true)
    })

    it('reports a browser-only save failure when localStorage rejects the write', async () => {
        const setItem = vi.spyOn(Storage, 'set').mockReturnValue(false)
        const { composable } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            categoryId: 5,
        }))

        await expect(composable.saveNow()).rejects.toThrow('DRAFT_LOCAL_STORAGE_FAILED')
        expect(composable.lastLocalSaveFailed.value).toBe(true)
        expect(composable.lastSaveScope.value).toBeNull()
        setItem.mockRestore()
    })

    it('retries a failed server recovery when connectivity returns', async () => {
        mocks.getMyDrafts.mockRejectedValueOnce(new Error('offline'))
        const { composable } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
        }))

        await composable.restoreDraft()
        expect(composable.restoreFailed.value).toBe(true)

        window.dispatchEvent(new Event('online'))
        await Promise.resolve()
        await Promise.resolve()

        expect(mocks.getMyDrafts).toHaveBeenCalledTimes(2)
        expect(composable.restoreFailed.value).toBe(false)
    })

    it('stops autosave when another tab advances the same draft', async () => {
        const { composable, payloadRef } = mountComposable()
        await composable.saveNow()
        payloadRef.value = { ...payloadRef.value, title: 'Unsaved tab edit' }
        composable.writeLocalSnapshot()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                boardUrl: 'free',
                title: 'Other tab edit',
                contents: 'Other tab body',
                updatedAt: '2026-07-07T13:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(composable.draftConflict.value).toBe(true)
    })
})
