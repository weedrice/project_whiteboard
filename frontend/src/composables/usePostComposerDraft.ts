import { computed, ref, watch, type ComputedRef, type Ref } from 'vue'
import { usePostDraft } from '@/composables/usePostDraft'
import type { PostComposerSnapshot } from '@/composables/usePostComposerState'
import type { PostFormFileIdScope } from '@/utils/postForm'
import logger from '@/utils/logger'

type ComposerToastType = 'info' | 'success' | 'warning' | 'error'

type UsePostComposerDraftOptions = {
  isAuthenticated: Ref<boolean>
  userId: Ref<string | number | undefined>
  identity: Ref<string>
  mode: () => 'create' | 'edit'
  boardUrl: Ref<string>
  postId: Ref<string | number>
  isLoading: Ref<boolean>
  selectedCategoryId: Ref<string | number>
  firstCategoryId: ComputedRef<number | undefined>
  buildPayload: (fileIdScope?: PostFormFileIdScope) => ReturnType<typeof import('@/utils/postForm').buildPostFormPayload>
  applyDraft: (draft: PostComposerSnapshot) => void
  markCurrentSnapshotSaved: () => void
  t: (key: string, values?: Record<string, unknown>) => string
  addToast: (message: string, type: ComposerToastType) => void
}

export function usePostComposerDraft(options: UsePostComposerDraftOptions) {
  const draftEnabled = computed(() => options.isAuthenticated.value && !!options.boardUrl.value)
  const draftStorageKey = computed(() =>
    `noviis:draft:${options.userId.value ?? 'guest'}:${options.mode()}:${options.boardUrl.value || 'unknown'}:${options.postId.value || 'new'}`,
  )
  const hasRestoredDraft = ref(false)

  const {
    saveNow: saveDraftNow,
    scheduleAutosave,
    restoreDraft,
    clearRecovery,
    writeLocalSnapshot,
    lastSavedAt,
    isSavingDraft,
    restoreSource,
    draftId,
    resetSession,
  } = usePostDraft({
    enabled: draftEnabled,
    storageKey: draftStorageKey,
    buildPayload: () => ({
      ...options.buildPayload('draft'),
      boardUrl: options.boardUrl.value,
      originalPostId: options.mode() === 'edit' ? Number(options.postId.value) : undefined,
    }),
    applyDraft: options.applyDraft,
  })

  const draftStatusLabel = computed(() => {
    if (!draftEnabled.value) return ''
    if (isSavingDraft.value) return options.t('board.writePost.draftStatus.saving')
    if (lastSavedAt.value) {
      return options.t('board.writePost.draftStatus.savedAt', {
        time: new Date(lastSavedAt.value).toLocaleTimeString(),
      })
    }
    return options.t('board.writePost.draftStatus.ready')
  })

  watch(
    options.identity,
    (_current, previous) => {
      if (previous === undefined) return
      hasRestoredDraft.value = false
      resetSession()
    },
  )

  watch(
    () => [options.isLoading.value, options.identity.value] as const,
    async ([loading]) => {
      if (loading || hasRestoredDraft.value) return
      const restoringIdentity = options.identity.value
      hasRestoredDraft.value = true

      if (options.mode() === 'create' && !options.selectedCategoryId.value && options.firstCategoryId.value != null) {
        options.selectedCategoryId.value = options.firstCategoryId.value
      }

      await restoreDraft()
      if (restoringIdentity !== options.identity.value) return
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
    },
    { immediate: true },
  )

  const draftSignature = computed(() => JSON.stringify({
    ...options.buildPayload(),
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
    try {
      const savedDraft = await saveDraftNow()
      if (savedDraft) {
        options.markCurrentSnapshotSaved()
        options.addToast(options.t('board.writePost.draftStatus.saved'), 'success')
      }
    } catch (error) {
      logger.error('Failed to save draft:', error)
    }
  }

  return {
    draftEnabled,
    draftStatusLabel,
    draftId,
    isSavingDraft,
    saveDraftNow,
    handleSaveDraft,
    cleanupPublishedDraft: clearRecovery,
  }
}
