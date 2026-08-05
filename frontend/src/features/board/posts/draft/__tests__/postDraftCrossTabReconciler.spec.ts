import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import type { PostDraftData } from '@/api/post'
import type { DraftRecoverySnapshot } from '@/features/board/posts/draft/postDraftRecovery'
import { createDraftCrossTabReconciler } from '@/features/board/posts/draft/postDraftCrossTabReconciler'

const payload = (overrides: Partial<PostDraftData> = {}): PostDraftData => ({
  boardUrl: 'general',
  title: 'Current title',
  contents: '<p>Current body</p>',
  tags: [],
  fileIds: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  ...overrides,
})

const incoming = (overrides: Partial<DraftRecoverySnapshot> = {}): DraftRecoverySnapshot => ({
  boardUrl: 'general',
  title: 'Incoming title',
  contents: '<p>Incoming body</p>',
  tags: [],
  fileIds: [],
  isNotice: false,
  isNsfw: false,
  isSpoiler: false,
  isSecret: false,
  draftId: 91,
  clientDraftKey: 'client-key',
  clientInstanceId: 'tab-b',
  version: 2,
  updatedAt: '2026-08-05T12:00:02.000Z',
  clientModifiedAt: '2026-08-05T12:00:03.000Z',
  ...overrides,
})

function createHarness(options: {
  currentPayload?: PostDraftData
  localRevision?: number
  persistedRevision?: number
  lastRemoteLocalChangeAt?: number
} = {}) {
  const draftId = ref<number | null>(91)
  const draftVersion = ref<number | null>(1)
  const clientDraftKey = ref('client-key')
  const updatedAt = ref<string | null>('2026-08-05T12:00:01.000Z')
  const lastSavedAt = ref<string | null>(null)
  const lastSaveScope = ref<'server' | 'browser' | null>(null)
  const draftConflict = ref(false)
  const restoreSource = ref<'idle' | 'local' | 'server'>('idle')
  let localRevision = options.localRevision ?? 0
  let persistedRevision = options.persistedRevision ?? 0
  let lastRemoteLocalChangeAt = options.lastRemoteLocalChangeAt ?? 0
  const applyDraft = vi.fn()
  const onSaved = vi.fn()
  const clearAutosaveTimer = vi.fn()
  const currentPayload = options.currentPayload ?? payload()

  const reconciler = createDraftCrossTabReconciler({
    clientInstanceId: 'tab-a',
    draftId,
    draftVersion,
    clientDraftKey,
    updatedAt,
    lastSavedAt,
    lastSaveScope,
    draftConflict,
    restoreSource,
    buildPayload: () => currentPayload,
    applyDraft,
    onSaved,
    clearAutosaveTimer,
    getLocalRevision: () => localRevision,
    getPersistedRevision: () => persistedRevision,
    incrementLocalRevision: () => { localRevision++ },
    markCurrentRevisionPersisted: () => { persistedRevision = localRevision },
    getLastRemoteLocalChangeAt: () => lastRemoteLocalChangeAt,
    setLastRemoteLocalChangeAt: (value) => { lastRemoteLocalChangeAt = value },
  })

  return {
    reconciler,
    draftId,
    draftVersion,
    clientDraftKey,
    updatedAt,
    lastSavedAt,
    lastSaveScope,
    draftConflict,
    restoreSource,
    applyDraft,
    onSaved,
    clearAutosaveTimer,
    getLocalRevision: () => localRevision,
    getPersistedRevision: () => persistedRevision,
    getLastRemoteLocalChangeAt: () => lastRemoteLocalChangeAt,
  }
}

describe('draft cross-tab reconciler', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('ignores snapshots from the current tab and older canonical server revisions', () => {
    const self = createHarness()
    expect(self.reconciler.reconcile(incoming({ clientInstanceId: 'tab-a' }))).toBe('ignored')
    expect(self.applyDraft).not.toHaveBeenCalled()

    const stale = createHarness()
    stale.draftVersion.value = 3
    expect(stale.reconciler.reconcile(incoming({ hasLocalChanges: false, version: 2 }))).toBe('ignored')
    expect(stale.applyDraft).not.toHaveBeenCalled()
  })

  it('does not apply another tab local edit even when the current tab is clean', () => {
    const harness = createHarness()

    expect(harness.reconciler.reconcile(incoming({ hasLocalChanges: true }))).toBe('conflict')
    expect(harness.draftConflict.value).toBe(true)
    expect(harness.clearAutosaveTimer).toHaveBeenCalledTimes(1)
    expect(harness.applyDraft).not.toHaveBeenCalled()
    expect(harness.getLocalRevision()).toBe(0)
    expect(harness.restoreSource.value).toBe('idle')
    expect(harness.getLastRemoteLocalChangeAt()).toBe(Date.parse('2026-08-05T12:00:03.000Z'))
  })

  it('acknowledges a canonical save that exactly matches the current content', () => {
    const current = payload({ title: 'Same title', contents: '<p>Same body</p>' })
    const harness = createHarness({ currentPayload: current, localRevision: 2, persistedRevision: 1 })
    const snapshot = incoming({
      title: current.title,
      contents: current.contents,
      hasLocalChanges: false,
    })

    expect(harness.reconciler.reconcile(snapshot)).toBe('server-acknowledged')
    expect(harness.getPersistedRevision()).toBe(2)
    expect(harness.draftVersion.value).toBe(2)
    expect(harness.lastSaveScope.value).toBe('server')
    expect(harness.lastSavedAt.value).toBe(snapshot.updatedAt)
    expect(harness.onSaved).toHaveBeenCalledTimes(1)
    expect(harness.applyDraft).not.toHaveBeenCalled()
  })

  it('does not apply a different canonical server snapshot when the current tab is clean', () => {
    const harness = createHarness()

    expect(harness.reconciler.reconcile(incoming({ hasLocalChanges: false }))).toBe('conflict')
    expect(harness.draftConflict.value).toBe(true)
    expect(harness.clearAutosaveTimer).toHaveBeenCalledTimes(1)
    expect(harness.applyDraft).not.toHaveBeenCalled()
    expect(harness.lastSaveScope.value).toBeNull()
  })

  it('preserves an unsaved local edit by entering conflict when another tab advances', () => {
    const harness = createHarness({ localRevision: 2, persistedRevision: 1 })

    expect(harness.reconciler.reconcile(incoming({ hasLocalChanges: false, version: 2 }))).toBe('conflict')
    expect(harness.draftConflict.value).toBe(true)
    expect(harness.clearAutosaveTimer).toHaveBeenCalledTimes(1)
    expect(harness.applyDraft).not.toHaveBeenCalled()
  })

  it('ignores an out-of-order local snapshot already superseded by another tab event', () => {
    const harness = createHarness({
      lastRemoteLocalChangeAt: Date.parse('2026-08-05T12:00:04.000Z'),
    })

    expect(harness.reconciler.reconcile(incoming({
      hasLocalChanges: true,
      clientModifiedAt: '2026-08-05T12:00:03.000Z',
    }))).toBe('ignored')
    expect(harness.applyDraft).not.toHaveBeenCalled()
    expect(harness.draftConflict.value).toBe(false)
  })
})
