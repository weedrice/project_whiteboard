<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watchEffect } from 'vue'
import { usePopoverFocus } from '@/composables/usePopoverFocus'
import { usePostComposerDraft } from '@/composables/usePostComposerDraft'
import { usePostFormResource } from '@/composables/usePostFormResource'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useToastStore } from '@/stores/toast'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import PostEditorTipTap from '@/components/board/PostEditorTipTap.vue'
import { sanitizeQuillHtml } from '@/utils/sanitize'
import { canWriteCategory } from '@/utils/board'
import logger from '@/utils/logger'
import { usePostComposerState } from '@/composables/usePostComposerState'
import { toEmbedPostVideoUrl } from '@/utils/postForm'

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
}>()

const emit = defineEmits<{
  cancel: []
}>()

type PostFormSubmitResult = {
  mode: 'create' | 'edit'
  boardUrl: string
  postId?: string | number
  newPostId?: string | number
  isSecret: boolean
  isBoardAdmin: boolean
}

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

const tiptapEditorRef = ref<InstanceType<typeof PostEditorTipTap> | null>(null)
const editorWrapperRef = ref<HTMLElement | null>(null)
const composePageRef = ref<HTMLElement | null>(null)
const videoPopoverRef = ref<HTMLElement | null>(null)

const showPreview = ref(false)
const showEmoticonPicker = ref(false)
const showVideoPopover = ref(false)
const videoUrl = ref('')
const videoPopoverStyle = ref<{ top: string; left: string }>({ top: '0', left: '0' })
const editorViewMode = ref<'visual' | 'html'>('visual')
const hasHydratedEditPost = ref(false)
const isEditorFocusWithin = ref(false)

usePopoverFocus(videoPopoverRef, showVideoPopover)

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

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = leaveConfirmMessage.value
  return leaveConfirmMessage.value
}

watchEffect(() => {
  if (props.mode !== 'edit' || !post.value || hasHydratedEditPost.value) return
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

function isMobileView(): boolean {
  if (typeof window === 'undefined') return false
  return window.matchMedia('(max-width: 767px)').matches
}

function openVideoPopover() {
  if (typeof window === 'undefined') {
    videoPopoverStyle.value = { top: '300px', left: '400px' }
  } else if (!isMobileView() && editorWrapperRef.value) {
    const toolbar = editorWrapperRef.value.querySelector('.tiptap-toolbar')
    if (toolbar) {
      const rect = toolbar.getBoundingClientRect()
      videoPopoverStyle.value = {
        top: `${rect.bottom + 8}px`,
        left: `${rect.left + rect.width / 2}px`,
      }
    } else {
      const rect = editorWrapperRef.value.getBoundingClientRect()
      videoPopoverStyle.value = {
        top: `${rect.top + 60}px`,
        left: `${rect.left + rect.width / 2}px`,
      }
    }
  } else {
    videoPopoverStyle.value = {
      top: `${window.innerHeight / 2}px`,
      left: `${window.innerWidth / 2}px`,
    }
  }
  videoUrl.value = ''
  showVideoPopover.value = true
}

function closeVideoPopover() {
  showVideoPopover.value = false
  videoUrl.value = ''
}

function insertVideoFromPopover() {
  const rawVideoUrl = videoUrl.value.trim()
  if (!rawVideoUrl) {
    toastStore.addToast(t('board.writePost.videoUrlRequired'), 'error')
    return
  }

  const embedUrl = toEmbedPostVideoUrl(rawVideoUrl)
  if (!embedUrl) {
    toastStore.addToast(t('board.writePost.invalidVideoUrl'), 'error')
    return
  }
  tiptapEditorRef.value?.setVideo(embedUrl)
  closeVideoPopover()
}

function handleEmoticonSelect(image: import('@/types/emoticon').EmoticonImage) {
  tiptapEditorRef.value?.setEmoticon(image)
  showEmoticonPicker.value = false
}

function syncEditorFocus(value: boolean) {
  if (typeof window === 'undefined') return
  window.dispatchEvent(new CustomEvent('noviis:editor-focus-change', { detail: value }))
}

let focusOutTimerId: number | undefined

function clearFocusOutTimer() {
  if (focusOutTimerId === undefined || typeof window === 'undefined') return
  window.clearTimeout(focusOutTimerId)
  focusOutTimerId = undefined
}

function handleFocusIn() {
  clearFocusOutTimer()
  isEditorFocusWithin.value = true
  syncEditorFocus(true)
}

function handleFocusOut() {
  clearFocusOutTimer()
  if (typeof window === 'undefined') return
  focusOutTimerId = window.setTimeout(() => {
    focusOutTimerId = undefined
    if (!composePageRef.value?.contains(document.activeElement)) {
      isEditorFocusWithin.value = false
      syncEditorFocus(false)
    }
  }, 0)
}

function navigateAfterCreate(newPostId: string | number, payload: ReturnType<typeof buildPayload>) {
  if (props.onSubmitted) {
    props.onSubmitted({
      mode: 'create',
      boardUrl: boardUrl.value,
      newPostId,
      isSecret: payload.isSecret,
      isBoardAdmin: board.value?.isAdmin ?? false,
    })
  }
}

async function handleSubmit() {
  if (!form.value.title) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }
  if (props.mode === 'create' && !props.hideCategory && !form.value.categoryId) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }

  let currentDraftId = draftId.value ?? undefined
  if (draftEnabled.value) {
    try {
      const savedDraft = await saveDraftNow()
      if (savedDraft?.draftId != null) {
        currentDraftId = savedDraft.draftId
      }
    } catch (error) {
      logger.error('Failed to save draft before submit:', error)
      toastStore.addToast(t('common.error.unknown'), 'error')
      return
    }
  }
  const payload = {
    ...buildPayload(),
    ...(currentDraftId !== undefined && { draftId: currentDraftId }),
  }

  if (props.mode === 'create') {
    createPost({ boardUrl: boardUrl.value, data: payload }, {
      onSuccess: (response) => {
        markCurrentSnapshotSaved()
        cleanupPublishedDraft()
        if (props.createSuccessToastMessage) {
          toastStore.addToast(props.createSuccessToastMessage, 'success')
        }
        navigateAfterCreate(response.data.data, payload)
      },
      onError: (error) => {
        logger.error('Failed to create post:', error)
      },
    })
    return
  }

  updatePost({ postId: postId.value, data: payload }, {
    onSuccess: () => {
      markCurrentSnapshotSaved()
      cleanupPublishedDraft()
      if (props.onSubmitted) {
        props.onSubmitted({
          mode: 'edit',
          boardUrl: boardUrl.value,
          postId: postId.value,
          isSecret: payload.isSecret,
          isBoardAdmin: board.value?.isAdmin ?? false,
        })
      }
    },
    onError: (error) => {
      logger.error('Failed to update post:', error)
    },
  })
}

function handleCancel() {
  emit('cancel')
}

function handleKeyDown(event: KeyboardEvent) {
  const { key, ctrlKey, metaKey } = event
  if ((ctrlKey || metaKey) && key === 'Enter') {
    event.preventDefault()
    handleSubmit()
    return
  }
  if ((ctrlKey || metaKey) && (key === 's' || key === 'S')) {
    event.preventDefault()
    void handleSaveDraft()
    return
  }
  if (key === 'Escape') {
    if (showVideoPopover.value) {
      event.preventDefault()
      closeVideoPopover()
      return
    }
    if (showEmoticonPicker.value) {
      event.preventDefault()
      showEmoticonPicker.value = false
      return
    }
    if (showPreview.value) {
      event.preventDefault()
      showPreview.value = false
      return
    }
    event.preventDefault()
    handleCancel()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
  window.addEventListener('beforeunload', onBeforeUnload)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('beforeunload', onBeforeUnload)
  clearFocusOutTimer()
  syncEditorFocus(false)
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
          <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
            {{ board?.boardName || boardUrl }}
          </p>
        </div>

        <div class="flex flex-wrap items-center justify-end gap-2">
          <BaseButton type="button" variant="secondary" size="sm" @click="handleCancel">
            {{ $t('common.cancel') }}
          </BaseButton>
          <BaseButton type="button" variant="secondary" size="sm" @click="showPreview = true">
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
            <div class="mb-4 flex flex-wrap items-center gap-2 lg:hidden">
              <div v-if="!props.hideCategory && filteredCategories.length > 0" class="min-w-[10rem] flex-1">
                <BaseSelect id="category-mobile" v-model="form.categoryId" :label="$t('common.category')">
                  <option value="" disabled>{{ $t('board.writePost.selectCategory') }}</option>
                  <option
                    v-for="cat in filteredCategories"
                    :key="cat.categoryId"
                    :value="cat.categoryId"
                    :disabled="cat.disabled"
                  >
                    {{ cat.name }}
                  </option>
                </BaseSelect>
              </div>
              <div class="flex flex-wrap gap-2">
                <BaseCheckbox v-if="showNotice" id="isNotice-m" v-model="form.isNotice" :label="$t('common.notice')" />
                <BaseCheckbox v-if="canShowNsfw" id="nsfw-m" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')" />
                <BaseCheckbox v-if="!props.hideSpoiler" id="spoiler-m" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')" />
                <BaseCheckbox v-if="!props.hideSecret" id="secret-m" v-model="form.isSecret" :label="$t('board.writePost.secret')" />
              </div>
            </div>

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
                <div class="flex items-center gap-2">
                  <button
                    type="button"
                    class="editor-view-toggle-btn"
                    :class="{ active: editorViewMode === 'visual' }"
                    @click="editorViewMode = 'visual'"
                  >
                    {{ $t('board.writePost.visualMode') }}
                  </button>
                  <button
                    type="button"
                    class="editor-view-toggle-btn"
                    :class="{ active: editorViewMode === 'html' }"
                    @click="editorViewMode = 'html'"
                  >
                    {{ $t('board.writePost.viewHtmlSource') }}
                  </button>
                </div>
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

            <div v-if="!props.hideCategory && filteredCategories.length > 0" class="nv-compose-side-section mb-4 hidden lg:block">
              <BaseSelect id="category" v-model="form.categoryId" :label="$t('common.category')">
                <option value="" disabled>{{ $t('board.writePost.selectCategory') }}</option>
                <option
                  v-for="cat in filteredCategories"
                  :key="cat.categoryId"
                  :value="cat.categoryId"
                  :disabled="cat.disabled"
                >
                  {{ cat.name }}
                </option>
              </BaseSelect>
            </div>

            <div v-if="!props.hideTags" class="nv-compose-side-section mb-4 hidden lg:block">
              <label for="post-tags-input-desktop" class="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                {{ $t('common.tags') }}
              </label>
              <PostTags v-model="form.tags" input-id="post-tags-input-desktop" />
            </div>

            <div class="nv-compose-side-section space-y-3">
              <BaseCheckbox v-if="showNotice" id="isNotice" v-model="form.isNotice" :label="$t('common.notice')" :description="$t('board.writePost.noticeDesc')" />
              <BaseCheckbox v-if="canShowNsfw" id="nsfw" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')" :description="$t('board.writePost.nsfwDesc')" />
              <BaseCheckbox v-if="!props.hideSpoiler" id="spoiler" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')" :description="$t('board.writePost.spoilerDesc')" />
              <BaseCheckbox v-if="!props.hideSecret" id="secret" v-model="form.isSecret" :label="$t('board.writePost.secret')" :description="$t('board.writePost.secretDesc')" />
            </div>
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

    <BaseModal :is-open="showPreview" :title="$t('board.writePost.preview.title')" size="2xl" mobile-fit-content @close="showPreview = false">
      <div class="space-y-4">
        <div>
          <p class="text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">{{ board?.boardName || boardUrl }}</p>
          <h3 class="mt-2 text-2xl font-semibold text-[var(--nv-ink)]">{{ form.title || $t('board.writePost.preview.untitledPost') }}</h3>
        </div>
        <div v-if="!props.hideTags && form.tags.length" class="flex flex-wrap gap-2">
          <span
            v-for="tag in form.tags"
            :key="tag"
            class="rounded-full border border-[var(--nv-line)] px-3 py-1 text-xs text-[var(--nv-ink-soft)]"
          >
            #{{ tag }}
          </span>
        </div>
        <article class="nv-rich-content prose max-w-none dark:prose-invert" v-html="previewHtml" />
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <BaseButton type="button" variant="secondary" size="sm" @click="showPreview = false">
            {{ $t('common.close') }}
          </BaseButton>
        </div>
      </template>
    </BaseModal>
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

.editor-view-toggle-btn {
  border-radius: 10px;
  border: 1px solid transparent;
  padding: 0.45rem 0.85rem;
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--nv-muted);
  transition: all 0.15s ease;
}

.editor-view-toggle-btn:hover {
  border-color: var(--nv-line);
  background: var(--nv-surface-alt);
  color: var(--nv-ink);
}

.editor-view-toggle-btn.active {
  border-color: color-mix(in srgb, var(--nv-accent) 35%, transparent);
  background: color-mix(in srgb, var(--nv-accent) 14%, var(--nv-surface));
  color: var(--nv-accent);
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


