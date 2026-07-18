<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref, watch, type ComponentPublicInstance } from 'vue'
import { usePostComposerDraft } from '@/features/board/posts/form/usePostComposerDraft'
import { usePostComposerEffects, type ComposerEditor } from '@/features/board/posts/form/usePostComposerEffects'
import { usePostComposerSubmit, type PostFormSubmitResult } from '@/features/board/posts/form/usePostComposerSubmit'
import { usePostEditorViewMode } from '@/features/board/posts/form/usePostEditorViewMode'
import { usePostFormEditHydration } from '@/features/board/posts/form/usePostFormEditHydration'
import { usePostFormCategoryOptions } from '@/features/board/posts/form/usePostFormCategoryOptions'
import { usePostFormMetadataBindings } from '@/features/board/posts/form/usePostFormMetadataBindings'
import { usePostFormResource } from '@/features/board/posts/form/usePostFormResource'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import type { SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import { useToastStore } from '@/stores/toast'
import { userApi } from '@/api/user'
import { unwrapAxiosApiData } from '@/api/response'
import PostFormHeader from '@/components/board/PostFormHeader.vue'
import PostFormMainSection from '@/components/board/PostFormMainSection.vue'
import PostFormSidePanel from '@/components/board/PostFormSidePanel.vue'
import PostPreviewModal from '@/components/board/PostPreviewModal.vue'
import { requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { usePostComposerState } from '@/features/board/posts/form/usePostComposerState'
import type { PostSeries } from '@/types'
import { useFieldValidation } from '@/composables/useFieldValidation'
import { usePwaReloadBlocker } from '@/pwaReloadGuard'
import { AUTH_SCOPED_QUERY_META, sessionQueryKey } from '@/queryAuthScope'
import { userQueryKeys } from '@/features/user/userQueryKeys'
import { queryClient } from '@/queryClient'
import ErrorState from '@/components/common/ui/ErrorState.vue'
import {
  POST_POLL_MAX_OPTIONS,
  POST_POLL_MIN_OPTIONS,
  POST_POLL_OPTION_MAX_LENGTH,
  POST_POLL_QUESTION_MAX_LENGTH,
  validatePostFormPoll,
} from '@/utils/postForm'

const props = defineProps<{
  mode: 'create' | 'edit'
  boardUrl?: string
  postId?: string | number
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
const localSeriesOptions = ref<PostSeries[] | null>(null)
const serverSeriesOptions = ref<PostSeries[]>([])
const isPostSeriesError = ref(false)
const newSeriesTitle = ref('')
const isCreatingSeries = ref(false)
const scheduledAt = ref('')
const seriesOptions = computed(() => localSeriesOptions.value ?? serverSeriesOptions.value)
let createSeriesAbortController: AbortController | null = null

async function loadPostSeries() {
  const generation = authStore.sessionGeneration
  isPostSeriesError.value = false
  try {
    const series = await queryClient.fetchQuery({
      queryKey: sessionQueryKey(generation, userQueryKeys.postSeries),
      meta: AUTH_SCOPED_QUERY_META,
      queryFn: async ({ signal }) => unwrapAxiosApiData(await userApi.getPostSeries({ signal })),
    })
    if (generation !== authStore.sessionGeneration) return
    serverSeriesOptions.value = series
  } catch {
    if (generation !== authStore.sessionGeneration) return
    isPostSeriesError.value = true
  }
}

onMounted(() => {
  if (authStore.isAuthenticated) void loadPostSeries()
})

watch(() => authStore.sessionGeneration, () => {
  createSeriesAbortController?.abort()
  createSeriesAbortController = null
  localSeriesOptions.value = null
  serverSeriesOptions.value = []
  isPostSeriesError.value = false
  newSeriesTitle.value = ''
  isCreatingSeries.value = false
})

onScopeDispose(() => createSeriesAbortController?.abort())

const boardUrl = computed(() => props.boardUrl ?? '')
const postId = computed(() => props.postId ?? '')
const preferredDraftId = computed(() => {
  if (props.initialDraftId == null || props.initialDraftId === '') return null
  const numericDraftId = Number(props.initialDraftId)
  return Number.isFinite(numericDraftId) && numericDraftId > 0 ? numericDraftId : null
})
const routeFormIdentity = computed(() => `${props.mode}:${boardUrl.value || 'unknown'}:${postId.value || 'new'}`)
const formIdentity = computed(() => [
  authStore.sessionGeneration,
  authStore.user?.userId ?? 'hydrating',
  routeFormIdentity.value,
].join(':'))

const {
  board,
  categories,
  post,
  isLoading,
  isSubmitting,
  showNotice,
  canShowNsfw,
  createPost,
  createScheduledPost,
  updatePost,
} = usePostFormResource({
  mode: () => props.mode,
  boardUrl,
  postId,
  skipBoardLookup: () => props.skipBoardLookup,
  hideNotice: () => props.hideNotice,
})

const pageTitle = computed(() =>
  props.mode === 'create'
    ? (props.createTitleOverride || t('board.writePost.createTitle'))
    : t('board.writePost.editTitle'),
)
const boardLabel = computed(() => board.value?.boardName || boardUrl.value)

const submitLabel = computed(() =>
  scheduledAt.value
    ? (isSubmitting.value ? t('board.writePost.scheduling') : t('board.writePost.actions.schedule'))
    :
  isSubmitting.value
    ? (props.mode === 'create' ? t('board.writePost.submitting') : t('board.writePost.updating'))
    : (props.mode === 'create' ? t('common.submit') : t('board.writePost.update')),
)

const {
  form,
  isDirty,
  isFormDirty,
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
})

type PostRequiredField = 'title'
const postValidation = useFieldValidation<PostRequiredField>({
  validators: {
    title: (values) => String(values.title ?? '').trim() ? '' : t('board.writePost.placeholder.title'),
  },
  fieldIds: { title: 'title' },
})
const postRequiredValues = computed(() => ({ title: form.value.title }))

const {
  filteredCategories,
  firstCategoryId,
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

async function handleCreateSeries() {
  const title = newSeriesTitle.value.trim()
  if (!title || isCreatingSeries.value) return

  isCreatingSeries.value = true
  const generation = authStore.sessionGeneration
  const controller = new AbortController()
  createSeriesAbortController?.abort()
  createSeriesAbortController = controller
  try {
    const createdSeries = unwrapAxiosApiData(await userApi.createPostSeries({ title }, {
      signal: controller.signal,
    }))
    if (controller.signal.aborted || authStore.sessionGeneration !== generation) return
    localSeriesOptions.value = [
      ...seriesOptions.value.filter((series) => series.seriesId !== createdSeries.seriesId),
      createdSeries,
    ]
    queryClient.setQueryData(
      sessionQueryKey(generation, userQueryKeys.postSeries),
      localSeriesOptions.value,
    )
    form.value.seriesId = createdSeries.seriesId
    newSeriesTitle.value = ''
    toastStore.addToast(t('board.writePost.createSeriesSuccess'), 'success')
  } catch {
    if (controller.signal.aborted || authStore.sessionGeneration !== generation) return
    toastStore.addToast(t('board.writePost.createSeriesFailed'), 'error')
  } finally {
    if (createSeriesAbortController === controller) createSeriesAbortController = null
    if (authStore.sessionGeneration === generation) isCreatingSeries.value = false
  }
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = leaveConfirmMessage.value
  return leaveConfirmMessage.value
}

function resetFormIdentityState() {
  resetEditHydrationState()
  resetFormState()
}

watch(
  formIdentity,
  (_current, previous) => {
    if (previous === undefined) return
    resetFormIdentityState()
  },
)

const { resetEditHydrationState } = usePostFormEditHydration({
  mode: () => props.mode,
  post,
  postId,
  applyDraftSnapshot,
  markCurrentSnapshotSaved,
})

const {
  draftEnabled,
  draftStatusLabel,
  draftId,
  draftConflict,
  isSavingDraft,
  saveDraftNow,
  handleSaveDraft,
  handleReloadServerDraft,
  cleanupPublishedDraft,
  clearScheduledDraftRecovery,
} = usePostComposerDraft({
  isAuthenticated: computed(() => Boolean(authStore.isAuthenticated)),
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
  buildPayload,
  applyDraft: applyDraftSnapshot,
  markCurrentSnapshotSaved,
  t,
  addToast: toastStore.addToast,
})

usePwaReloadBlocker(computed(() => isDirty.value))
usePwaReloadBlocker(
  computed(() => isSubmitting.value || isSavingDraft.value),
  { retainWhileBlockedOnDispose: true },
)

const { handleSubmit, isSubmissionLocked } = usePostComposerSubmit({
  mode: () => props.mode,
  boardUrl,
  postId,
  board,
  form,
  hideCategory: () => props.hideCategory,
  draftEnabled,
  draftConflict,
  draftId,
  saveDraftNow,
  buildPayload,
  markCurrentSnapshotSaved,
  cleanupPublishedDraft,
  clearScheduledDraftRecovery,
  createPost,
  createScheduledPost,
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

    if (props.mode === 'create') {
      const pollError = validatePostFormPoll(form.value.poll, Date.now(), scheduledAt.value)
      if (pollError) {
        toastStore.addToast(t(`board.writePost.poll.validation.${pollError}`, {
          questionMax: POST_POLL_QUESTION_MAX_LENGTH,
          optionMax: POST_POLL_OPTION_MAX_LENGTH,
          min: POST_POLL_MIN_OPTIONS,
          max: POST_POLL_MAX_OPTIONS,
        }), 'error')
        return false
      }
    }

    return true
  },
})

function handleCancel() {
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
  hasUnsavedChanges: () => isFormDirty(),
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
        <PostFormMainSection
          :title="form.title"
          :content="form.content"
          :title-error="postValidation.visibleError('title')"
          :tags="form.tags"
          :poll="form.poll"
          :mode="props.mode"
          :hide-tags="props.hideTags"
          :metadata-panel-props="metadataPanelProps"
          :metadata-panel-handlers="metadataPanelHandlers"
          :editor-view-mode="editorViewMode"
          :editor-view-options="editorViewOptions"
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
          @file-uploaded="trackUploadedFile"
          @open-poll="openPollEditor"
        />

        <PostFormSidePanel
          :metadata-panel-props="metadataPanelProps"
          :metadata-panel-handlers="metadataPanelHandlers"
          :draft-status-label="draftStatusLabel"
          :draft-enabled="draftEnabled"
          :is-saving-draft="isSavingDraft"
          :draft-conflict="draftConflict"
          :scheduled-at="scheduledAt"
          :show-scheduler="props.mode === 'create'"
          @save-draft="handleSaveDraft"
          @reload-server-draft="handleReloadServerDraft"
          @update:scheduled-at="scheduledAt = $event"
        />
      </form>
    </div>

    <div class="nv-compose-mobile-actions nv-elevated-surface sm:hidden">
      <div v-if="draftStatusLabel" class="truncate px-1 text-xs font-medium text-[var(--nv-muted)]">
        {{ draftStatusLabel }}
      </div>
      <BaseButton
        v-if="draftConflict"
        type="button"
        variant="secondary"
        size="sm"
        class="min-h-[36px] w-full"
        @click="handleReloadServerDraft"
      >
        {{ $t('board.writePost.draftStatus.reloadServer') }}
      </BaseButton>
      <div class="flex items-center gap-2">
        <BaseButton type="button" variant="secondary" size="sm" class="min-h-[40px]" @click="handleCancel">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton
          v-if="!props.hidePreview"
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[40px] flex-1"
          @click="showPreview = true"
        >
          {{ $t('board.writePost.actions.preview') }}
        </BaseButton>
        <BaseButton
          v-else-if="draftEnabled"
          type="button"
          variant="secondary"
          size="sm"
          class="min-h-[40px] flex-1"
          :disabled="isSavingDraft || isSubmissionLocked"
          @click="handleSaveDraft"
        >
          {{ isSavingDraft ? $t('board.writePost.draftStatus.saving') : $t('board.writePost.actions.saveDraft') }}
        </BaseButton>
        <BaseButton
          type="button"
          variant="primary"
          size="sm"
          class="min-h-[40px] flex-1"
          :loading="isSubmitting || isSubmissionLocked"
          :disabled="isSavingDraft || isSubmissionLocked"
          @click="handleSubmit"
        >
          {{ scheduledAt ? $t('board.writePost.actions.schedule') : submitLabel }}
        </BaseButton>
      </div>
      <BaseButton
        v-if="!props.hidePreview && draftEnabled"
        type="button"
        variant="secondary"
        size="sm"
        class="mt-2 min-h-[36px] w-full"
        :disabled="isSavingDraft || isSubmissionLocked"
        @click="handleSaveDraft"
      >
        {{ isSavingDraft ? $t('board.writePost.draftStatus.saving') : $t('board.writePost.actions.saveDraft') }}
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


