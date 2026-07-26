import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import { usePostDraft } from '@/features/board/posts/draft/usePostDraft'
import type { PostComposerSnapshot } from '@/features/board/posts/form/usePostComposerState'
import type { PostFormFileIdScope } from '@/utils/postForm'
import logger from '@/utils/logger'
import { formatTimeOnly } from '@/utils/date'

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
  const draftStorageKey = computed(() =>
    `noviis:draft:${options.userId.value ?? 'guest'}:${options.mode()}:${options.boardUrl.value || 'unknown'}:${options.postId.value || 'new'}`,
  )
  const hasRestoredDraft = ref(false)
  const initializedBaselineIdentity = ref<string | null>(null)
  const draftIdentity = computed(() => [
    options.sessionGeneration.value,
    options.userId.value ?? 'hydrating',
    options.identity.value,
  ].join(':'))

  const {
    saveNow: saveDraftNow,
    scheduleAutosave,
    restoreDraft,
    clearRecovery,
    writeLocalSnapshot,
    lastSavedAt,
    lastSaveScope,
    lastSaveFailed,
    draftConflict,
    reloadServerDraft,
    isSavingDraft,
    restoreSource,
    draftId,
    resetSession,
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
    applyDraft: options.applyDraft,
    onSaved: options.markCurrentSnapshotSaved,
    onServerSaved: (payload) => options.releaseUploadedFileOwnership(payload.fileIds ?? []),
    canPersist: options.validateBeforeSave,
  })

  const draftStatusLabel = computed(() => {
    if (!draftEnabled.value) return ''
    if (isSavingDraft.value) return options.t('board.writePost.draftStatus.saving')
    if (draftConflict.value) return options.t('board.writePost.draftStatus.conflict')
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
      hasRestoredDraft.value = true

      if (options.mode() === 'create' && !options.selectedCategoryId.value && options.firstCategoryId.value != null) {
        options.selectedCategoryId.value = options.firstCategoryId.value
      }

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
    },
    { immediate: true },
  )

  const draftSignature = computed(() => JSON.stringify({
    ...options.buildPayload('draft'),
    boardUrl: options.boardUrl.value,
    originalPostId: options.mode() === 'edit' ? Number(options.postId.value) : undefined,
  }))

  watch(
    draftSignature,
    () => {
      if (!hasRestoredDraft.value || !draftEnabled.value || options.isLoading.value) return
      writeLocalSnapshot()
      scheduleAutosave()
    },
    { flush: 'post' },
  )

  async function handleSaveDraft() {
    if (options.validateBeforeSave?.() === false) {
      options.addToast(options.t('board.writePost.validation'), 'error')
      return
    }
    try {
      const savedDraft = await saveDraftNow()
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

  return {
    draftEnabled,
    draftStatusLabel,
    draftId,
    draftConflict,
    isSavingDraft,
    saveDraftNow,
    handleSaveDraft,
    handleReloadServerDraft,
    cleanupPublishedDraft: clearRecovery,
    clearScheduledDraftRecovery: clearRecovery,
  }
}
