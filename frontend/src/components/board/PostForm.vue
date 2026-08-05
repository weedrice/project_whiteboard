<script setup lang="ts">
import { computed, nextTick, ref, watch, type ComponentPublicInstance } from 'vue'
import { usePostComposerDraft } from '@/features/board/posts/form/usePostComposerDraft'
import { usePostComposerEffects, type ComposerEditor } from '@/features/board/posts/form/usePostComposerEffects'
import { usePostComposerSubmit, type PostFormSubmitResult } from '@/features/board/posts/form/usePostComposerSubmit'
import { usePostEditorViewMode } from '@/features/board/posts/form/usePostEditorViewMode'
import { usePostFormEditHydration } from '@/features/board/posts/form/usePostFormEditHydration'
import { usePostFormCategoryOptions } from '@/features/board/posts/form/usePostFormCategoryOptions'
import { usePostFormMetadataBindings } from '@/features/board/posts/form/usePostFormMetadataBindings'
import { usePostFormResource } from '@/features/board/posts/form/usePostFormResource'
import { usePostSeriesOptions } from '@/features/board/posts/form/usePostSeriesOptions'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import type { SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { useToastStore } from '@/stores/toast'
import PostFormHeader from '@/components/board/PostFormHeader.vue'
import PostFormMainSection from '@/components/board/PostFormMainSection.vue'
import PostFormSidePanel from '@/components/board/PostFormSidePanel.vue'
import PostPreviewModal from '@/components/board/PostPreviewModal.vue'
import { requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { usePostComposerState } from '@/features/board/posts/form/usePostComposerState'
import { usePostComposerUploadOwnership } from '@/features/board/posts/form/usePostComposerUploadOwnership'
import { useFieldValidation } from '@/composables/useFieldValidation'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import { toDateTimeLocalInputValue } from '@/utils/date'
import {
  POST_POLL_MAX_OPTIONS,
  POST_POLL_MIN_OPTIONS,
  POST_POLL_OPTION_MAX_LENGTH,
  POST_POLL_QUESTION_MAX_LENGTH,
  POST_TITLE_MAX_LENGTH,
  validatePostDraftContent,
  validatePostDraftPollContract,
  validatePostFormContent,
  type PostFormPollValidationError,
  validatePostFormPoll,
} from '@/utils/postForm'

const props = defineProps<{
  mode: 'create' | 'edit'
  boardUrl?: string
  postId?: string | number
  scheduledPostId?: string | number
  initialDraftId?: string | number | null
  onSubmitted?: (result: PostFormSubmitResult) => void
  redirectOnCreate?: string
  goBackOnCreate?: boolean
  createTitleOverride?: string
  createSuccessToastMessage?: string
  hideCategory?: boolean
  hideTags?: boolean
  hideNotice?: boolean
  hideSpoiler?: boolean
  hideSecret?: boolean
  skipBoardLookup?: boolean
  hideBoardLabel?: boolean
  hidePreview?: boolean
}>()

const emit = defineEmits<{
  cancel: []
}>()

const { t } = useI18n()
const authStore = useAuthStore()
const toastStore = useToastStore()
const scheduledAt = ref('')
const savedScheduledAt = ref('')

const boardUrl = computed(() => props.boardUrl ?? '')
const postId = computed(() => props.postId ?? '')
const scheduledPostId = computed(() => props.scheduledPostId ?? '')
const preferredDraftId = computed(() => {
  if (props.initialDraftId == null || props.initialDraftId === '') return null
  const numericDraftId = Number(props.initialDraftId)
  return Number.isFinite(numericDraftId) && numericDraftId > 0 ? numericDraftId : null
})
const routeFormIdentity = computed(() => [
  props.mode,
  boardUrl.value || 'unknown',
  scheduledPostId.value ? `scheduled-${scheduledPostId.value}` : (postId.value || 'new'),
].join(':'))
const formIdentity = computed(() => [
  authStore.sessionGeneration,
  authStore.user?.userId ?? 'hydrating',
  routeFormIdentity.value,
].join(':'))

const {
  board,
  categories,
  post,
  scheduledPost,
  isLoading,
  isSubmitting,
  showNotice,
  canShowNsfw,
  createPost,
  createScheduledPost,
  updateScheduledPost,
  updatePost,
} = usePostFormResource({
  mode: () => props.mode,
  boardUrl,
  postId,
  scheduledPostId,
  skipBoardLookup: () => props.skipBoardLookup,
  hideNotice: () => props.hideNotice,
})

const pageTitle = computed(() =>
  scheduledPostId.value
    ? t('board.writePost.editScheduledTitle')
    : props.mode === 'create'
    ? (props.createTitleOverride || t('board.writePost.createTitle'))
    : t('board.writePost.editTitle'),
)
const boardLabel = computed(() => board.value?.boardName || boardUrl.value)

const submitLabel = computed(() =>
  scheduledPostId.value
    ? (isSubmitting.value ? t('board.writePost.updating') : t('board.writePost.updateSchedule'))
    : scheduledAt.value
    ? (isSubmitting.value ? t('board.writePost.scheduling') : t('board.writePost.actions.schedule'))
    :
  isSubmitting.value
    ? (props.mode === 'create' ? t('board.writePost.submitting') : t('board.writePost.updating'))
    : (props.mode === 'create' ? t('common.submit') : t('board.writePost.update')),
)

function pollValidationMessage(error: PostFormPollValidationError) {
  const params = {
    questionMax: POST_POLL_QUESTION_MAX_LENGTH,
    optionMax: POST_POLL_OPTION_MAX_LENGTH,
    min: POST_POLL_MIN_OPTIONS,
    max: POST_POLL_MAX_OPTIONS,
  }

  switch (error) {
    case 'questionRequired': return t('board.writePost.poll.validation.questionRequired', params)
    case 'questionTooLong': return t('board.writePost.poll.validation.questionTooLong', params)
    case 'optionRequired': return t('board.writePost.poll.validation.optionRequired', params)
    case 'optionCount': return t('board.writePost.poll.validation.optionCount', params)
    case 'optionTooLong': return t('board.writePost.poll.validation.optionTooLong', params)
    case 'closesAtFuture': return t('board.writePost.poll.validation.closesAtFuture', params)
    case 'closesAtAfterSchedule': return t('board.writePost.poll.validation.closesAtAfterSchedule', params)
  }
}

const {
  form,
  isDirty,
  markCurrentSnapshotSaved,
  applyDraftSnapshot,
  buildPayload,
  openPollEditor,
  trackUploadedFile,
  resetFormState,
} = usePostComposerState({
  mode: () => props.mode,
  hideCategory: () => props.hideCategory,
  hideTags: () => props.hideTags,
  hideSpoiler: () => props.hideSpoiler,
  hideSecret: () => props.hideSecret,
  showNotice,
  canShowNsfw,
  includePoll: () => props.mode === 'create' || Boolean(scheduledPostId.value),
})

const {
  seriesOptions,
  newSeriesTitle,
  isCreatingSeries,
  isPostSeriesError,
  loadPostSeries,
  createSeries: handleCreateSeries,
  cancelCreateSeriesRequest,
  resetSeriesInput,
} = usePostSeriesOptions({
  form,
  formIdentity,
})

const hasUnsavedChanges = computed(() => (
  isDirty.value || (Boolean(scheduledPostId.value) && scheduledAt.value !== savedScheduledAt.value)
))

function markCurrentComposerSaved() {
  markCurrentSnapshotSaved()
  savedScheduledAt.value = scheduledAt.value
}

const {
  ownedUploadedFileIds,
  recordUploadedFile,
  adoptUploadedFiles: adoptUploadedFileOwnership,
  releaseUploadedFiles: releaseUploadedFileOwnership,
} = usePostComposerUploadOwnership({
  identity: formIdentity,
  content: computed(() => form.value.content),
})

function handleEditorFileUploaded(fileId: number) {
  trackUploadedFile(fileId)
  recordUploadedFile(fileId)
}

type PostRequiredField = 'title'
const postValidation = useFieldValidation<PostRequiredField>({
  validators: {
    title: (values) => String(values.title ?? '').trim() ? '' : t('board.writePost.placeholder.title'),
  },
  fieldIds: { title: 'title' },
})
const postRequiredValues = computed(() => ({ title: form.value.title }))
const postContentIsValid = () => {
  const payload = buildPayload('content')
  return validatePostFormContent({
    title: payload.title,
    content: payload.contents,
    tags: payload.tags,
    fileIds: payload.fileIds,
  }) == null
}
const draftContentIsValid = () => {
  const payload = buildPayload('draft')
  return validatePostDraftContent({
    title: payload.title,
    content: payload.contents,
    tags: payload.tags,
    fileIds: payload.fileIds,
  }) == null
    && validatePostDraftPollContract(payload.poll) == null
}

const {
  filteredCategories,
  firstCategoryId,
  isCategorySelectable,
} = usePostFormCategoryOptions({
  categories,
  board,
  post,
  selectedCategoryId: computed({
    get: () => form.value.categoryId,
    set: (categoryId) => {
      form.value.categoryId = categoryId
    },
  }),
  userRole: computed(() => authStore.user?.role),
})

const previewContent = computed(() => form.value.content || `<p>${t('board.writePost.preview.emptyContent')}</p>`)
const leaveConfirmMessage = computed(() => t('board.writePost.leaveConfirm'))
const editorViewOptions = computed<SegmentedControlOption[]>(() => [
  { value: 'visual', label: t('board.writePost.visualMode') },
  { value: 'html', label: t('board.writePost.viewHtmlSource') },
])
const { metadataPanelProps, metadataPanelHandlers } = usePostFormMetadataBindings({
  form,
  categories: filteredCategories,
  seriesOptions,
  newSeriesTitle,
  isCreatingSeries,
  showNotice,
  canShowNsfw,
  hideCategory: () => props.hideCategory,
  hideTags: () => props.hideTags,
  hideSpoiler: () => props.hideSpoiler,
  hideSecret: () => props.hideSecret,
  createSeries: handleCreateSeries,
  boardUrl,
})

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!hasUnsavedChanges.value && !isSubmitting.value && !isSubmissionLocked.value) return
  event.preventDefault()
  event.returnValue = leaveConfirmMessage.value
  return leaveConfirmMessage.value
}

function resetFormIdentityState() {
  resetEditHydrationState()
  resetFormState()
  resetSeriesInput()
  scheduledAt.value = ''
  savedScheduledAt.value = ''
  hasHydratedScheduledPost.value = false
}

watch(
  formIdentity,
  (current, previous) => {
    if (previous === undefined) return
    cancelCreateSeriesRequest()
    resetFormIdentityState()
    void nextTick(() => {
      if (formIdentity.value === current) hydrateScheduledPost(scheduledPost.value)
    })
  },
)

const { resetEditHydrationState } = usePostFormEditHydration({
  mode: () => props.mode,
  post,
  postId,
  applyDraftSnapshot,
  markCurrentSnapshotSaved: markCurrentComposerSaved,
})

const hasHydratedScheduledPost = ref(false)
watch(scheduledPostId, () => {
  hasHydratedScheduledPost.value = false
}, { flush: 'sync' })
function hydrateScheduledPost(value: typeof scheduledPost.value) {
  if (!scheduledPostId.value || !value || hasHydratedScheduledPost.value) return
  if (String(value.scheduledPostId) !== String(scheduledPostId.value)) return
  if (value.userId != null && value.userId !== authStore.user?.userId) return
  if (value.boardUrl !== boardUrl.value) return
  hasHydratedScheduledPost.value = true
  applyDraftSnapshot({
    title: value.title,
    contents: value.contents,
    categoryId: value.categoryId,
    tags: value.tags ?? [],
    isNsfw: value.isNsfw,
    isSpoiler: value.isSpoiler,
    isNotice: value.isNotice,
    isSecret: value.isSecret,
    seriesId: value.seriesId,
    poll: value.poll ?? null,
    fileIds: value.fileIds ?? [],
  })
  // offset이 붙은 값을 그대로 넣으면 datetime-local 입력이 빈칸이 된다.
  scheduledAt.value = toDateTimeLocalInputValue(value.scheduledAt)
  markCurrentComposerSaved()
}
watch(scheduledPost, hydrateScheduledPost, { immediate: true })

const {
  draftEnabled,
  draftStatusLabel,
  draftId,
  draftConflict,
  draftProtected,
  protectedDraftForkAvailable,
  draftDeleted,
  restoreFailed,
  multipleDraftsFound,
  isRestoringDraft,
  isSavingDraft,
  lastSaveFailed,
  saveDraftNow,
  handleSaveDraft,
  handleReloadServerDraft,
  handleKeepLocalDraft,
  handleRetryDraftRestore,
  handleSaveDeletedDraftAsNew,
  handleDiscardDeletedDraft,
  handleSaveProtectedDraftAsNew,
  handleDiscardProtectedDraft,
  cleanupPublishedDraft,
  clearScheduledDraftRecovery,
} = usePostComposerDraft({
  isAuthenticated: computed(() => Boolean(authStore.isAuthenticated) && !scheduledPostId.value),
  userId: computed(() => authStore.user?.userId),
  sessionGeneration: computed(() => authStore.sessionGeneration),
  identity: formIdentity,
  mode: () => props.mode,
  boardUrl,
  postId,
  preferredDraftId,
  isLoading,
  selectedCategoryId: computed({
    get: () => form.value.categoryId,
    set: (categoryId) => {
      form.value.categoryId = categoryId
    },
  }),
  firstCategoryId,
  isCategorySelectable,
  buildPayload,
  applyDraft: applyDraftSnapshot,
  markCurrentSnapshotSaved,
  ownedUploadedFileIds,
  adoptUploadedFileOwnership,
  releaseUploadedFileOwnership,
  t,
  addToast: toastStore.addToast,
  validateBeforeSave: draftContentIsValid,
})

const effectiveDraftId = computed(() => (
  scheduledPostId.value ? (scheduledPost.value?.draftId ?? null) : draftId.value
))

usePwaReloadBlocker(hasUnsavedChanges)
usePwaReloadBlocker(
  computed(() => isSubmitting.value || isSavingDraft.value),
  { retainWhileBlockedOnDispose: true },
)

const { handleSubmit, isSubmissionLocked } = usePostComposerSubmit({
  identity: formIdentity,
  mode: () => props.mode,
  boardUrl,
  postId,
  scheduledPostId,
  board,
  form,
  hideCategory: () => props.hideCategory,
  draftEnabled,
  draftBlockReason: computed(() => {
    if (draftDeleted.value) return 'deleted' as const
    if (draftConflict.value) return 'conflict' as const
    if (draftProtected.value) return 'protected' as const
    return null
  }),
  draftId: effectiveDraftId,
  saveDraftNow,
  buildPayload,
  markCurrentSnapshotSaved: markCurrentComposerSaved,
  cleanupPublishedDraft,
  clearScheduledDraftRecovery,
  releaseUploadedFileOwnership,
  createPost,
  createScheduledPost,
  updateScheduledPost,
  updatePost,
  onSubmitted: () => props.onSubmitted,
  createSuccessToastMessage: () => props.createSuccessToastMessage,
  scheduledAt,
  t,
  addToast: toastStore.addToast,
  validateBeforeSubmit: () => {
    const valid = postValidation.validateAll(postRequiredValues.value)
    if (!valid) {
      toastStore.addToast(t('board.writePost.validation'), 'error')
      return false
    }

    if (!postContentIsValid()) {
      toastStore.addToast(t('board.writePost.validation'), 'error')
      return false
    }

    if (props.mode === 'create' || scheduledPostId.value) {
      const pollError = validatePostFormPoll(form.value.poll, Date.now(), scheduledAt.value)
      if (pollError) {
        toastStore.addToast(pollValidationMessage(pollError), 'error')
        return false
      }
    }

    return true
  },
})

function handleCancel() {
  if (isSubmitting.value || isSubmissionLocked.value) return
  emit('cancel')
}

const {
  editorViewMode,
  handleEditorViewModeChange,
} = usePostEditorViewMode(computed({
  get: () => form.value.content,
  set: (content) => {
    form.value.content = content
  },
}))

watch(
  () => form.value.content,
  (content) => {
    if (editorViewMode.value === 'visual' && requiresSandboxedPostHtml(content)) {
      handleEditorViewModeChange('html')
    }
  },
  { flush: 'sync', immediate: true },
)

const {
  tiptapEditorRef,
  editorWrapperRef,
  composePageRef,
  videoPopoverRef,
  showPreview,
  showEmoticonPicker,
  showVideoPopover,
  videoUrl,
  videoPopoverStyle,
  openVideoPopover,
  closeVideoPopover,
  insertVideoFromPopover,
  handleEmoticonSelect,
  handleFocusIn,
  handleFocusOut,
} = usePostComposerEffects({
  t,
  addToast: toastStore.addToast,
  handleSubmit,
  handleSaveDraft,
  handleCancel,
  onBeforeUnload,
})

function assignTiptapEditor(value: Element | ComponentPublicInstance | null) {
  tiptapEditorRef.value = value as ComposerEditor | null
}

function assignEditorWrapper(value: Element | ComponentPublicInstance | null) {
  editorWrapperRef.value = value instanceof HTMLElement ? value : null
}

function assignVideoPopover(value: Element | ComponentPublicInstance | null) {
  videoPopoverRef.value = value instanceof HTMLElement ? value : null
}

defineExpose({
  hasUnsavedChanges: () => hasUnsavedChanges.value,
  isSubmissionInProgress: () => isSubmissionLocked.value,
  getLeaveConfirmMessage: () => leaveConfirmMessage.value,
})
</script>

<template>
  <div class="w-full max-w-full overflow-x-hidden pb-24 sm:pb-0">
    <div
      ref="composePageRef"
      class="nv-compose-page"
      @focusin="handleFocusIn"
      @focusout="handleFocusOut"
    >
      <PostFormHeader
        :page-title="pageTitle"
        :board-label="boardLabel"
        :hide-board-label="props.hideBoardLabel"
        :hide-preview="props.hidePreview"
        :is-submitting="isSubmitting || isSubmissionLocked"
        :submit-label="submitLabel"
        @cancel="handleCancel"
        @preview="showPreview = true"
        @submit="handleSubmit"
      />

      <div v-if="isLoading" class="py-10 text-center">
        <BaseSpinner size="lg" />
      </div>

      <ErrorState
        v-else-if="isPostSeriesError"
        class="!max-w-none !py-4"
        title-tag="h2"
        :message="t('common.messages.loadFailed')"
        show-retry
        @retry="loadPostSeries"
      />

      <form
        v-if="!isLoading"
        class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_18.5rem]"
        @submit.prevent="handleSubmit"
      >
        <fieldset
          class="contents"
          :disabled="isSubmitting || isSubmissionLocked"
          :inert="isSubmitting || isSubmissionLocked"
          :aria-busy="isSubmitting || isSubmissionLocked"
        >
          <PostFormMainSection
            :title="form.title"
            :title-max-length="POST_TITLE_MAX_LENGTH"
            :content="form.content"
            :title-error="postValidation.visibleError('title')"
            :tags="form.tags"
            :poll="form.poll"
            :poll-read-only="props.mode === 'edit' && !scheduledPostId"
            :hide-tags="props.hideTags"
            :metadata-panel-props="metadataPanelProps"
            :metadata-panel-handlers="metadataPanelHandlers"
            :editor-view-mode="editorViewMode"
            :editor-view-options="editorViewOptions"
            :upload-owner-identity="formIdentity"
            :show-video-popover="showVideoPopover"
            :show-emoticon-picker="showEmoticonPicker"
            :video-url="videoUrl"
            :video-popover-style="videoPopoverStyle"
            :assign-tiptap-editor="assignTiptapEditor"
            :assign-editor-wrapper="assignEditorWrapper"
            :assign-video-popover="assignVideoPopover"
            @update:title="form.title = $event"
            @update:content="form.content = $event"
            @blur-title="postValidation.touchField('title', postRequiredValues)"
            @update:tags="form.tags = $event"
            @update:poll="form.poll = $event"
            @update:editor-view-mode="handleEditorViewModeChange"
            @update:show-emoticon-picker="showEmoticonPicker = $event"
            @update:video-url="videoUrl = $event"
            @open-video="openVideoPopover"
            @close-video="closeVideoPopover"
            @insert-video="insertVideoFromPopover"
            @select-emoticon="handleEmoticonSelect"
            @file-uploaded="handleEditorFileUploaded"
            @open-poll="openPollEditor"
          />

          <PostFormSidePanel
            :metadata-panel-props="metadataPanelProps"
            :metadata-panel-handlers="metadataPanelHandlers"
            :draft-status-label="draftStatusLabel"
            :draft-enabled="draftEnabled"
            :is-saving-draft="isSavingDraft"
            :is-restoring-draft="isRestoringDraft"
            :draft-conflict="draftConflict"
            :draft-protected="draftProtected"
            :protected-draft-fork-available="protectedDraftForkAvailable"
            :draft-deleted="draftDeleted"
            :restore-failed="restoreFailed"
            :multiple-drafts-found="multipleDraftsFound"
            :save-failed="lastSaveFailed"
            :scheduled-at="scheduledAt"
            :show-scheduler="props.mode === 'create' || Boolean(scheduledPostId)"
            @save-draft="handleSaveDraft"
            @reload-server-draft="handleReloadServerDraft"
            @keep-local-draft="handleKeepLocalDraft"
            @retry-restore="handleRetryDraftRestore"
            @save-deleted-as-new="handleSaveDeletedDraftAsNew"
            @discard-deleted="handleDiscardDeletedDraft"
            @save-protected-as-new="handleSaveProtectedDraftAsNew"
            @discard-protected="handleDiscardProtectedDraft"
            @update:scheduled-at="scheduledAt = $event"
          />
        </fieldset>
      </form>
    </div>

    <div class="nv-compose-mobile-actions nv-elevated-surface sm:hidden">
      <div v-if="draftStatusLabel" class="truncate px-1 text-xs font-medium text-[var(--nv-muted)]">
        {{ draftStatusLabel }}
      </div>
      <div v-if="draftDeleted" class="grid grid-cols-2 gap-2">
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          class="min-h-[36px] w-full"
          :disabled="isSavingDraft || isRestoringDraft || isSubmitting || isSubmissionLocked"
          @click="handleSaveDeletedDraftAsNew"
        >
          {{ $t('board.writePost.draftStatus.saveAsNew') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[36px] w-full"
          :disabled="isSavingDraft || isRestoringDraft || isSubmitting || isSubmissionLocked"
          @click="handleDiscardDeletedDraft"
        >
          {{ $t('board.writePost.draftStatus.discardLocal') }}
        </BaseButton>
      </div>
      <div v-else-if="draftConflict" class="grid grid-cols-2 gap-2">
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[36px] w-full"
          :disabled="isSavingDraft || isRestoringDraft || isSubmitting || isSubmissionLocked"
          @click="handleReloadServerDraft"
        >
          {{ $t('board.writePost.draftStatus.reloadServer') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          class="min-h-[36px] w-full"
          :disabled="isSavingDraft || isRestoringDraft || isSubmitting || isSubmissionLocked"
          @click="handleKeepLocalDraft"
        >
          {{ $t('board.writePost.draftStatus.keepLocal') }}
        </BaseButton>
      </div>
      <div v-else-if="draftProtected && protectedDraftForkAvailable" class="grid grid-cols-2 gap-2">
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          class="min-h-[36px] w-full"
          :disabled="isSavingDraft || isRestoringDraft || isSubmitting || isSubmissionLocked"
          @click="handleSaveProtectedDraftAsNew"
        >
          {{ $t('board.writePost.draftStatus.saveAsNew') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[36px] w-full"
          :disabled="isSavingDraft || isRestoringDraft || isSubmitting || isSubmissionLocked"
          @click="handleDiscardProtectedDraft"
        >
          {{ $t('board.writePost.draftStatus.discardLocal') }}
        </BaseButton>
      </div>
      <BaseButton
        v-else-if="draftProtected || multipleDraftsFound"
        type="button"
        variant="secondary"
        size="sm"
        class="min-h-[36px] w-full"
        to="/mypage/drafts"
      >
        {{ draftProtected
          ? $t('board.writePost.draftStatus.openScheduledPosts')
          : $t('board.writePost.draftStatus.openDrafts') }}
      </BaseButton>
      <BaseButton
        v-else-if="restoreFailed"
        type="button"
        variant="secondary"
        size="sm"
        class="min-h-[36px] w-full"
        :disabled="isRestoringDraft || isSubmitting || isSubmissionLocked"
        @click="handleRetryDraftRestore"
      >
        {{ $t('board.writePost.draftStatus.retryRestore') }}
      </BaseButton>
      <div class="flex items-center gap-2">
        <BaseButton
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[40px]"
          :disabled="isSubmitting || isSubmissionLocked"
          @click="handleCancel"
        >
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton
          v-if="!props.hidePreview"
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[40px] flex-1"
          :disabled="isSubmitting || isSubmissionLocked"
          @click="showPreview = true"
        >
          {{ $t('board.writePost.actions.preview') }}
        </BaseButton>
        <BaseButton
          v-else-if="draftEnabled && !draftConflict && !draftProtected && !draftDeleted"
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[40px] flex-1"
          :disabled="isSavingDraft || isSubmitting || isSubmissionLocked"
          @click="handleSaveDraft"
        >
          {{ isSavingDraft
            ? $t('board.writePost.draftStatus.saving')
            : lastSaveFailed
              ? $t('board.writePost.draftStatus.retryNow')
              : $t('board.writePost.actions.saveDraft') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          class="min-h-[40px] flex-1"
          :loading="isSubmitting || isSubmissionLocked"
          :disabled="isSavingDraft || isSubmitting || isSubmissionLocked"
          @click="handleSubmit"
        >
          {{ scheduledAt ? $t('board.writePost.actions.schedule') : submitLabel }}
        </BaseButton>
      </div>
      <BaseButton
        v-if="!props.hidePreview && draftEnabled && !draftConflict && !draftProtected && !draftDeleted"
        type="button"
        variant="secondary"
        size="sm"
        class="mt-2 min-h-[36px] w-full"
        :disabled="isSavingDraft || isSubmitting || isSubmissionLocked"
        @click="handleSaveDraft"
      >
        {{ isSavingDraft
          ? $t('board.writePost.draftStatus.saving')
          : lastSaveFailed
            ? $t('board.writePost.draftStatus.retryNow')
            : $t('board.writePost.actions.saveDraft') }}
      </BaseButton>
    </div>

    <PostPreviewModal
      v-if="!props.hidePreview"
      :is-open="showPreview"
      :board-label="board?.boardName || boardUrl"
      :post-title="form.title"
      :tags="form.tags"
      :content="previewContent"
      :hide-board-label="props.hideBoardLabel"
      :hide-tags="props.hideTags"
      @close="showPreview = false"
    />
  </div>
</template>

<style scoped>
.nv-compose-page {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.nv-compose-side-section + .nv-compose-side-section {
  border-top: 1px solid var(--nv-line);
  padding-top: 1rem;
}

.nv-compose-page .text-xs.text-\[var\(--nv-muted\)\] > span.mx-2 {
  font-size: 0;
}

.nv-compose-page .text-xs.text-\[var\(--nv-muted\)\] > span.mx-2::before {
  content: '/';
  font-size: 0.75rem;
}

.nv-compose-mobile-actions {
  background: color-mix(in srgb, var(--nv-surface) 96%, transparent);
  border: 1px solid var(--nv-line);
  border-radius: 1rem 1rem 0 0;
  bottom: calc(var(--nv-bottom-nav-height) + env(safe-area-inset-bottom));
  box-shadow: var(--nv-shadow-card);
  left: 0.75rem;
  padding: 0.65rem;
  position: fixed;
  right: 0.75rem;
  z-index: 45;
}
</style>


