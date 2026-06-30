<script setup lang="ts">
import { computed, ref, watch, watchEffect } from 'vue'
import { usePostComposerDraft } from '@/composables/usePostComposerDraft'
import { usePostComposerEffects } from '@/composables/usePostComposerEffects'
import { usePostComposerSubmit, type PostFormSubmitResult } from '@/composables/usePostComposerSubmit'
import { usePostEditorViewMode } from '@/composables/usePostEditorViewMode'
import { usePostFormResource } from '@/composables/usePostFormResource'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSegmentedControl, { type SegmentedControlOption } from '@/components/common/ui/BaseSegmentedControl.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useToastStore } from '@/stores/toast'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import PostEditorTipTap from '@/components/board/PostEditorTipTap.vue'
import PostFormMetadataPanel from '@/components/board/PostFormMetadataPanel.vue'
import PostPreviewModal from '@/components/board/PostPreviewModal.vue'
import { sanitizeQuillHtml } from '@/utils/sanitize'
import { canWriteCategory } from '@/utils/board'
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

type CategoryOption = {
  categoryId: number
  name: string
  minWriteRole?: string
  disabled?: boolean
}

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

const hasHydratedEditPost = ref(false)

const filteredCategories = computed<CategoryOption[]>(() => {
  const selectableCategories = categories.value.filter((cat) => canWriteCategory(
    cat,
    authStore.user?.role,
    board.value?.isAdmin ?? false
  ))
  const selectedCategoryId = Number(form.value.categoryId)
  const selectedCategory = categories.value.find((category) => category.categoryId === selectedCategoryId)
    ?? (post.value?.category?.categoryId === selectedCategoryId
      ? {
          categoryId: post.value.category.categoryId,
          name: post.value.category.name,
          minWriteRole: post.value.category.minWriteRole,
        }
      : null)
  if (!selectedCategory) return selectableCategories
  if (selectableCategories.some((category) => category.categoryId === selectedCategory.categoryId)) {
    return selectableCategories
  }
  return [
    {
      ...selectedCategory,
      disabled: true,
    },
    ...selectableCategories,
  ]
})

const pageTitle = computed(() =>
  props.mode === 'create'
    ? (props.createTitleOverride || t('board.writePost.createTitle'))
    : t('board.writePost.editTitle'),
)

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
const previewHtml = computed(() => sanitizeQuillHtml(form.value.content || `<p>${t('board.writePost.preview.emptyContent')}</p>`))
const leaveConfirmMessage = computed(() => t('board.writePost.leaveConfirm'))
const editorViewOptions = computed<SegmentedControlOption[]>(() => [
  { value: 'visual', label: t('board.writePost.visualMode') },
  { value: 'html', label: t('board.writePost.viewHtmlSource') },
])
const metadataPanelProps = computed(() => ({
  categories: filteredCategories.value,
  categoryId: form.value.categoryId,
  tags: form.value.tags,
  isNotice: form.value.isNotice,
  isNsfw: form.value.isNsfw,
  isSpoiler: form.value.isSpoiler,
  isSecret: form.value.isSecret,
  hideCategory: props.hideCategory,
  hideTags: props.hideTags,
  showNotice: showNotice.value,
  canShowNsfw: canShowNsfw.value,
  hideSpoiler: props.hideSpoiler,
  hideSecret: props.hideSecret,
}))
const metadataPanelHandlers = {
  'update:categoryId': (value: string | number) => {
    form.value.categoryId = value
  },
  'update:tags': (value: string[]) => {
    form.value.tags = value
  },
  'update:isNotice': (value: boolean) => {
    form.value.isNotice = value
  },
  'update:isNsfw': (value: boolean) => {
    form.value.isNsfw = value
  },
  'update:isSpoiler': (value: boolean) => {
    form.value.isSpoiler = value
  },
  'update:isSecret': (value: boolean) => {
    form.value.isSecret = value
  },
}

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = leaveConfirmMessage.value
  return leaveConfirmMessage.value
}

function resetFormIdentityState() {
  hasHydratedEditPost.value = false
  resetFormState()
}

watch(
  formIdentity,
  (_current, previous) => {
    if (previous === undefined) return
    resetFormIdentityState()
  },
)

watchEffect(() => {
  if (props.mode !== 'edit' || !post.value || hasHydratedEditPost.value) return
  if (String(post.value.postId) !== String(postId.value)) return
  hasHydratedEditPost.value = true
  applyDraftSnapshot({
    title: post.value.title,
    contents: post.value.contents,
    categoryId: post.value.category?.categoryId,
    tags: post.value.tags?.map((tag: { name?: string } | string) => typeof tag === 'string' ? tag : (tag.name ?? '')) ?? [],
    isNsfw: post.value.isNsfw,
    isSpoiler: post.value.isSpoiler,
    isSecret: post.value.isSecret ?? false,
    isNotice: false,
    fileIds: [],
  })
  markCurrentSnapshotSaved()
})

const firstCategoryId = computed(() => filteredCategories.value[0]?.categoryId)
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
      <div class="nv-compose-header">
        <div class="min-w-0">
          <h2 class="truncate text-2xl font-semibold tracking-[-0.05em] text-[var(--nv-ink)] sm:text-3xl">
            {{ pageTitle }}
          </h2>
          <p v-if="!props.hideBoardLabel" class="mt-2 text-sm text-[var(--nv-ink-soft)]">
            {{ board?.boardName || boardUrl }}
          </p>
        </div>

        <div class="flex flex-wrap items-center justify-end gap-2">
          <BaseButton type="button" variant="secondary" size="sm" @click="handleCancel">
            {{ $t('common.cancel') }}
          </BaseButton>
          <BaseButton v-if="!props.hidePreview" type="button" variant="secondary" size="sm" @click="showPreview = true">
            {{ $t('board.writePost.actions.preview') }}
          </BaseButton>
          <BaseButton
            v-if="draftEnabled"
            type="button"
            variant="secondary"
            size="sm"
            :disabled="isSavingDraft"
            @click="handleSaveDraft"
          >
            {{ isSavingDraft ? $t('board.writePost.draftStatus.saving') : $t('board.writePost.actions.saveDraft') }}
          </BaseButton>
          <BaseButton type="button" variant="primary" size="sm" :loading="isSubmitting" @click="handleSubmit">
            {{ submitLabel }}
          </BaseButton>
        </div>
      </div>

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

            <div class="mt-4">
              <div class="flex items-center justify-between rounded-t-xl border border-[var(--nv-line)] border-b-0 bg-[var(--nv-elevated)] px-3 py-2">
                <div class="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                  <span>{{ $t('board.writePost.sections.editor') }}</span>
                </div>
                <BaseSegmentedControl
                  :model-value="editorViewMode"
                  :options="editorViewOptions"
                  :label="$t('board.writePost.sections.editor')"
                  variant="pill"
                  @update:model-value="handleEditorViewModeChange"
                />
              </div>

              <div class="editor-area-container rounded-b-xl border border-[var(--nv-line)]">
                <div
                  v-if="editorViewMode === 'visual'"
                  ref="editorWrapperRef"
                  class="tiptap-editor-wrapper"
                >
                  <PostEditorTipTap
                    ref="tiptapEditorRef"
                    v-model="form.content"
                    @open-video="openVideoPopover"
                    @open-emoticon="showEmoticonPicker = true"
                    @file-uploaded="trackUploadedFile"
                  />
                  <Teleport to="body">
                    <div v-if="showVideoPopover" class="video-url-popover-mask" @click.self="closeVideoPopover" @keydown.enter.stop @keydown.escape.stop.prevent="closeVideoPopover">
                      <div
                        ref="videoPopoverRef"
                        class="video-url-popover"
                        :style="{ top: videoPopoverStyle.top, left: videoPopoverStyle.left }"
                        role="dialog"
                        aria-modal="true"
                        aria-labelledby="post-video-url-label"
                      >
                        <label id="post-video-url-label" for="post-video-url-input" class="video-url-popover-label">{{ $t('board.writePost.video.inputLabel') }}</label>
                        <input
                          id="post-video-url-input"
                          v-model="videoUrl"
                          type="url"
                          class="video-url-popover-input"
                          :placeholder="$t('board.writePost.video.placeholder')"
                          aria-describedby="post-video-url-help"
                          @keydown.enter.stop.prevent="insertVideoFromPopover"
                          @keydown.escape.stop.prevent="closeVideoPopover"
                        >
                        <p id="post-video-url-help" class="video-url-popover-help">{{ $t('board.writePost.video.help') }}</p>
                        <div class="video-url-popover-actions">
                          <BaseButton type="button" variant="secondary" size="sm" @click="closeVideoPopover">
                            {{ $t('common.cancel') }}
                          </BaseButton>
                          <BaseButton type="button" variant="primary" size="sm" @click="insertVideoFromPopover">
                            {{ $t('common.confirm') }}
                          </BaseButton>
                        </div>
                      </div>
                    </div>
                  </Teleport>
                  <EmoticonPicker :show="showEmoticonPicker" @select="handleEmoticonSelect" @close="showEmoticonPicker = false" />
                </div>

                <div v-else class="html-source-editor-wrap">
                  <textarea
                    id="content"
                    v-model="form.content"
                    class="html-source-textarea"
                    :placeholder="$t('board.writePost.htmlSourcePlaceholder')"
                    spellcheck="false"
                  />
                </div>
              </div>
            </div>

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

          <section class="nv-compose-side-card rounded-2xl border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
            <div class="mb-3">
              <p class="nv-compose-kicker">{{ $t('board.writePost.sections.draftState') }}</p>
            </div>
            <p class="text-sm text-[var(--nv-ink-soft)]">{{ draftStatusLabel }}</p>
          </section>
        </aside>
      </form>
    </div>

    <PostPreviewModal
      v-if="!props.hidePreview"
      :is-open="showPreview"
      :board-label="board?.boardName || boardUrl"
      :post-title="form.title"
      :tags="form.tags"
      :html="previewHtml"
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

.nv-compose-header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
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

.editor-area-container {
  background: var(--nv-surface);
  overflow: hidden;
}

.tiptap-editor-wrapper {
  display: flex;
  height: 26rem;
  min-height: 26rem;
  flex-direction: column;
  overflow: hidden;
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

<style>
.video-url-popover-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: transparent;
}

.video-url-popover {
  position: fixed;
  transform: translateX(-50%);
  min-width: 320px;
  max-width: 90vw;
  padding: 12px 14px;
  background: var(--nv-surface);
  border: 1px solid var(--nv-line);
  border-radius: 10px;
  box-shadow: var(--nv-shadow-soft);
  z-index: 10000;
}

.video-url-popover-label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--nv-ink-soft);
}

.video-url-popover-input {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  padding: 10px 12px;
  border: 1px solid var(--nv-line);
  border-radius: 8px;
  background: var(--nv-elevated);
  color: var(--nv-ink);
  box-sizing: border-box;
}

.video-url-popover-help {
  margin: 0 0 10px;
  color: var(--nv-muted);
  font-size: 12px;
}

.video-url-popover-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.html-source-editor-wrap {
  height: 26rem;
  overflow: hidden;
}

.html-source-textarea {
  display: block;
  width: 100%;
  height: 100%;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--nv-ink);
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-word;
  box-sizing: border-box;
}
</style>


