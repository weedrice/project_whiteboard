<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import { usePostDraft } from '@/composables/usePostDraft'
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
import logger from '@/utils/logger'

const props = defineProps<{
  mode: 'create' | 'edit'
  boardUrl?: string
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

type CategoryOption = {
  categoryId: number
  name: string
  minWriteRole?: string
  disabled?: boolean
}

type FormState = {
  title: string
  content: string
  categoryId: string | number
  tags: string[]
  isNsfw: boolean
  isSpoiler: boolean
  isNotice: boolean
  isSecret: boolean
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const boardUrl = computed(() => props.boardUrl ?? (route.params.boardUrl as string))
const postId = computed(() => route.params.postId as string)

const { useBoardDetail, useBoardCategories } = useBoard()
const {
  usePostDetail,
  useCreatePost,
  useUpdatePost,
} = usePost()

const queryEnabled = computed(() => !!boardUrl.value && !props.skipBoardLookup)
const { data: board, isLoading: isBoardLoading } = useBoardDetail(boardUrl, {
  enabled: queryEnabled,
})
const { data: categories, isLoading: isCategoriesLoading } = useBoardCategories(boardUrl, {
  enabled: queryEnabled,
})
const postIdRef = computed(() => (props.mode === 'edit' ? postId.value : '') as string)
const { data: post, isLoading: isPostLoading } = usePostDetail(postIdRef, {
  enabled: computed(() => props.mode === 'edit' && !!postId.value),
})
const { mutate: createPost, isPending: isCreateSubmitting } = useCreatePost()
const { mutate: updatePost, isPending: isUpdateSubmitting } = useUpdatePost()

const tiptapEditorRef = ref<InstanceType<typeof PostEditorTipTap> | null>(null)
const editorWrapperRef = ref<HTMLElement | null>(null)
const composePageRef = ref<HTMLElement | null>(null)
const draftFileIds = ref<number[]>([])

const showPreview = ref(false)
const showEmoticonPicker = ref(false)
const showVideoPopover = ref(false)
const videoUrl = ref('')
const videoPopoverStyle = ref<{ top: string; left: string }>({ top: '0', left: '0' })
const editorViewMode = ref<'visual' | 'html'>('visual')
const hasRestoredDraft = ref(false)
const hasHydratedEditPost = ref(false)
const isEditorFocusWithin = ref(false)

const form = ref<FormState>({
  title: '',
  content: '',
  categoryId: '',
  tags: [],
  isNsfw: false,
  isSpoiler: false,
  isNotice: false,
  isSecret: false,
})

const initialFormSnapshot = ref<FormState | null>(null)

const isSubmitting = computed(() => isCreateSubmitting.value || isUpdateSubmitting.value)
const isLoading = computed(() =>
  isBoardLoading.value || isCategoriesLoading.value || (props.mode === 'edit' && isPostLoading.value),
)

function copyFormSnapshot(src: FormState): FormState {
  return {
    title: src.title,
    content: src.content,
    categoryId: src.categoryId,
    tags: [...src.tags],
    isNsfw: src.isNsfw,
    isSpoiler: src.isSpoiler,
    isNotice: src.isNotice,
    isSecret: src.isSecret,
  }
}

function markCurrentSnapshotSaved() {
  initialFormSnapshot.value = copyFormSnapshot(form.value)
}

function canWriteCategory(minWriteRole?: string) {
  const userRole = authStore.user?.role || 'USER'
  const isBoardAdmin = board.value?.isAdmin || false
  const normalizedRole = minWriteRole || 'USER'

  switch (normalizedRole) {
    case 'USER':
      return true
    case 'BOARD_ADMIN':
      return userRole === 'SUPER_ADMIN' || isBoardAdmin
    case 'SUPER_ADMIN':
      return userRole === 'SUPER_ADMIN'
    default:
      return false
  }
}

const filteredCategories = computed<CategoryOption[]>(() => {
  if (!categories.value) return []
  const selectableCategories = categories.value.filter((cat) => canWriteCategory(cat.minWriteRole))
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

const showNotice = computed(() => !props.hideNotice && props.mode === 'create' && board.value?.isAdmin)
const canShowNsfw = computed(() => Boolean(board.value?.allowNsfw))
const draftEnabled = computed(() => authStore.isAuthenticated && !!boardUrl.value)
const draftStorageKey = computed(() => `noviis:draft:${authStore.user?.userId ?? 'guest'}:${props.mode}:${boardUrl.value || 'unknown'}:${postId.value || 'new'}`)
const previewHtml = computed(() => sanitizeQuillHtml(form.value.content || `<p>${t('board.writePost.preview.emptyContent')}</p>`))

function isFormDirty(): boolean {
  const init = initialFormSnapshot.value
  if (!init) return false
  const current = form.value
  if (
    current.title !== init.title
    || current.content !== init.content
    || current.isNsfw !== init.isNsfw
    || current.isSpoiler !== init.isSpoiler
    || current.isNotice !== init.isNotice
    || current.isSecret !== init.isSecret
  ) {
    return true
  }
  if (String(current.categoryId) !== String(init.categoryId)) return true
  if (current.tags.length !== init.tags.length) return true
  return current.tags.some((tag, index) => tag !== init.tags[index])
}

const isDirty = computed(() => isFormDirty())
const leaveConfirmMessage = computed(() => t('board.writePost.leaveConfirm'))

function onBeforeUnload(event: BeforeUnloadEvent) {
  if (!isDirty.value) return
  event.preventDefault()
  event.returnValue = leaveConfirmMessage.value
  return leaveConfirmMessage.value
}

function applyDraftSnapshot(draft: {
  title?: string
  contents?: string
  categoryId?: number | null
  tags?: string[]
  isNsfw?: boolean
  isSpoiler?: boolean
  isNotice?: boolean
  isSecret?: boolean
  fileIds?: number[]
}) {
  form.value = {
    title: draft.title ?? '',
    content: draft.contents ?? '',
    categoryId: draft.categoryId ?? '',
    tags: [...(draft.tags ?? [])],
    isNsfw: Boolean(draft.isNsfw),
    isSpoiler: Boolean(draft.isSpoiler),
    isNotice: Boolean(draft.isNotice),
    isSecret: Boolean(draft.isSecret),
  }
  draftFileIds.value = [...(draft.fileIds ?? [])]
}

const buildPayload = () => {
  const editorFileIds = (tiptapEditorRef.value as { fileIds?: { value?: number[] } | number[] } | null)?.fileIds
  const sessionFileIds = Array.isArray(editorFileIds)
    ? editorFileIds
    : editorFileIds?.value ?? []
  const fileIds = Array.from(new Set([...draftFileIds.value, ...sessionFileIds]))
  const parsedCategoryId = typeof form.value.categoryId === 'string'
    ? parseInt(form.value.categoryId, 10)
    : form.value.categoryId
  const categoryId = props.hideCategory || Number.isNaN(parsedCategoryId) || !parsedCategoryId
    ? undefined
    : parsedCategoryId

  return {
    title: form.value.title,
    ...(categoryId !== undefined && { categoryId }),
    tags: props.hideTags ? [] : form.value.tags,
    contents: form.value.content,
    isNsfw: canShowNsfw.value ? form.value.isNsfw : false,
    isSpoiler: props.hideSpoiler ? false : form.value.isSpoiler,
    isSecret: props.hideSecret ? false : form.value.isSecret,
    ...(props.mode === 'create' && { isNotice: showNotice.value ? form.value.isNotice : false }),
    fileIds,
  }
}

function trackUploadedFile(fileId: number) {
  if (!draftFileIds.value.includes(fileId)) {
    draftFileIds.value.push(fileId)
  }
}

const {
  saveNow: saveDraftNow,
  scheduleAutosave,
  restoreDraft,
  cleanupDraft,
  clearRecovery,
  writeLocalSnapshot,
  lastSavedAt,
  isSavingDraft,
  restoreSource,
} = usePostDraft({
  enabled: draftEnabled,
  storageKey: draftStorageKey,
  buildPayload: () => ({
    ...buildPayload(),
    boardUrl: boardUrl.value,
    originalPostId: props.mode === 'edit' ? Number(postId.value) : undefined,
  }),
  applyDraft: applyDraftSnapshot,
})

const draftStatusLabel = computed(() => {
  if (!draftEnabled.value) return ''
  if (isSavingDraft.value) return t('board.writePost.draftStatus.saving')
  if (lastSavedAt.value) {
    return t('board.writePost.draftStatus.savedAt', {
      time: new Date(lastSavedAt.value).toLocaleTimeString(),
    })
  }
  return t('board.writePost.draftStatus.ready')
})

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

watch(
  isLoading,
  async (loading) => {
    if (loading || hasRestoredDraft.value) return
    hasRestoredDraft.value = true

    if (props.mode === 'create' && !form.value.categoryId && filteredCategories.value.length > 0) {
      form.value.categoryId = filteredCategories.value[0].categoryId
    }

    await restoreDraft()
    const restoredDraftSource = restoreSource.value
    if (restoredDraftSource !== 'idle') {
      toastStore.addToast(
        restoredDraftSource === 'local'
          ? t('board.writePost.draftStatus.restoredLocal')
          : t('board.writePost.draftStatus.restoredServer'),
        'info',
      )
    }
    markCurrentSnapshotSaved()
  },
  { immediate: true },
)

const draftSignature = computed(() => JSON.stringify({
  ...buildPayload(),
  boardUrl: boardUrl.value,
  originalPostId: props.mode === 'edit' ? Number(postId.value) : undefined,
}))

watch(
  draftSignature,
  () => {
    if (!hasRestoredDraft.value || !draftEnabled.value || isLoading.value) return
    writeLocalSnapshot()
    scheduleAutosave()
  },
  { flush: 'post' },
)

function toEmbedVideoUrl(url: string): string {
  const trimmed = (url || '').trim()
  if (!trimmed) return ''
  const youtubeMatch = trimmed.match(/^(?:(https?):\/\/)?(?:(?:www|m)\.)?youtube\.com\/watch.*v=([a-zA-Z0-9_-]+)/)
    || trimmed.match(/^(?:(https?):\/\/)?(?:(?:www|m)\.)?youtu\.be\/([a-zA-Z0-9_-]+)/)
  if (youtubeMatch) {
    return `${youtubeMatch[1] || 'https'}://www.youtube.com/embed/${youtubeMatch[2]}?showinfo=0`
  }
  const vimeoMatch = trimmed.match(/^(?:(https?):\/\/)?(?:www\.)?vimeo\.com\/(\d+)/)
  if (vimeoMatch) {
    return `${vimeoMatch[1] || 'https'}://player.vimeo.com/video/${vimeoMatch[2]}/`
  }
  return trimmed
}

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
  const embedUrl = toEmbedVideoUrl(videoUrl.value)
  if (!embedUrl) {
    toastStore.addToast(t('board.writePost.videoUrlRequired'), 'error')
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

function handleFocusIn() {
  isEditorFocusWithin.value = true
  syncEditorFocus(true)
}

function handleFocusOut() {
  setTimeout(() => {
    if (!composePageRef.value?.contains(document.activeElement)) {
      isEditorFocusWithin.value = false
      syncEditorFocus(false)
    }
  }, 0)
}

async function handleSaveDraft() {
  try {
    const savedDraft = await saveDraftNow()
    if (savedDraft) {
      markCurrentSnapshotSaved()
      toastStore.addToast(t('board.writePost.draftStatus.saved'), 'success')
    }
  } catch (error) {
    logger.error('Failed to save draft:', error)
  }
}

function cleanupPublishedDraft() {
  void cleanupDraft().catch((error) => {
    logger.error('Failed to clean up published draft:', error)
    toastStore.addToast(t('board.writePost.draftStatus.cleanupFailed'), 'error')
  })
}

function navigateAfterCreate(newPostId: string | number, payload: ReturnType<typeof buildPayload>) {
  if (props.goBackOnCreate) {
    if (typeof window !== 'undefined' && window.history.length > 1) {
      router.back()
      return
    }
    if (props.redirectOnCreate) {
      router.push(props.redirectOnCreate)
      return
    }
    router.push('/')
    return
  }
  if (props.redirectOnCreate) {
    router.push(props.redirectOnCreate)
    return
  }
  if (payload.isSecret && !board.value?.isAdmin) {
    router.push(`/board/${boardUrl.value}`)
    return
  }
  router.push(`/board/${boardUrl.value}/post/${newPostId}`)
}

function handleSubmit() {
  if (!form.value.title) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }
  if (props.mode === 'create' && !props.hideCategory && !form.value.categoryId) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }

  const payload = buildPayload()

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
      router.push(`/board/${boardUrl.value}/post/${postId.value}`)
    },
    onError: (error) => {
      logger.error('Failed to update post:', error)
    },
  })
}

function handleCancel() {
  router.back()
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
          <p class="nv-compose-kicker">{{ props.mode === 'create' ? $t('board.writePost.composeMode.create') : $t('board.writePost.composeMode.edit') }}</p>
          <h2 class="truncate text-2xl font-semibold tracking-[-0.05em] text-[var(--nv-ink)] sm:text-3xl">
            {{ pageTitle }}
          </h2>
          <p class="mt-2 text-sm text-[var(--nv-ink-soft)]">
            {{ board?.boardName || boardUrl }}
          </p>
        </div>

        <div class="flex flex-wrap items-center justify-end gap-2">
          <BaseButton type="button" variant="ghost" size="sm" @click="showPreview = true">
            {{ $t('board.writePost.actions.preview') }}
          </BaseButton>
          <BaseButton v-if="draftEnabled" type="button" variant="secondary" size="sm" @click="handleSaveDraft">
            {{ $t('board.writePost.actions.saveDraft') }}
          </BaseButton>
          <BaseButton type="button" variant="secondary" size="sm" @click="handleCancel">
            {{ $t('common.cancel') }}
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
          <div class="nv-compose-main-card rounded-[28px] border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)] sm:p-5">
            <div class="mb-4 hidden items-center justify-between gap-3 rounded-[20px] border border-[var(--nv-line)] bg-[var(--nv-elevated)] px-4 py-3 lg:flex">
              <div>
                <p class="nv-compose-kicker">{{ $t('board.writePost.sections.editor') }}</p>
                <p class="mt-1 text-sm text-[var(--nv-ink-soft)]">{{ draftStatusLabel }}</p>
              </div>
              <div class="rounded-full border border-[var(--nv-line)] bg-[var(--nv-surface)] px-3 py-1 text-[11px] font-medium uppercase tracking-[0.16em] text-[var(--nv-muted)]">
                {{ $t('board.writePost.toolbar.insertBlock') }}
              </div>
            </div>

            <div class="mb-4 flex flex-wrap items-center gap-2 lg:hidden">
              <div v-if="!props.hideCategory && filteredCategories.length > 0" class="min-w-[10rem] flex-1">
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
              inputClass="!rounded-2xl !border-[var(--nv-line)] !bg-[var(--nv-elevated)] !px-4 !py-3 !text-sm sm:!text-base"
            />

            <div class="mt-4">
              <div class="flex items-center justify-between rounded-t-[20px] border border-[var(--nv-line)] border-b-0 bg-[var(--nv-elevated)] px-3 py-2">
                <div class="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                  <span>{{ $t('board.writePost.sections.editor') }}</span>
                  <span class="hidden sm:inline">{{ draftStatusLabel }}</span>
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

              <div class="editor-area-container rounded-b-[24px] border border-[var(--nv-line)]">
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
                    <div v-if="showVideoPopover" class="video-url-popover-mask" @click.self="closeVideoPopover">
                      <div class="video-url-popover" :style="{ top: videoPopoverStyle.top, left: videoPopoverStyle.left }" role="dialog" :aria-label="$t('board.writePost.video.dialogLabel')">
                        <span class="video-url-popover-label">{{ $t('board.writePost.video.inputLabel') }}</span>
                        <input
                          v-model="videoUrl"
                          type="url"
                          class="video-url-popover-input"
                          :placeholder="$t('board.writePost.video.placeholder')"
                          @keydown.enter="insertVideoFromPopover"
                          @keydown.escape="closeVideoPopover"
                        >
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

            <div class="mt-4 flex items-center justify-between gap-3 text-xs text-[var(--nv-muted)] sm:hidden">
              <span>{{ draftStatusLabel }}</span>
              <button v-if="draftEnabled" type="button" class="font-medium text-[var(--nv-accent)]" @click="handleSaveDraft">
                {{ $t('board.writePost.actions.saveNow') }}
              </button>
            </div>

            <div v-if="!props.hideTags" class="mt-5 lg:hidden">
              <label for="tags" class="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                {{ $t('common.tags') }}
              </label>
              <PostTags v-model="form.tags" />
            </div>
          </div>
        </section>

        <aside class="space-y-4 lg:sticky lg:top-24 lg:self-start">
          <section class="nv-compose-side-card rounded-[28px] border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
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
              <label for="tags" class="mb-2 block text-xs font-medium uppercase tracking-[0.18em] text-[var(--nv-muted)]">
                {{ $t('common.tags') }}
              </label>
              <PostTags v-model="form.tags" />
            </div>

            <div class="nv-compose-side-section space-y-3">
              <BaseCheckbox v-if="showNotice" id="isNotice" v-model="form.isNotice" :label="$t('common.notice')" :description="$t('board.writePost.noticeDesc')" />
              <BaseCheckbox v-if="canShowNsfw" id="nsfw" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')" :description="$t('board.writePost.nsfwDesc')" />
              <BaseCheckbox v-if="!props.hideSpoiler" id="spoiler" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')" :description="$t('board.writePost.spoilerDesc')" />
              <BaseCheckbox v-if="!props.hideSecret" id="secret" v-model="form.isSecret" :label="$t('board.writePost.secret')" :description="$t('board.writePost.secretDesc')" />
            </div>
          </section>

          <section class="nv-compose-side-card rounded-[28px] border border-[var(--nv-line)] bg-[var(--nv-surface)] p-4 shadow-[var(--nv-shadow-soft)]">
            <div class="mb-3">
              <p class="nv-compose-kicker">{{ $t('board.writePost.sections.status') }}</p>
              <h3 class="text-lg font-semibold text-[var(--nv-ink)]">{{ $t('board.writePost.sections.draftState') }}</h3>
            </div>
            <p class="text-sm text-[var(--nv-ink-soft)]">{{ draftStatusLabel }}</p>
            <div class="mt-4 flex flex-wrap gap-2">
              <BaseButton v-if="draftEnabled" type="button" variant="secondary" size="sm" @click="handleSaveDraft">
                {{ $t('board.writePost.actions.saveDraft') }}
              </BaseButton>
              <BaseButton type="button" variant="ghost" size="sm" @click="showPreview = true">
                {{ $t('board.writePost.actions.preview') }}
              </BaseButton>
              <BaseButton v-if="draftEnabled" type="button" variant="ghost" size="sm" @click="clearRecovery">
                {{ $t('board.writePost.draftStatus.clearLocalBackup') }}
              </BaseButton>
            </div>
            <p class="mt-4 text-xs text-[var(--nv-muted)]">
              <kbd class="rounded border border-[var(--nv-line)] px-2 py-1">Ctrl/Cmd + S</kbd>
              {{ $t('board.writePost.shortcuts.saveDraft') }}
              <span class="mx-2">/</span>
              <kbd class="rounded border border-[var(--nv-line)] px-2 py-1">Ctrl/Cmd + Enter</kbd>
              {{ $t('board.writePost.shortcuts.publish') }}
            </p>
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
        <article class="prose max-w-none dark:prose-invert" v-html="previewHtml" />
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
}

.tiptap-editor-wrapper {
  display: flex;
  min-height: 26rem;
  flex-direction: column;
}

.editor-view-toggle-btn {
  border-radius: 999px;
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
  border-radius: 16px;
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
  margin-bottom: 10px;
  padding: 10px 12px;
  border: 1px solid var(--nv-line);
  border-radius: 12px;
  background: var(--nv-elevated);
  color: var(--nv-ink);
  box-sizing: border-box;
}

.video-url-popover-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.html-source-editor-wrap {
  min-height: 26rem;
}

.html-source-textarea {
  display: block;
  width: 100%;
  min-height: 26rem;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  color: var(--nv-ink);
  background: transparent;
  border: none;
  outline: none;
  resize: vertical;
  white-space: pre-wrap;
  word-break: break-word;
  box-sizing: border-box;
}
</style>


