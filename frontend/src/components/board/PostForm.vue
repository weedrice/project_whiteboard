<script setup lang="ts">
import { computed, watch, type ComponentPublicInstance } from 'vue'
import { usePostComposerDraft } from '@/composables/usePostComposerDraft'
import { usePostComposerEffects, type ComposerEditor } from '@/composables/usePostComposerEffects'
import { usePostComposerSubmit, type PostFormSubmitResult } from '@/composables/usePostComposerSubmit'
import { usePostEditorViewMode } from '@/composables/usePostEditorViewMode'
import { usePostFormEditHydration } from '@/composables/usePostFormEditHydration'
import { usePostFormCategoryOptions } from '@/composables/usePostFormCategoryOptions'
import { usePostFormMetadataBindings } from '@/composables/usePostFormMetadataBindings'
import { usePostFormResource } from '@/composables/usePostFormResource'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import type { SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useToastStore } from '@/stores/toast'
import PostFormHeader from '@/components/board/PostFormHeader.vue'
import PostDraftStatusPanel from '@/components/board/PostDraftStatusPanel.vue'
import PostFormEditorSection from '@/components/board/PostFormEditorSection.vue'
import PostFormMetadataPanel from '@/components/board/PostFormMetadataPanel.vue'
import PostPreviewModal from '@/components/board/PostPreviewModal.vue'
import { requiresSandboxedPostHtml } from '@/utils/postHtmlSandbox'
import { usePostComposerState } from '@/composables/usePostComposerState'

const props = defineProps<{
  mode: 'create' | 'edit'
  boardUrl?: string
  postId?: string | number
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

const boardUrl = computed(() => props.boardUrl ?? '')
const postId = computed(() => props.postId ?? '')
const formIdentity = computed(() => `${props.mode}:${boardUrl.value || 'unknown'}:${postId.value || 'new'}`)

const {
  board,
  categories,
  post,
  isLoading,
  isSubmitting,
  showNotice,
  canShowNsfw,
  createPost,
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
  showNotice,
  canShowNsfw,
  hideCategory: () => props.hideCategory,
  hideTags: () => props.hideTags,
  hideSpoiler: () => props.hideSpoiler,
  hideSecret: () => props.hideSecret,
})

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
  isSavingDraft,
  saveDraftNow,
  handleSaveDraft,
  cleanupPublishedDraft,
} = usePostComposerDraft({
  isAuthenticated: computed(() => Boolean(authStore.isAuthenticated)),
  userId: computed(() => authStore.user?.userId),
  identity: formIdentity,
  mode: () => props.mode,
  boardUrl,
  postId,
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

const { handleSubmit } = usePostComposerSubmit({
  mode: () => props.mode,
  boardUrl,
  postId,
  board,
  form,
  hideCategory: () => props.hideCategory,
  draftEnabled,
  draftId,
  saveDraftNow,
  buildPayload,
  markCurrentSnapshotSaved,
  cleanupPublishedDraft,
  createPost,
  updatePost,
  onSubmitted: () => props.onSubmitted,
  createSuccessToastMessage: () => props.createSuccessToastMessage,
  t,
  addToast: toastStore.addToast,
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
  <div class="w-full max-w-full overflow-x-hidden">
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
        :draft-enabled="draftEnabled"
        :is-saving-draft="isSavingDraft"
        :is-submitting="isSubmitting"
        :submit-label="submitLabel"
        @cancel="handleCancel"
        @preview="showPreview = true"
        @save-draft="handleSaveDraft"
        @submit="handleSubmit"
      />

      <div v-if="isLoading" class="py-10 text-center">
        <BaseSpinner size="lg" />
      </div>

      <form
        v-else
        class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_18.5rem]"
        @submit.prevent="handleSubmit"
      >
        <section class="nv-compose-main">
          <div class="nv-compose-main-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)] sm:p-5">
            <PostFormMetadataPanel
              layout="mobile"
              v-bind="metadataPanelProps"
              v-on="metadataPanelHandlers"
            />

            <BaseInput
              id="title"
              v-model="form.title"
              name="title"
              type="text"
              required
              :placeholder="$t('board.writePost.placeholder.title')"
              :label="$t('common.title')"
              labelClass="!text-xs !font-medium !uppercase !tracking-[0.18em] !text-[var(--nv-muted)]"
              inputClass="!rounded-xl !border-[var(--nv-line)] !bg-[var(--nv-elevated)] !px-4 !py-3 !text-sm sm:!text-base"
            />

            <PostFormEditorSection
              v-model="form.content"
              :editor-view-mode="editorViewMode"
              :editor-view-options="editorViewOptions"
              :show-video-popover="showVideoPopover"
              :show-emoticon-picker="showEmoticonPicker"
              :video-url="videoUrl"
              :video-popover-style="videoPopoverStyle"
              :assign-tiptap-editor="assignTiptapEditor"
              :assign-editor-wrapper="assignEditorWrapper"
              :assign-video-popover="assignVideoPopover"
              @update:editor-view-mode="handleEditorViewModeChange"
              @update:show-emoticon-picker="showEmoticonPicker = $event"
              @update:video-url="videoUrl = $event"
              @open-video="openVideoPopover"
              @close-video="closeVideoPopover"
              @insert-video="insertVideoFromPopover"
              @select-emoticon="handleEmoticonSelect"
              @file-uploaded="trackUploadedFile"
            />

            <div v-if="!props.hideTags" class="mt-5 lg:hidden">
              <label for="post-tags-input-mobile" class="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                {{ $t('common.tags') }}
              </label>
              <PostTags v-model="form.tags" input-id="post-tags-input-mobile" />
            </div>
          </div>
        </section>

        <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <section class="nv-compose-side-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
            <div class="mb-4">
              <p class="nv-compose-kicker">{{ $t('board.writePost.sections.metadata') }}</p>
              <h3 class="text-lg font-semibold text-[var(--nv-ink)]">{{ $t('board.writePost.sections.postSettings') }}</h3>
            </div>

            <PostFormMetadataPanel
              layout="desktop"
              v-bind="metadataPanelProps"
              v-on="metadataPanelHandlers"
            />
          </section>

          <PostDraftStatusPanel :label="draftStatusLabel" />
        </aside>
      </form>
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

.nv-compose-kicker {
  color: var(--nv-muted);
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.nv-compose-main {
  min-width: 0;
}

.nv-compose-main-card {
  position: relative;
}

.nv-compose-side-card {
  background: color-mix(in srgb, var(--nv-surface) 94%, transparent);
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
</style>


