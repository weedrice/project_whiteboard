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
import PostDraftActions from '@/components/board/PostDraftActions.vue'
import PostPreviewModal from '@/components/board/PostPreviewModal.vue'
import { requiresPreservedPostHtml } from '@/utils/postHtmlSandbox'
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
  const candidate = props.initialDraftId
  if (candidate == null || candidate === '') return null
  if (typeof candidate === 'string' && !/^[1-9]\d*$/.test(candidate)) return null
  const numericDraftId = Number(candidate)
  return Number.isSafeInteger(numericDraftId) && numericDraftId > 0 ? numericDraftId : null
})
const routeFormIdentity = computed(() => [
  props.mode,
  boardUrl.value || 'unknown',
  scheduledPostId.value ? `scheduled-${scheduledPostId.value}` : (postId.value || 'new'),
  preferredDraftId.value ?? 'default-draft',
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

const durableDraftFileIds = ref<number[]>([])
const {
  ownedUploadedFileIds,
  recordUploadedFile,
  adoptUploadedFiles: adoptUploadedFileOwnership,
  releaseUploadedFiles: releaseUploadedFileOwnership,
} = usePostComposerUploadOwnership({
  identity: formIdentity,
  content: computed(() => form.value.content),
  durableDraftFileIds,
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
})

function getLeaveState() {
  return {
    dirty: hasUnsavedChanges.value,
    submitting: isSubmissionLocked.value,
    message: leaveConfirmMessage.value,
  }
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  const leaveState = getLeaveState()
  if (!leaveState.dirty && !leaveState.submitting) return
  event.preventDefault()
  event.returnValue = leaveState.message
  return leaveState.message
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
  draftPresentation,
  draftId,
  draftConflict,
  draftProtected,
  draftDeleted,
  isSavingDraft,
  saveDraftNow,
  executeDraftAction,
  cleanupPublishedDraft,
  clearScheduledDraftRecovery,
  flushLatestLocalSnapshot,
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
  durableDraftFileIds,
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

const effectiveDraftPresentation = computed(() => ({
  ...draftPresentation.value,
  actions: draftPresentation.value.actions.map((action) => ({
    ...action,
    disabled: action.disabled || isSubmitting.value || isSubmissionLocked.value,
  })),
}))
const hasSaveDraftAction = computed(() => (
  effectiveDraftPresentation.value.actions.length === 1
  && effectiveDraftPresentation.value.actions[0]?.id === 'save'
))
const hasBlockingDraftActions = computed(() => (
  effectiveDraftPresentation.value.actions.length > 0 && !hasSaveDraftAction.value
))

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
    if (editorViewMode.value === 'visual' && requiresPreservedPostHtml(content)) {
      handleEditorViewModeChange('visual')
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
  handleSaveDraft: () => executeDraftAction('save'),
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
  getLeaveState,
  flushDraft: flushLatestLocalSnapshot,
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
            :draft-presentation="effectiveDraftPresentation"
            :scheduled-at="scheduledAt"
            :show-scheduler="props.mode === 'create' || Boolean(scheduledPostId)"
            @draft-action="executeDraftAction"
            @update:scheduled-at="scheduledAt = $event"
          />
        </fieldset>
      </form>
    </div>

    <div class="nv-compose-mobile-actions nv-elevated-surface sm:hidden">
      <div v-if="effectiveDraftPresentation.label" class="truncate px-1 text-xs font-medium text-[var(--nv-muted)]">
        {{ effectiveDraftPresentation.label }}
      </div>
      <PostDraftActions
        v-if="hasBlockingDraftActions"
        :presentation="effectiveDraftPresentation"
        @action="executeDraftAction"
      />
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
        <PostDraftActions
          v-else-if="hasSaveDraftAction"
          :presentation="effectiveDraftPresentation"
          inline
          @action="executeDraftAction"
        />
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
      <PostDraftActions
        v-if="!props.hidePreview && hasSaveDraftAction"
        class="mt-2"
        :presentation="effectiveDraftPresentation"
        @action="executeDraftAction"
      />
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


