import { computed, nextTick, ref, watch, type ComputedRef, type Ref } from 'vue'
import { usePostDraft } from '@/features/board/posts/draft/usePostDraft'
import type { PostComposerSnapshot } from '@/features/board/posts/form/usePostComposerState'
import type { PostFormFileIdScope } from '@/utils/postForm'
import logger from '@/utils/logger'
import { formatTimeOnly } from '@/utils/date'
import { migrateStoredDraftSnapshot } from '@/features/board/posts/draft/postDraftLifecycle'
import { useEventListener } from '@/composables/useEventListener'

type ComposerToastType = 'info' | 'success' | 'warning' | 'error'

type UsePostComposerDraftOptions = {
  isAuthenticated: Ref<boolean>
  userId: Ref<string | number | undefined>
  sessionGeneration: Ref<number>
  identity: Ref<string>
  mode: () => 'create' | 'edit'
  boardUrl: Ref<string>
  postId: Ref<string | number>
  preferredDraftId?: Ref<number | null>
  isLoading: Ref<boolean>
  selectedCategoryId: Ref<string | number>
  firstCategoryId: ComputedRef<number | undefined>
  buildPayload: (fileIdScope?: PostFormFileIdScope) => ReturnType<typeof import('@/utils/postForm').buildPostFormPayload>
  applyDraft: (draft: PostComposerSnapshot) => void
  markCurrentSnapshotSaved: () => void
  releaseUploadedFileOwnership: (fileIds: number[]) => void
  t: (key: string, values?: Record<string, unknown>) => string
  addToast: (message: string, type: ComposerToastType) => void
  validateBeforeSave?: () => boolean
}

export function usePostComposerDraft(options: UsePostComposerDraftOptions) {
  const draftEnabled = computed(() => (
    options.isAuthenticated.value
    && options.userId.value != null
    && !!options.boardUrl.value
  ))
  const legacyDraftStorageKey = computed(() =>
    `noviis:draft:${options.userId.value ?? 'guest'}:${options.mode()}:${options.boardUrl.value || 'unknown'}:${options.postId.value || 'new'}`,
  )
  const draftStorageKey = computed(() => {
    const preferredDraftId = options.preferredDraftId?.value
    return preferredDraftId == null
      ? legacyDraftStorageKey.value
      : `${legacyDraftStorageKey.value}:draft-${preferredDraftId}`
  })
  const hasRestoredDraft = ref(false)
  const initializedBaselineIdentity = ref<string | null>(null)
  const draftIdentity = computed(() => [
    options.sessionGeneration.value,
    options.userId.value ?? 'hydrating',
    options.identity.value,
    options.preferredDraftId?.value ?? 'default',
  ].join(':'))
  let appliedDraftSignature: string | null = null
  let lastStoredDraftSignature: string | null = null

  const serializeDraftPayload = () => JSON.stringify({
    ...options.buildPayload('draft'),
    boardUrl: options.boardUrl.value,
    originalPostId: options.mode() === 'edit' ? Number(options.postId.value) : undefined,
  })

  const applyDraftWithoutTracking = (draft: PostComposerSnapshot) => {
    options.applyDraft(draft)
    appliedDraftSignature = serializeDraftPayload()
  }

  const {
    saveNow: saveDraftNow,
    retrySaveNow,
    scheduleAutosave,
    restoreDraft,
    clearPublishedDraftRecovery,
    clearScheduledDraftRecovery,
    writeLocalSnapshot,
    lastSavedAt,
    lastSaveScope,
    lastSaveFailed,
    saveRetryAttempt,
    saveRetryScheduled,
    saveRetryExhausted,
    saveRetryMaxAttempts,
    lastLocalSaveFailed,
    draftConflict,
    draftProtected,
    draftDeleted,
    restoreFailed,
    multipleDraftsFound,
    isRestoringDraft,
    reloadServerDraft,
    keepLocalDraft,
    retryRestore,
    isSavingDraft,
    restoreSource,
    draftId,
    resetSession,
    saveDeletedDraftAsNew,
    discardDeletedDraft,
  } = usePostDraft({
    enabled: draftEnabled,
    storageKey: draftStorageKey,
    ownerId: options.userId,
    preferredDraftId: options.preferredDraftId,
    buildPayload: () => ({
      ...options.buildPayload('draft'),
      boardUrl: options.boardUrl.value,
      originalPostId: options.mode() === 'edit' ? Number(options.postId.value) : undefined,
    }),
    applyDraft: applyDraftWithoutTracking,
    onSaved: options.markCurrentSnapshotSaved,
    onServerSaved: (payload) => options.releaseUploadedFileOwnership(payload.fileIds ?? []),
    prepareStaleSnapshot: (snapshot) => ({
      ...snapshot,
      categoryId: options.firstCategoryId.value ?? null,
    }),
    onStaleReferencesReset: () => options.addToast(
      options.t('board.writePost.draftStatus.referencesReset'),
      'warning',
    ),
    canPersist: options.validateBeforeSave,
  })

  const draftStatusLabel = computed(() => {
    if (!draftEnabled.value) return ''
    if (isRestoringDraft.value) return options.t('board.writePost.draftStatus.restoring')
    if (isSavingDraft.value) return options.t('board.writePost.draftStatus.saving')
    if (draftDeleted.value) return options.t('board.writePost.draftStatus.deleted')
    if (draftConflict.value) return options.t('board.writePost.draftStatus.conflict')
    if (draftProtected.value) return options.t('board.writePost.draftStatus.protected')
    if (multipleDraftsFound.value) return options.t('board.writePost.draftStatus.multipleFound')
    if (restoreFailed.value) return options.t('board.writePost.draftStatus.restoreFailed')
    if (lastLocalSaveFailed.value) return options.t('board.writePost.draftStatus.localStorageFailed')
    if (saveRetryScheduled.value) {
      return options.t('board.writePost.draftStatus.retryScheduled', {
        attempt: saveRetryAttempt.value,
        max: saveRetryMaxAttempts,
      })
    }
    if (saveRetryExhausted.value) return options.t('board.writePost.draftStatus.retryExhausted')
    if (lastSaveFailed.value) return options.t('board.writePost.draftStatus.failed')
    if (lastSavedAt.value) {
      if (lastSaveScope.value === 'browser') {
        return options.t('board.writePost.draftStatus.savedBrowserAt', {
          time: formatTimeOnly(lastSavedAt.value),
        })
      }
      return options.t('board.writePost.draftStatus.savedAt', {
        time: formatTimeOnly(lastSavedAt.value),
      })
    }
    return options.t('board.writePost.draftStatus.ready')
  })

  watch(
    draftIdentity,
    (_current, previous) => {
      if (previous === undefined) return
      hasRestoredDraft.value = false
      initializedBaselineIdentity.value = null
      lastStoredDraftSignature = null
      resetSession()
    },
  )

  watch(
    () => [options.isLoading.value, draftIdentity.value, draftEnabled.value] as const,
    async ([loading, identity, enabled]) => {
      if (loading) return
      if (!enabled) {
        if (initializedBaselineIdentity.value !== identity) {
          options.markCurrentSnapshotSaved()
          initializedBaselineIdentity.value = identity
        }
        return
      }
      if (hasRestoredDraft.value) return
      const restoringIdentity = identity
      migrateStoredDraftSnapshot(
        legacyDraftStorageKey.value,
        draftStorageKey.value,
        options.preferredDraftId?.value,
      )

      if (options.mode() === 'create' && !options.selectedCategoryId.value && options.firstCategoryId.value != null) {
        options.selectedCategoryId.value = options.firstCategoryId.value
        // 기본 카테고리 초기화는 사용자 편집이 아니다. 해당 변경 감시를 먼저 소진해야
        // 복구 중 실제 입력만 로컬 수정으로 기록되고 멀티탭 자동 동기화를 막지 않는다.
        await nextTick()
      }

      hasRestoredDraft.value = true
      await restoreDraft()
      if (restoringIdentity !== draftIdentity.value) return
      const restoredDraftSource = restoreSource.value
      if (restoredDraftSource !== 'idle') {
        options.addToast(
          restoredDraftSource === 'local'
            ? options.t('board.writePost.draftStatus.restoredLocal')
            : options.t('board.writePost.draftStatus.restoredServer'),
          'info',
        )
      }
      options.markCurrentSnapshotSaved()
      initializedBaselineIdentity.value = restoringIdentity
      lastStoredDraftSignature = serializeDraftPayload()
    },
    { immediate: true },
  )

  const draftSignature = computed(serializeDraftPayload)

  watch(
    draftSignature,
    (signature) => {
      if (!hasRestoredDraft.value || !draftEnabled.value || options.isLoading.value) return
      if (appliedDraftSignature === signature) {
        appliedDraftSignature = null
        lastStoredDraftSignature = signature
        return
      }
      appliedDraftSignature = null
      writeLocalSnapshot()
      lastStoredDraftSignature = signature
      scheduleAutosave()
    },
    { flush: 'post' },
  )

  const flushLatestLocalSnapshot = () => {
    if (!hasRestoredDraft.value || !draftEnabled.value || options.isLoading.value) return
    const signature = serializeDraftPayload()
    if (signature === lastStoredDraftSignature) return
    writeLocalSnapshot()
    lastStoredDraftSignature = signature
  }

  useEventListener(() => window, 'pagehide', flushLatestLocalSnapshot)
  useEventListener(() => document, 'visibilitychange', () => {
    if (document.visibilityState === 'hidden') flushLatestLocalSnapshot()
  })

  async function handleSaveDraft() {
    if (options.validateBeforeSave?.() === false) {
      options.addToast(options.t('board.writePost.validation'), 'error')
      return
    }
    try {
      const savedDraft = await retrySaveNow()
      if (savedDraft) {
        options.markCurrentSnapshotSaved()
        options.addToast(options.t('board.writePost.draftStatus.saved'), 'success')
      } else if (lastSaveScope.value === 'browser') {
        options.addToast(options.t('board.writePost.draftStatus.savedBrowser'), 'success')
      }
    } catch (error) {
      logger.error('Failed to save draft:', error)
      options.addToast(options.t('common.messages.saveFailed'), 'error')
    }
  }

  async function handleReloadServerDraft() {
    try {
      if (await reloadServerDraft()) {
        options.addToast(options.t('board.writePost.draftStatus.restoredServer'), 'info')
      }
    } catch (error) {
      logger.error('Failed to reload server draft:', error)
      options.addToast(options.t('common.error.unknown'), 'error')
    }
  }

  async function handleKeepLocalDraft() {
    try {
      if (await keepLocalDraft()) {
        options.markCurrentSnapshotSaved()
        options.addToast(options.t('board.writePost.draftStatus.saved'), 'success')
      }
    } catch (error) {
      logger.error('Failed to keep local draft:', error)
      options.addToast(options.t('common.messages.saveFailed'), 'error')
    }
  }

  async function handleRetryDraftRestore() {
    try {
      await retryRestore()
    } catch (error) {
      logger.error('Failed to retry draft recovery:', error)
      options.addToast(options.t('common.error.unknown'), 'error')
    }
  }

  async function handleSaveDeletedDraftAsNew() {
    try {
      if (await saveDeletedDraftAsNew()) {
        options.markCurrentSnapshotSaved()
        options.addToast(options.t('board.writePost.draftStatus.savedAsNew'), 'success')
      }
    } catch (error) {
      logger.error('Failed to save deleted draft as new:', error)
      options.addToast(options.t('common.messages.saveFailed'), 'error')
    }
  }

  function handleDiscardDeletedDraft() {
    discardDeletedDraft()
    applyDraftWithoutTracking({
      title: '',
      contents: '',
      categoryId: options.firstCategoryId.value,
      tags: [],
      fileIds: [],
      isNotice: false,
      isNsfw: false,
      isSpoiler: false,
      isSecret: false,
      poll: null,
      seriesId: null,
    })
    options.markCurrentSnapshotSaved()
    options.addToast(options.t('board.writePost.draftStatus.discarded'), 'info')
  }

  return {
    draftEnabled,
    draftStatusLabel,
    draftId,
    draftConflict,
    draftProtected,
    draftDeleted,
    restoreFailed,
    multipleDraftsFound,
    isRestoringDraft,
    isSavingDraft,
    lastSaveFailed,
    saveRetryScheduled,
    saveRetryExhausted,
    saveDraftNow,
    handleSaveDraft,
    handleReloadServerDraft,
    handleKeepLocalDraft,
    handleRetryDraftRestore,
    handleSaveDeletedDraftAsNew,
    handleDiscardDeletedDraft,
    cleanupPublishedDraft: clearPublishedDraftRecovery,
    clearScheduledDraftRecovery,
  }
}
