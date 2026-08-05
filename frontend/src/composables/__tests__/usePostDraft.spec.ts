import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { defineComponent, h, nextTick, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'
import { isTransientDraftSaveError, usePostDraft } from '@/features/board/posts/draft/usePostDraft'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/usePostDraft'
import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import { Storage } from '@/utils/storage'
import {
    closeDraftDeletedChannelForTest,
    getDraftTombstoneKey,
    markDraftDeletedLocally,
} from '@/features/board/posts/draft/postDraftTombstone'
import {
    closeDraftScheduledChannelForTest,
    type DraftScheduledEvent,
} from '@/features/board/posts/draft/postDraftScheduledEvent'
import { closeDraftUpdatedChannelForTest } from '@/features/board/posts/draft/postDraftUpdatedEvent'

const mocks = vi.hoisted(() => {
    const saveDraftMutateAsync = vi.fn()
    const deleteDraftMutateAsync = vi.fn()
    const getDraft = vi.fn()
    const getMatchingDraft = vi.fn()
    const loggerError = vi.fn()
    const reportDraftOperationalEvent = vi.fn()

    return {
        saveDraftMutateAsync,
        deleteDraftMutateAsync,
        getDraft,
        getMatchingDraft,
        loggerError,
        reportDraftOperationalEvent,
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
        getMatchingDraft: mocks.getMatchingDraft,
    },
}))

vi.mock('@/utils/logger', () => ({
    default: { error: mocks.loggerError },
}))

vi.mock('@/utils/clientErrorReporter', () => ({
    reportDraftOperationalEvent: mocks.reportDraftOperationalEvent,
}))

function mountComposable(payloadRef: Ref<PostDraftData> = ref({
    boardUrl: 'free',
    title: 'Draft title',
    contents: 'Draft body',
    fileIds: [7],
    originalPostId: undefined as number | undefined,
}), storageKeyRef = ref('noviis:test:draft'), enabledRef = ref(true), ownerIdRef = ref<number | null>(null), onServerSaved?: (payload: PostDraftData, savedDraft: DraftPost) => void, resolveStorageKey?: (draftId: number) => string, onServerReferencesReset?: (savedDraft: DraftPost) => void) {
    const appliedDrafts: DraftRecoverySnapshot[] = []
    let composable: ReturnType<typeof usePostDraft> | null = null

    const TestHarness = defineComponent({
        setup() {
            composable = usePostDraft({
                enabled: enabledRef,
                storageKey: storageKeyRef,
                resolveStorageKey,
                ownerId: ownerIdRef,
                buildPayload: () => payloadRef.value,
                applyDraft: (draft) => appliedDrafts.push(draft),
                onServerSaved,
                onServerReferencesReset,
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
        mocks.getMatchingDraft.mockResolvedValue({
            data: {
                data: {
                    draftId: null,
                    multipleMatchesFound: false,
                },
            },
        })
    })

    afterEach(() => {
        closeDraftScheduledChannelForTest()
        closeDraftDeletedChannelForTest()
        closeDraftUpdatedChannelForTest()
        vi.restoreAllMocks()
        vi.unstubAllGlobals()
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

        expect(onServerSaved).toHaveBeenCalledExactlyOnceWith(
            payload.value,
            expect.objectContaining({ draftId: 91 }),
        )
    })

    it('reports references removed by the server and keeps that recovery state', async () => {
        mocks.saveDraftMutateAsync.mockResolvedValueOnce({
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
                    fileIds: [],
                    seriesId: null,
                    isNotice: false,
                    isNsfw: false,
                    isSpoiler: false,
                    isSecret: false,
                    staleReferencesReset: true,
                    updatedAt: '2025-01-01T00:00:00.000Z',
                },
            },
        })
        const onServerReferencesReset = vi.fn()
        const { composable } = mountComposable(
            undefined,
            ref('noviis:test:draft'),
            ref(true),
            ref(1),
            undefined,
            undefined,
            onServerReferencesReset,
        )

        await composable.saveNow()

        expect(composable.staleReferencesReset.value).toBe(true)
        expect(onServerReferencesReset).toHaveBeenCalledExactlyOnceWith(expect.objectContaining({
            draftId: 91,
            fileIds: [],
            seriesId: null,
        }), expect.objectContaining({ fileIds: [7] }))
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({ fileIds: [] }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('seriesId')
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

    it('treats the reloaded server copy as clean for later tab synchronization', async () => {
        const { composable, payloadRef, appliedDrafts } = mountComposable()
        await composable.saveNow()
        payloadRef.value = { ...payloadRef.value, title: 'Unsaved local title' }
        composable.writeLocalSnapshot()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                clientDraftKey: 'client-draft-key-1234',
                version: 1,
                boardUrl: 'free',
                title: 'Server title',
                contents: 'Draft body',
                fileIds: [7],
                updatedAt: '2025-01-02T00:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            }),
        }))
        expect(composable.draftConflict.value).toBe(true)

        mocks.getDraft.mockResolvedValueOnce({
            data: { data: {
                draftId: 91,
                clientDraftKey: 'client-draft-key-1234',
                version: 1,
                boardUrl: 'free',
                title: 'Server title',
                contents: 'Draft body',
                fileIds: [7],
                updatedAt: '2025-01-02T00:00:00.000Z',
            } },
        })
        await expect(composable.reloadServerDraft()).resolves.toBe(true)
        payloadRef.value = { ...payloadRef.value, title: 'Server title' }

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                clientDraftKey: 'client-draft-key-1234',
                version: 2,
                boardUrl: 'free',
                title: 'Later server title',
                contents: 'Draft body',
                fileIds: [7],
                updatedAt: '2025-01-03T00:00:00.000Z',
                clientInstanceId: 'third-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(composable.draftConflict.value).toBe(false)
        expect(appliedDrafts.at(-1)).toEqual(expect.objectContaining({ title: 'Later server title' }))
    })

    it('restores the newest server draft even when local storage has no draft id', async () => {
        const { composable, appliedDrafts, payloadRef } = mountComposable(ref({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
            originalPostId: 7,
        }))

        mocks.getMatchingDraft.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 13,
                    multipleMatchesFound: false,
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

        expect(mocks.getMatchingDraft).toHaveBeenCalledWith({ boardUrl: 'free', originalPostId: 7 })
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

    it('enters protected state when direct recovery targets a scheduled draft', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Scheduled draft',
            contents: 'Scheduled body',
            draftId: 91,
            clientModifiedAt: '2026-07-07T11:30:00.000Z',
            hasLocalChanges: false,
        })
        mocks.getDraft.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 409, data: { error: { code: 'P005' } } },
        })
        const { composable, appliedDrafts } = mountComposable()

        await composable.restoreDraft()

        expect(composable.draftProtected.value).toBe(true)
        expect(composable.restoreFailed.value).toBe(false)
        expect(appliedDrafts).toHaveLength(0)
        expect(Storage.has('noviis:test:draft')).toBe(false)
    })

    it('stops autosave immediately when another tab schedules the draft', async () => {
        const ownerId = ref<number | null>(1)
        const { composable } = mountComposable(undefined, undefined, undefined, ownerId)
        await composable.saveNow()
        mocks.saveDraftMutateAsync.mockClear()
        composable.scheduleAutosave()
        const message: DraftScheduledEvent = {
            type: 'draft-scheduled',
            eventId: 'scheduled-event-1',
            sourceId: 'peer-tab',
            ownerId: '1',
            draftId: 91,
            clientDraftKey: 'client-draft-key-1234',
            storageKey: 'noviis:test:draft',
            at: Date.now(),
        }

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:draft-scheduled-event',
            newValue: JSON.stringify(message),
        }))
        await vi.advanceTimersByTimeAsync(10_000)

        expect(composable.draftProtected.value).toBe(true)
        expect(mocks.saveDraftMutateAsync).not.toHaveBeenCalled()
        expect(Storage.has('noviis:test:draft')).toBe(false)
    })

    it('preserves unsaved edits as a detached local draft when another tab schedules the server draft', async () => {
        const ownerId = ref<number | null>(1)
        const { composable, payloadRef, appliedDrafts } = mountComposable(
            undefined,
            undefined,
            undefined,
            ownerId,
        )
        await composable.saveNow()
        const previousClientDraftKey = composable.clientDraftKey.value
        payloadRef.value = { ...payloadRef.value, title: 'Unsaved protected edit' }
        composable.writeLocalSnapshot()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:draft-scheduled-event',
            newValue: JSON.stringify({
                type: 'draft-scheduled',
                eventId: 'scheduled-event-with-local-edits',
                sourceId: 'peer-tab',
                ownerId: '1',
                draftId: 91,
                clientDraftKey: previousClientDraftKey,
                storageKey: 'noviis:test:draft',
                at: Date.now(),
            } satisfies DraftScheduledEvent),
        }))

        expect(composable.draftProtected.value).toBe(true)
        expect(composable.protectedDraftForkAvailable.value).toBe(true)
        expect(composable.draftId.value).toBeNull()
        expect(composable.clientDraftKey.value).not.toBe(previousClientDraftKey)
        expect(appliedDrafts.at(-1)).toEqual(expect.objectContaining({
            title: 'Unsaved protected edit',
            fileIds: [],
            hasLocalChanges: true,
        }))
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            title: 'Unsaved protected edit',
            hasLocalChanges: true,
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')

        mocks.saveDraftMutateAsync.mockClear()
        await composable.saveProtectedDraftAsNew()

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledWith(expect.objectContaining({
            draftId: undefined,
        }))
        expect(composable.draftProtected.value).toBe(false)
        expect(composable.protectedDraftForkAvailable.value).toBe(false)
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

    it('ignores restore status returned after the form identity resets', async () => {
        let resolveDrafts: (value: unknown) => void = () => undefined
        mocks.getMatchingDraft.mockReturnValueOnce(new Promise((resolve) => {
            resolveDrafts = resolve
        }))
        const { composable } = mountComposable()

        const restoring = composable.restoreDraft()
        composable.resetSession()
        resolveDrafts({
            data: {
                data: {
                    draftId: null,
                    multipleMatchesFound: true,
                },
            },
        })
        await restoring

        expect(composable.multipleDraftsFound.value).toBe(false)
        expect(composable.restoreFailed.value).toBe(false)
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

        mocks.getMatchingDraft.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: null,
                    multipleMatchesFound: true,
                },
            },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(mocks.getMatchingDraft).toHaveBeenCalledExactlyOnceWith({ boardUrl: 'free' })
        expect(mocks.getDraft).not.toHaveBeenCalled()
        expect(appliedDrafts).toHaveLength(0)
        expect(composable.restoreSource.value).toBe('idle')
        expect(composable.multipleDraftsFound.value).toBe(true)
    })

    it('debounces autosave', async () => {
        const { composable, payloadRef } = mountComposable()

        payloadRef.value = {
            ...payloadRef.value,
            title: 'Autosave me',
        }
        composable.scheduleAutosave()
        await vi.advanceTimersByTimeAsync(1500)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)
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

    it('retries a transient save failure with exponential backoff', async () => {
        const random = vi.spyOn(Math, 'random').mockReturnValue(0.5)
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({ isAxiosError: true })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ isAxiosError: true })
        expect(composable.lastSaveFailed.value).toBe(true)
        expect(composable.saveRetryScheduled.value).toBe(true)
        expect(composable.saveRetryAttempt.value).toBe(1)
        await vi.advanceTimersByTimeAsync(999)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(1)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
        expect(composable.lastSaveFailed.value).toBe(false)
        expect(composable.saveRetryScheduled.value).toBe(false)
        expect(composable.saveRetryAttempt.value).toBe(0)
        random.mockRestore()
    })

    it('waits for connectivity without consuming retry attempts', async () => {
        let online = false
        const onlineSpy = vi.spyOn(window.navigator, 'onLine', 'get').mockImplementation(() => online)
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({ isAxiosError: true })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ isAxiosError: true })
        expect(composable.saveRetryScheduled.value).toBe(true)
        expect(composable.saveRetryAttempt.value).toBe(0)

        await vi.advanceTimersByTimeAsync(60_000)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)
        expect(composable.saveRetryAttempt.value).toBe(0)

        online = true
        window.dispatchEvent(new Event('online'))
        await vi.advanceTimersByTimeAsync(0)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
        expect(composable.lastSaveFailed.value).toBe(false)
        onlineSpy.mockRestore()
    })

    it('pauses a scheduled retry when connectivity drops', async () => {
        let online = true
        const onlineSpy = vi.spyOn(window.navigator, 'onLine', 'get').mockImplementation(() => online)
        const random = vi.spyOn(Math, 'random').mockReturnValue(0.5)
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({ isAxiosError: true })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ isAxiosError: true })
        expect(composable.saveRetryAttempt.value).toBe(1)

        online = false
        window.dispatchEvent(new Event('offline'))
        expect(composable.saveRetryAttempt.value).toBe(0)
        await vi.advanceTimersByTimeAsync(10_000)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)

        online = true
        window.dispatchEvent(new Event('online'))
        await vi.advanceTimersByTimeAsync(0)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
        expect(composable.lastSaveFailed.value).toBe(false)
        random.mockRestore()
        onlineSpy.mockRestore()
    })

    it('honors Retry-After when throttled', async () => {
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: {
                status: 429,
                headers: { 'retry-after': '5' },
            },
        })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 429 } })
        await vi.advanceTimersByTimeAsync(4999)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(1)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
        expect(composable.lastSaveFailed.value).toBe(false)
    })

    it('does not retry early when Retry-After exceeds one timer window', async () => {
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: {
                status: 429,
                headers: { 'retry-after': '60' },
            },
        })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 429 } })
        await vi.advanceTimersByTimeAsync(59_999)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(1)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
    })

    it('does not retry permanent client errors', async () => {
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 400 },
        })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 400 } })
        await vi.advanceTimersByTimeAsync(60_000)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)
    })

    it('replaces a pending failure retry with the normal debounce after a new edit', async () => {
        const random = vi.spyOn(Math, 'random').mockReturnValue(0.5)
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({ isAxiosError: true })
        const { composable, payloadRef } = mountComposable()
        await expect(composable.saveNow()).rejects.toMatchObject({ isAxiosError: true })

        payloadRef.value = { ...payloadRef.value, title: 'New edit after failure' }
        composable.scheduleAutosave()
        await vi.advanceTimersByTimeAsync(1000)
        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(1)

        await vi.advanceTimersByTimeAsync(500)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(2)
        expect(mocks.saveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
            title: 'New edit after failure',
        }))
        random.mockRestore()
    })

    it('caps repeated transient retries', async () => {
        const random = vi.spyOn(Math, 'random').mockReturnValue(0.5)
        mocks.saveDraftMutateAsync.mockRejectedValue({
            isAxiosError: true,
            response: { status: 503 },
        })
        const { composable } = mountComposable()

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 503 } })
        await vi.advanceTimersByTimeAsync(60_000)

        expect(mocks.saveDraftMutateAsync).toHaveBeenCalledTimes(6)
        expect(composable.saveRetryScheduled.value).toBe(false)
        expect(composable.saveRetryAttempt.value).toBe(5)
        expect(composable.saveRetryExhausted.value).toBe(true)
        expect(mocks.loggerError).toHaveBeenCalledWith(
            'Draft autosave retries exhausted.',
            { event: 'draft_autosave_retry_exhausted', attempts: 5 },
        )
        expect(mocks.reportDraftOperationalEvent)
            .toHaveBeenCalledExactlyOnceWith('autosave_retry_exhausted', { attempts: 5 })

        mocks.saveDraftMutateAsync.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 91,
                    boardUrl: 'free',
                    title: 'Draft title',
                    contents: 'Draft body',
                    tags: [],
                    fileIds: [7],
                    updatedAt: '2026-07-07T12:01:00.000Z',
                },
            },
        })

        await expect(composable.retrySaveNow()).resolves.toEqual(expect.objectContaining({ draftId: 91 }))
        expect(composable.saveRetryAttempt.value).toBe(0)
        expect(composable.saveRetryExhausted.value).toBe(false)
        random.mockRestore()
    })

    it('classifies only network, throttling, and server errors as transient', () => {
        expect(isTransientDraftSaveError({ isAxiosError: true })).toBe(true)
        expect(isTransientDraftSaveError({ isAxiosError: true, response: { status: 429 } })).toBe(true)
        expect(isTransientDraftSaveError({ isAxiosError: true, response: { status: 500 } })).toBe(true)
        expect(isTransientDraftSaveError({ isAxiosError: true, response: { status: 409 } })).toBe(false)
        expect(isTransientDraftSaveError(new Error('local storage failed'))).toBe(false)
    })

    it('deletes an existing server draft when all meaningful content is cleared', async () => {
        const { composable, payloadRef } = mountComposable(
            undefined,
            ref('noviis:test:draft'),
            ref(true),
            ref(7),
        )

        await composable.saveNow()
        payloadRef.value = {
            ...payloadRef.value,
            title: '',
            contents: '',
            fileIds: [],
        }

        await composable.saveNow()

        expect(mocks.deleteDraftMutateAsync).toHaveBeenCalledWith({ draftId: 91, version: 0 })
        expect(composable.draftId.value).toBeNull()
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            boardUrl: 'free',
            title: '',
            contents: '',
            fileIds: [],
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')
        expect(Storage.get('noviis:draft-deleted:7:91')).toEqual({
            deletedAt: '2026-07-07T12:00:00.000Z',
        })
    })

    it('switches a newly assigned draft to its draft-specific storage key', async () => {
        const { composable } = mountComposable(
            undefined,
            ref('noviis:test:draft'),
            ref(true),
            ref(7),
            undefined,
            (draftId) => `noviis:test:draft:${draftId}`,
        )

        await composable.saveNow()

        expect(Storage.has('noviis:test:draft')).toBe(false)
        expect(Storage.get('noviis:test:draft:91')).toEqual(expect.objectContaining({
            draftId: 91,
            clientDraftKey: 'client-draft-key-1234',
            hasLocalChanges: false,
        }))
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

    it('keeps a newer server draft when an old tab tries to delete its empty copy', async () => {
        const { composable, payloadRef } = mountComposable()

        await composable.saveNow()
        mocks.deleteDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 409, data: { error: { code: 'P004' } } },
        })
        payloadRef.value = {
            ...payloadRef.value,
            title: '',
            contents: '',
            fileIds: [],
        }

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 409 } })

        expect(mocks.deleteDraftMutateAsync).toHaveBeenCalledWith({ draftId: 91, version: 0 })
        expect(composable.draftConflict.value).toBe(true)
        expect(composable.draftId.value).toBe(91)
    })

    it('drops a stale local draft id when the server draft was already deleted', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Local draft',
            contents: 'Local contents',
            categoryId: 5,
            fileIds: [31, 32],
            seriesId: 8,
            draftId: 91,
            clientDraftKey: 'client-draft-key-1234',
            version: 4,
            updatedAt: '2025-01-01T00:00:00.000Z',
            clientModifiedAt: '2026-07-07T11:30:00.000Z',
        })

        const { composable, appliedDrafts } = mountComposable()
        mocks.getDraft.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404, data: { error: { code: 'P007' } } },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(mocks.getDraft).toHaveBeenCalledWith(91)
        expect(composable.draftId.value).toBeNull()
        expect(appliedDrafts[0]).toEqual(expect.objectContaining({
            title: 'Local draft',
            contents: 'Local contents',
            categoryId: null,
            fileIds: [],
            seriesId: null,
            staleReferencesReset: true,
        }))
        expect(composable.restoreSource.value).toBe('local')
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            schemaVersion: 1,
            hasLocalChanges: true,
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('version')
        expect(Storage.get('noviis:test:draft')).not.toEqual(expect.objectContaining({
            clientDraftKey: 'client-draft-key-1234',
        }))
    })

    it('keeps local server identity when draft recovery gets a related-resource 404', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Local draft',
            contents: 'Local contents',
            draftId: 91,
            updatedAt: '2025-01-01T00:00:00.000Z',
            clientModifiedAt: '2026-07-07T11:30:00.000Z',
        })

        const { composable, appliedDrafts } = mountComposable()
        mocks.getDraft.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404, data: { error: { code: 'B001' } } },
        })

        await composable.restoreDraft()
        await nextTick()

        expect(composable.restoreFailed.value).toBe(true)
        expect(composable.draftId.value).toBe(91)
        expect(appliedDrafts[0]).toEqual(expect.objectContaining({ title: 'Local draft' }))
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({ draftId: 91 }))
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

    it('stops editing when another tab broadcasts deletion of the current draft', async () => {
        closeDraftDeletedChannelForTest()
        const channels: FakeBroadcastChannel[] = []
        class FakeBroadcastChannel {
            listeners: Array<(event: MessageEvent) => void> = []
            constructor(readonly name: string) {
                channels.push(this)
            }
            addEventListener(_type: string, listener: (event: MessageEvent) => void) {
                this.listeners.push(listener)
            }
            postMessage(message: unknown) {
                channels.filter((candidate) => candidate !== this && candidate.name === this.name)
                    .forEach((candidate) => candidate.listeners.forEach((listener) => listener({ data: message } as MessageEvent)))
            }
            close() {}
        }
        vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
        const ownerId = ref<number | null>(7)
        const { composable } = mountComposable(undefined, undefined, undefined, ownerId)
        await composable.saveNow()
        const peer = new FakeBroadcastChannel('noviis-draft-deleted')
        peer.postMessage({
            type: 'draft-deleted',
            sourceId: 'peer-tab',
            ownerId: '7',
            draftId: '91',
            at: Date.now(),
        })

        expect(composable.draftDeleted.value).toBe(true)
        expect(mocks.reportDraftOperationalEvent).toHaveBeenCalledWith('deleted_in_another_tab')
    })

    it('does not re-adopt a delayed save response after another tab deletes the draft', async () => {
        const ownerId = ref<number | null>(7)
        const { composable } = mountComposable(undefined, undefined, undefined, ownerId)
        await composable.saveNow()
        let resolveSave!: (value: unknown) => void
        mocks.saveDraftMutateAsync.mockImplementationOnce(() => new Promise((resolve) => {
            resolveSave = resolve
        }))

        const pendingSave = composable.saveNow()
        const pendingSignal = mocks.saveDraftConfig?.()?.signal
        window.dispatchEvent(new StorageEvent('storage', {
            key: getDraftTombstoneKey(7, 91),
            newValue: JSON.stringify({ deletedAt: new Date().toISOString() }),
        }))

        expect(pendingSignal?.aborted).toBe(true)
        resolveSave({
            data: {
                data: {
                    draftId: 91,
                    clientDraftKey: 'client-draft-key-1234',
                    version: 1,
                    boardUrl: 'free',
                    title: 'Delayed server response',
                    contents: 'Draft body',
                    fileIds: [7],
                    updatedAt: '2025-01-02T00:00:00.000Z',
                },
            },
        })
        await pendingSave

        expect(composable.draftDeleted.value).toBe(true)
        expect(composable.draftId.value).toBeNull()
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            title: 'Draft title',
            hasLocalChanges: true,
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')
    })

    it('falls back to a replacement server draft after a stale local draft id returns 404', async () => {
        Storage.set('noviis:test:draft', {
            boardUrl: 'free',
            title: 'Local draft',
            contents: 'Local contents',
            draftId: 91,
            clientDraftKey: 'client-draft-key-1234',
            updatedAt: '2025-01-01T00:00:00.000Z',
            clientModifiedAt: '2026-07-07T11:30:00.000Z',
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
            response: { status: 404, data: { error: { code: 'P007' } } },
        })
        mocks.getMatchingDraft.mockResolvedValueOnce({
            data: {
                data: {
                    draftId: 13,
                    multipleMatchesFound: false,
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
        expect(mocks.getMatchingDraft).toHaveBeenCalledWith({
            boardUrl: 'free',
            originalPostId: 7,
        })
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
        mocks.getMatchingDraft.mockResolvedValueOnce({
            data: { data: { draftId: 91, multipleMatchesFound: false } },
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
        const setItem = vi.spyOn(Storage, 'setWithResult')
            .mockReturnValue({ ok: false, reason: 'unavailable' })
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
        expect(mocks.loggerError).toHaveBeenCalledWith(
            'Draft local snapshot storage failed.',
            { event: 'draft_local_snapshot_write_failed' },
        )
        expect(mocks.reportDraftOperationalEvent)
            .toHaveBeenCalledExactlyOnceWith('local_storage_write_failed')
        setItem.mockRestore()
    })

    it('reports evicted draft rollback failures without exposing draft data', async () => {
        const targetKey = 'noviis:test:draft'
        const rollbackFailureKey = 'noviis:draft:1:create:free:0'
        for (let index = 0; index < 5; index++) {
            Storage.set(`noviis:draft:1:create:free:${index}`, {
                boardUrl: 'free',
                title: `draft ${index}`,
                hasLocalChanges: false,
                clientModifiedAt: new Date(Date.UTC(2026, 6, 1, 0, index)).toISOString(),
            })
        }
        const originalSet = Storage.setWithResult.bind(Storage)
        const setItem = vi.spyOn(Storage, 'setWithResult').mockImplementation((key, value) => {
            if (key === targetKey) return { ok: false, reason: 'quota-exceeded' }
            if (key === rollbackFailureKey) return { ok: false, reason: 'unavailable' }
            return originalSet(key, value)
        })
        try {
            const { composable } = mountComposable(undefined, ref(targetKey))

            expect(composable.writeLocalSnapshot()).toBe(false)
            expect(mocks.loggerError).toHaveBeenCalledWith(
                'Draft local snapshot rollback failed.',
                { event: 'draft_local_snapshot_rollback_failed', failedCount: 1 },
            )
            expect(mocks.reportDraftOperationalEvent).toHaveBeenCalledWith(
                'local_storage_rollback_failed',
                { failedCount: 1 },
            )
        } finally {
            setItem.mockRestore()
        }
    })

    it('retries a failed server recovery when connectivity returns', async () => {
        mocks.getMatchingDraft.mockRejectedValueOnce(new Error('offline'))
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

        expect(mocks.getMatchingDraft).toHaveBeenCalledTimes(2)
        expect(composable.restoreFailed.value).toBe(false)
    })

    it('adopts a canonical server save from another tab when this tab has no unsaved edits', async () => {
        const { composable, appliedDrafts } = mountComposable()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                clientDraftKey: composable.clientDraftKey.value,
                version: 3,
                boardUrl: 'free',
                title: 'Other tab edit',
                contents: 'Other tab body',
                fileIds: [],
                updatedAt: '2026-07-07T13:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(appliedDrafts[0]).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Other tab edit',
        }))
        expect(composable.draftId.value).toBe(91)
        expect(composable.draftVersion.value).toBe(3)
        expect(composable.updatedAt.value).toBe('2026-07-07T13:00:00.000Z')
        expect(composable.draftConflict.value).toBe(false)
    })

    it('adopts the first server id through the update channel after the storage key changes', async () => {
        const channels: FakeBroadcastChannel[] = []
        class FakeBroadcastChannel {
            listeners: Array<(event: MessageEvent) => void> = []
            constructor(readonly name: string) { channels.push(this) }
            addEventListener(_type: string, listener: (event: MessageEvent) => void) {
                this.listeners.push(listener)
            }
            postMessage(data: unknown) {
                channels
                    .filter((channel) => channel !== this && channel.name === this.name)
                    .forEach((channel) => channel.listeners.forEach((listener) => listener({ data } as MessageEvent)))
            }
            close() {}
        }
        vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
        const { composable, appliedDrafts } = mountComposable(
            undefined,
            ref('noviis:test:draft'),
            ref(true),
            ref(7),
            undefined,
            (draftId) => `noviis:test:draft:${draftId}`,
        )
        const peer = new FakeBroadcastChannel('noviis-draft-updated')

        peer.postMessage({
            type: 'draft-updated',
            eventId: 'updated-event-1',
            sourceId: 'other-tab',
            ownerId: '7',
            storageKey: 'noviis:test:draft:91',
            at: Date.now(),
            snapshot: {
                draftId: 91,
                clientDraftKey: composable.clientDraftKey.value,
                version: 1,
                boardUrl: 'free',
                title: 'Draft title',
                contents: 'Draft body',
                fileIds: [7],
                updatedAt: '2026-07-07T13:00:00.000Z',
                clientModifiedAt: '2026-07-07T13:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            },
        })

        expect(composable.draftId.value).toBe(91)
        expect(composable.draftVersion.value).toBe(1)
        expect(appliedDrafts.at(-1)).toEqual(expect.objectContaining({ draftId: 91 }))
        expect(composable.draftConflict.value).toBe(false)
    })

    it('ignores an update channel event for a different logical draft', async () => {
        const { composable, appliedDrafts } = mountComposable()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 92,
                clientDraftKey: 'different-draft-key',
                version: 1,
                boardUrl: 'free',
                title: 'Different draft',
                contents: 'Different body',
                updatedAt: '2026-07-07T13:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(composable.draftId.value).toBeNull()
        expect(appliedDrafts).toHaveLength(0)
        expect(composable.draftConflict.value).toBe(false)
    })

    it('adopts compatible unsaved edits from another tab before this tab diverges', async () => {
        const { composable, appliedDrafts } = mountComposable()
        await composable.saveNow()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                clientDraftKey: 'client-draft-key-1234',
                version: 0,
                boardUrl: 'free',
                title: 'Unsaved edit from another tab',
                contents: 'Draft body',
                updatedAt: '2025-01-01T00:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: true,
            }),
        }))

        expect(appliedDrafts.at(-1)).toEqual(expect.objectContaining({
            title: 'Unsaved edit from another tab',
        }))
        expect(composable.draftConflict.value).toBe(false)

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                clientDraftKey: 'client-draft-key-1234',
                version: 1,
                boardUrl: 'free',
                title: 'Server advanced elsewhere',
                contents: 'Draft body',
                updatedAt: '2025-01-02T00:00:00.000Z',
                clientInstanceId: 'third-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(composable.draftConflict.value).toBe(true)
    })

    it('reconciles an adopted tab edit when the same content finishes saving on the server', async () => {
        const { composable, appliedDrafts, payloadRef } = mountComposable()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                clientDraftKey: composable.clientDraftKey.value,
                boardUrl: 'free',
                title: 'Unsaved edit from another tab',
                contents: 'Draft body',
                updatedAt: '2025-01-01T00:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: true,
            }),
        }))
        payloadRef.value = {
            ...payloadRef.value,
            title: 'Unsaved edit from another tab',
        }

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 91,
                clientDraftKey: composable.clientDraftKey.value,
                version: 1,
                boardUrl: 'free',
                title: 'Unsaved edit from another tab',
                contents: 'Draft body',
                fileIds: [7],
                updatedAt: '2025-01-02T00:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(appliedDrafts.at(-1)).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Unsaved edit from another tab',
            version: 1,
        }))
        expect(composable.draftId.value).toBe(91)
        expect(composable.draftVersion.value).toBe(1)
        expect(composable.updatedAt.value).toBe('2025-01-02T00:00:00.000Z')
        expect(composable.draftConflict.value).toBe(false)
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

    it('does not replace an already tracked draft with a different draft from another tab', async () => {
        const { composable, appliedDrafts } = mountComposable()
        await composable.saveNow()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            newValue: JSON.stringify({
                draftId: 92,
                clientDraftKey: 'different-draft-key',
                version: 1,
                boardUrl: 'free',
                title: 'Different draft',
                contents: 'Different body',
                updatedAt: '2026-07-07T13:00:00.000Z',
                clientInstanceId: 'other-tab',
                hasLocalChanges: false,
            }),
        }))

        expect(composable.draftId.value).toBe(91)
        expect(appliedDrafts).toHaveLength(0)
        expect(composable.draftConflict.value).toBe(false)
    })

    it('does not treat local snapshot eviction as server draft deletion', async () => {
        const { composable } = mountComposable(undefined, ref('noviis:test:draft'), ref(true), ref(7))
        await composable.saveNow()
        const snapshot = Storage.get<DraftRecoverySnapshot>('noviis:test:draft')

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:test:draft',
            oldValue: JSON.stringify({ ...snapshot, clientInstanceId: 'other-tab' }),
            newValue: null,
        }))

        expect(composable.draftDeleted.value).toBe(false)
        expect(composable.draftId.value).toBe(91)
    })

    it('broadcasts a tombstone when a published draft recovery is cleared', async () => {
        const { composable } = mountComposable(undefined, ref('noviis:test:draft'), ref(true), ref(7))
        await composable.saveNow()

        composable.clearPublishedDraftRecovery()

        expect(Storage.get('noviis:draft-deleted:7:91')).toEqual({
            deletedAt: '2026-07-07T12:00:00.000Z',
        })
        expect(Storage.get('noviis:test:draft')).toBeNull()
        expect(composable.draftId.value).toBeNull()
    })

    it('preserves local content and can save it as new when the server draft disappeared', async () => {
        const { composable, payloadRef } = mountComposable()
        await composable.saveNow()
        const previousClientKey = composable.clientDraftKey.value
        payloadRef.value = { ...payloadRef.value, title: 'Preserved after deletion' }
        composable.writeLocalSnapshot()
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404, data: { error: { code: 'P007' } } },
        })

        await expect(composable.saveNow()).rejects.toMatchObject({ response: { status: 404 } })

        expect(composable.draftDeleted.value).toBe(true)
        expect(composable.draftId.value).toBeNull()
        expect(composable.lastSaveFailed.value).toBe(false)
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            title: 'Preserved after deletion',
            categoryId: null,
            fileIds: [],
            seriesId: null,
            staleReferencesReset: true,
            hasLocalChanges: true,
        }))
        expect(Storage.get('noviis:test:draft')).not.toHaveProperty('draftId')

        await expect(composable.saveDeletedDraftAsNew()).resolves.toBe(true)

        expect(mocks.saveDraftMutateAsync).toHaveBeenLastCalledWith(expect.objectContaining({
            draftId: undefined,
            title: 'Preserved after deletion',
            clientDraftKey: expect.not.stringMatching(new RegExp(`^${previousClientKey}$`)),
        }))
        expect(composable.draftDeleted.value).toBe(false)
    })

    it('does not report the draft as deleted for a related-resource 404', async () => {
        const { composable, payloadRef } = mountComposable()
        await composable.saveNow()
        payloadRef.value = { ...payloadRef.value, title: 'Still recoverable' }
        composable.writeLocalSnapshot()
        mocks.saveDraftMutateAsync.mockRejectedValueOnce({
            isAxiosError: true,
            response: { status: 404, data: { error: { code: 'B001' } } },
        })

        await expect(composable.saveNow()).rejects.toMatchObject({
            response: { status: 404, data: { error: { code: 'B001' } } },
        })

        expect(composable.draftDeleted.value).toBe(false)
        expect(composable.draftId.value).toBe(91)
        expect(composable.lastSaveFailed.value).toBe(true)
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            draftId: 91,
            title: 'Still recoverable',
        }))
    })

    it('enters deleted state when another tab records a tombstone for the active draft', async () => {
        const { composable } = mountComposable(
            undefined,
            ref('noviis:test:draft'),
            ref(true),
            ref(7),
        )
        await composable.saveNow()

        window.dispatchEvent(new StorageEvent('storage', {
            key: 'noviis:draft-deleted:7:91',
            newValue: JSON.stringify({ deletedAt: '2026-07-07T13:00:00.000Z' }),
        }))

        expect(composable.draftDeleted.value).toBe(true)
        expect(composable.draftId.value).toBeNull()
        expect(Storage.get('noviis:test:draft')).toEqual(expect.objectContaining({
            title: 'Draft title',
            hasLocalChanges: true,
        }))
    })
})
