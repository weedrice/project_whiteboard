import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftPost } from '@/types'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import { createDraftRecoveryCoordinator } from '@/features/board/posts/draft/postDraftRecoveryCoordinator'
import { loadDraftById } from '@/features/board/posts/draft/postDraftRecovery'
import { resolveServerDraftForRecovery } from '@/features/board/posts/draft/postDraftRestore'
import { Storage } from '@/utils/storage'

vi.mock('@/features/board/posts/draft/postDraftRecovery', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/features/board/posts/draft/postDraftRecovery')>()
  return { ...actual, loadDraftById: vi.fn() }
})

vi.mock('@/features/board/posts/draft/postDraftRestore', () => ({
  resolveServerDraftForRecovery: vi.fn(),
}))

vi.mock('@/utils/clientErrorReporter', () => ({
  reportDraftOperationalEvent: vi.fn().mockResolvedValue(undefined),
}))

const payload = (): PostDraftData => ({
  boardUrl: 'general',
  title: 'Current title',
  contents: '<p>Current body</p>',
  tags: [],
  fileIds: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
})

const serverDraft = (overrides: Partial<DraftPost> = {}): DraftPost => ({
  draftId: 91,
  clientDraftKey: 'server-key',
  version: 2,
  boardId: 1,
  boardUrl: 'general',
  boardName: 'General',
  originalPostId: null,
  title: 'Server title',
  contents: '<p>Server body</p>',
  tags: [],
  fileIds: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  updatedAt: '2026-08-05T12:00:00.000Z',
  modifiedAt: '2026-08-05T12:00:00.000Z',
  ...overrides,
} as DraftPost)

function createHarness() {
  const enabled = ref(true)
  const ownerId = ref<string | number | null | undefined>(7)
  const preferredDraftId = ref<number | null>(91)
  const draftId = ref<number | null>(91)
  const draftVersion = ref<number | null>(1)
  const clientDraftKey = ref('client-key')
  const updatedAt = ref<string | null>('2026-08-05T11:00:00.000Z')
  const lastSavedAt = ref<string | null>(null)
  const lastSaveScope = ref<'server' | 'browser' | null>(null)
  const lastSaveFailed = ref(false)
  const restoreFailed = ref(false)
  const multipleDraftsFound = ref(false)
  const isRestoringDraft = ref(false)
  const draftConflict = ref(false)
  const draftProtected = ref(false)
  const draftDeleted = ref(false)
  const staleReferencesReset = ref(false)
  const contractValidationFailed = ref(false)
  const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
  const hasRestoredDraft = ref(false)
  let sessionGeneration = 0
  let localRevision = 0
  let persistedRevision = 0
  let activeRecoveryController: AbortController | null = null
  const applyDraft = vi.fn()
  const onSaved = vi.fn()
  const onStaleReferencesReset = vi.fn()
  const onLocalSnapshotAvailable = vi.fn()
  const removeLocalSnapshot = vi.fn(() => true)
  const storeLocalSnapshot = vi.fn(() => true)
  const resetDraftTracking = vi.fn()
  const transitionToProtectedDraft = vi.fn()
  const scheduleAutosave = vi.fn()
  const saveNow = vi.fn().mockResolvedValue(serverDraft())

  const coordinator = createDraftRecoveryCoordinator({
    enabled,
    ownerId,
    preferredDraftId,
    draftId,
    draftVersion,
    clientDraftKey,
    updatedAt,
    lastSavedAt,
    lastSaveScope,
    lastSaveFailed,
    restoreFailed,
    multipleDraftsFound,
    isRestoringDraft,
    draftConflict,
    draftProtected,
    draftDeleted,
    staleReferencesReset,
    contractValidationFailed,
    restoreSource,
    hasRestoredDraft,
    getSessionGeneration: () => sessionGeneration,
    getLocalRevision: () => localRevision,
    incrementLocalRevision: () => { localRevision++ },
    markCurrentRevisionPersisted: () => { persistedRevision = localRevision },
    startRecoveryRequest: () => {
      activeRecoveryController?.abort()
      activeRecoveryController = new AbortController()
      return activeRecoveryController
    },
    finishRecoveryRequest: (controller) => {
      if (activeRecoveryController !== controller) return false
      activeRecoveryController = null
      return true
    },
    isRecoveryRequestCurrent: (controller) => activeRecoveryController === controller,
    buildPayload: payload,
    applyDraft,
    onSaved,
    onStaleReferencesReset,
    onLocalSnapshotAvailable,
    loadLocalSnapshot: () => null,
    removeLocalSnapshot,
    storeLocalSnapshot,
    resetDraftTracking,
    transitionToProtectedDraft,
    scheduleAutosave,
    saveNow,
  })

  return {
    coordinator,
    draftId,
    draftVersion,
    clientDraftKey,
    updatedAt,
    lastSavedAt,
    lastSaveScope,
    isRestoringDraft,
    draftConflict,
    draftProtected,
    draftDeleted,
    restoreFailed,
    restoreSource,
    hasRestoredDraft,
    applyDraft,
    onSaved,
    onStaleReferencesReset,
    onLocalSnapshotAvailable,
    storeLocalSnapshot,
    transitionToProtectedDraft,
    scheduleAutosave,
    saveNow,
    incrementLocalRevision: () => { localRevision++ },
    getLocalRevision: () => localRevision,
    getPersistedRevision: () => persistedRevision,
    incrementGeneration: () => { sessionGeneration++ },
  }
}

describe('draft recovery coordinator', () => {
  beforeEach(() => {
    Storage.clear()
    vi.clearAllMocks()
  })

  it('reloads the canonical server draft and schedules a save when preparation changes it', async () => {
    const harness = createHarness()
    vi.mocked(loadDraftById).mockResolvedValue(serverDraft())
    const changedSnapshot = {
      ...serverDraft(),
      title: 'Prepared title',
      staleReferencesReset: true,
    } as DraftRecoverySnapshot

    const coordinator = createDraftRecoveryCoordinator({
      enabled: ref(true),
      draftId: harness.draftId,
      draftVersion: harness.draftVersion,
      clientDraftKey: harness.clientDraftKey,
      updatedAt: harness.updatedAt,
      lastSavedAt: harness.lastSavedAt,
      lastSaveScope: harness.lastSaveScope,
      lastSaveFailed: ref(false),
      restoreFailed: harness.restoreFailed,
      multipleDraftsFound: ref(false),
      isRestoringDraft: harness.isRestoringDraft,
      draftConflict: harness.draftConflict,
      draftProtected: harness.draftProtected,
      draftDeleted: harness.draftDeleted,
      staleReferencesReset: ref(false),
      contractValidationFailed: ref(false),
      restoreSource: harness.restoreSource,
      hasRestoredDraft: harness.hasRestoredDraft,
      getSessionGeneration: () => 0,
      getLocalRevision: harness.getLocalRevision,
      incrementLocalRevision: harness.incrementLocalRevision,
      markCurrentRevisionPersisted: vi.fn(),
      startRecoveryRequest: () => new AbortController(),
      finishRecoveryRequest: () => true,
      isRecoveryRequestCurrent: () => true,
      buildPayload: payload,
      applyDraft: harness.applyDraft,
      prepareRecoveredSnapshot: () => changedSnapshot,
      onSaved: harness.onSaved,
      onStaleReferencesReset: harness.onStaleReferencesReset,
      loadLocalSnapshot: () => null,
      removeLocalSnapshot: () => true,
      storeLocalSnapshot: harness.storeLocalSnapshot,
      resetDraftTracking: vi.fn(),
      transitionToProtectedDraft: harness.transitionToProtectedDraft,
      scheduleAutosave: harness.scheduleAutosave,
      saveNow: harness.saveNow,
    })

    expect(await coordinator.reloadServerDraft()).toBe(true)
    expect(harness.applyDraft).toHaveBeenCalledWith(changedSnapshot)
    expect(harness.scheduleAutosave).toHaveBeenCalledTimes(1)
    expect(harness.onStaleReferencesReset).toHaveBeenCalledTimes(1)
  })

  it('refreshes server identity before keeping the local draft', async () => {
    const harness = createHarness()
    vi.mocked(loadDraftById).mockResolvedValue(serverDraft({ version: 4, clientDraftKey: 'latest-key' }))

    expect(await harness.coordinator.keepLocalDraft()).toBe(true)
    expect(harness.draftVersion.value).toBe(4)
    expect(harness.clientDraftKey.value).toBe('latest-key')
    expect(harness.saveNow).toHaveBeenCalledTimes(1)
  })

  it('routes protected recovery into the protected-draft transition', async () => {
    const harness = createHarness()
    vi.mocked(resolveServerDraftForRecovery).mockResolvedValue({
      localSnapshot: null,
      serverDraft: null,
      recoveryFailed: false,
      draftProtected: true,
      multipleMatchesFound: false,
    })

    await harness.coordinator.restoreDraft()

    expect(harness.transitionToProtectedDraft).toHaveBeenCalledTimes(1)
    expect(harness.applyDraft).not.toHaveBeenCalled()
  })

  it('preserves an edit made while recovery is in flight and reports a conflict', async () => {
    const harness = createHarness()
    let resolveRecovery!: (value: Awaited<ReturnType<typeof resolveServerDraftForRecovery>>) => void
    vi.mocked(resolveServerDraftForRecovery).mockReturnValue(new Promise((resolve) => {
      resolveRecovery = resolve
    }))

    const restoring = harness.coordinator.restoreDraft()
    harness.incrementLocalRevision()
    resolveRecovery({
      localSnapshot: null,
      serverDraft: serverDraft(),
      recoveryFailed: false,
      draftProtected: false,
      multipleMatchesFound: false,
    })
    await restoring

    expect(harness.draftConflict.value).toBe(true)
    expect(harness.restoreSource.value).toBe('local')
    expect(harness.storeLocalSnapshot).toHaveBeenCalledTimes(1)
    expect(harness.applyDraft).not.toHaveBeenCalled()
  })
})
