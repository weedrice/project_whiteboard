<script setup lang="ts">
import { ref, computed, watch, watchEffect, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useToastStore } from '@/stores/toast'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import PostEditorTipTap from '@/components/board/PostEditorTipTap.vue'
import logger from '@/utils/logger'

const props = defineProps<{
  mode: 'create' | 'edit'
}>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

const boardUrl = computed(() => route.params.boardUrl as string)
const postId = computed(() => route.params.postId as string)

const { useBoardDetail, useBoardCategories } = useBoard()
const { usePostDetail, useCreatePost, useUpdatePost } = usePost()

const { data: board, isLoading: isBoardLoading } = useBoardDetail(boardUrl)
const { data: categories, isLoading: isCategoriesLoading } = useBoardCategories(boardUrl)
const postIdRef = computed(() => (props.mode === 'edit' ? postId.value : '') as string)
const { data: post, isLoading: isPostLoading } = usePostDetail(postIdRef, {
  enabled: computed(() => props.mode === 'edit' && !!postId.value)
})
const { mutate: createPost, isPending: isCreateSubmitting } = useCreatePost()
const { mutate: updatePost, isPending: isUpdateSubmitting } = useUpdatePost()

const isSubmitting = computed(() => isCreateSubmitting.value || isUpdateSubmitting.value)
const isLoading = computed(() =>
  isBoardLoading.value || isCategoriesLoading.value || (props.mode === 'edit' && isPostLoading.value)
)

const filteredCategories = computed(() => {
  if (!categories.value) return []
  if (props.mode === 'edit') return categories.value
  const userRole = authStore.user?.role || 'USER'
  const isBoardAdmin = board.value?.isAdmin || false
  return categories.value.filter(cat => {
    const minRole = cat.minWriteRole || 'USER'
    if (minRole === 'SUPER_ADMIN') return userRole === 'SUPER_ADMIN'
    if (minRole === 'BOARD_ADMIN') return userRole === 'SUPER_ADMIN' || isBoardAdmin
    return true
  })
})

const tiptapEditorRef = ref<InstanceType<typeof PostEditorTipTap> | null>(null)
const showEmoticonPicker = ref(false)
const showVideoPopover = ref(false)
const videoUrl = ref('')
const editorWrapperRef = ref<HTMLElement | null>(null)
const videoPopoverStyle = ref<{ top: string; left: string }>({ top: '0', left: '0' })
/** 에디터 표시 모드: 보기(WYSIWYG) | HTML 원본 */
const editorViewMode = ref<'visual' | 'html'>('visual')

const form = ref({
  title: '',
  content: '',
  categoryId: '' as string | number,
  tags: [] as string[],
  isNsfw: false,
  isSpoiler: false,
  isNotice: false
})

type FormSnapshot = typeof form.value
const initialFormSnapshot = ref<FormSnapshot | null>(null)
const lastEditPostId = ref<string | null>(null)

function copyFormSnapshot(src: FormSnapshot): FormSnapshot {
  return {
    title: src.title,
    content: src.content,
    categoryId: src.categoryId,
    tags: [...(src.tags || [])],
    isNsfw: src.isNsfw,
    isSpoiler: src.isSpoiler,
    isNotice: src.isNotice
  }
}

function isFormDirty(): boolean {
  const init = initialFormSnapshot.value
  if (!init) return false
  const f = form.value
  if (f.title !== init.title || f.content !== init.content || f.isNsfw !== init.isNsfw || f.isSpoiler !== init.isSpoiler || f.isNotice !== init.isNotice) return true
  const catEqual = String(f.categoryId) === String(init.categoryId)
  if (!catEqual) return true
  if (f.tags.length !== init.tags.length) return true
  return f.tags.some((t, i) => t !== init.tags[i])
}

const isDirty = computed(() => isFormDirty())

const leaveConfirmMessage = computed(() => t('board.writePost.leaveConfirm') || '사이트에서 나가시겠습니까? 변경사항이 저장되지 않을 수 있습니다.')

function onBeforeUnload(e: BeforeUnloadEvent) {
  if (isDirty.value) {
    e.preventDefault()
    e.returnValue = leaveConfirmMessage.value
    return leaveConfirmMessage.value
  }
}

/** YouTube/Vimeo URL을 embed URL로 변환 */
function toEmbedVideoUrl(url: string): string {
  const trimmed = (url || '').trim()
  if (!trimmed) return ''
  const yt = trimmed.match(/^(?:(https?):\/\/)?(?:(?:www|m)\.)?youtube\.com\/watch.*v=([a-zA-Z0-9_-]+)/) ||
    trimmed.match(/^(?:(https?):\/\/)?(?:(?:www|m)\.)?youtu\.be\/([a-zA-Z0-9_-]+)/)
  if (yt) return (yt[1] || 'https') + '://www.youtube.com/embed/' + yt[2] + '?showinfo=0'
  const vimeo = trimmed.match(/^(?:(https?):\/\/)?(?:www\.)?vimeo\.com\/(\d+)/)
  if (vimeo) return (vimeo[1] || 'https') + '://player.vimeo.com/video/' + vimeo[2] + '/'
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
        left: `${rect.left + rect.width / 2}px`
      }
    } else {
      const rect = editorWrapperRef.value.getBoundingClientRect()
      videoPopoverStyle.value = {
        top: `${rect.top + 60}px`,
        left: `${rect.left + rect.width / 2}px`
      }
    }
  } else {
    const cx = window.innerWidth / 2
    const cy = window.innerHeight / 2
    videoPopoverStyle.value = { top: `${cy}px`, left: `${cx}px` }
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
    toastStore.addToast(t('board.writePost.videoUrlRequired') || '동영상 URL을 입력해 주세요.', 'error')
    return
  }
  tiptapEditorRef.value?.setVideo(embedUrl)
  closeVideoPopover()
}

function handleEmoticonSelect(image: import('@/types/emoticon').EmoticonImage) {
  tiptapEditorRef.value?.setEmoticon(image)
  showEmoticonPicker.value = false
}

// Edit: fill form from post and set initial snapshot once per post
watchEffect(() => {
  if (props.mode === 'edit' && post.value) {
    const id = String(post.value.postId)
    if (lastEditPostId.value !== id) {
      lastEditPostId.value = id
      form.value = {
        title: post.value.title,
        content: post.value.contents,
        categoryId: post.value.category?.categoryId ?? '',
        tags: post.value.tags?.map((t: { name?: string } | string) => typeof t === 'string' ? t : (t.name ?? '')) ?? [],
        isNsfw: post.value.isNsfw,
        isSpoiler: post.value.isSpoiler,
        isNotice: false
      }
      initialFormSnapshot.value = copyFormSnapshot(form.value)
    }
  }
})

// Create: default category and set initial snapshot once ready
const createInitialSet = ref(false)
watchEffect(() => {
  if (props.mode === 'create' && filteredCategories.value?.length) {
    if (!form.value.categoryId) form.value.categoryId = filteredCategories.value[0].categoryId
    if (!createInitialSet.value) {
      createInitialSet.value = true
      nextTick(() => {
        if (!initialFormSnapshot.value) initialFormSnapshot.value = copyFormSnapshot(form.value)
      })
    }
  }
})
// Create: when loading finishes, ensure initial snapshot is set (e.g. no categories case)
watch(isLoading, (loading) => {
  if (props.mode === 'create' && !loading && !initialFormSnapshot.value) {
    nextTick(() => {
      if (!initialFormSnapshot.value) initialFormSnapshot.value = copyFormSnapshot(form.value)
    })
  }
}, { immediate: true })

function buildPayload() {
  const fileIdsRef = tiptapEditorRef.value?.fileIds
  const fileIdsArray = (fileIdsRef && typeof fileIdsRef === 'object' && 'value' in fileIdsRef ? fileIdsRef.value : []) as number[]
  return {
    title: form.value.title,
    categoryId: typeof form.value.categoryId === 'string' ? parseInt(form.value.categoryId) || 0 : form.value.categoryId,
    tags: form.value.tags,
    contents: form.value.content,
    isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false,
    isSpoiler: form.value.isSpoiler,
    ...(props.mode === 'create' && { isNotice: form.value.isNotice }),
    fileIds: fileIdsArray
  }
}

function handleSubmit() {
  if (!form.value.title) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }
  if (props.mode === 'create' && !form.value.categoryId) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }

  const payload = buildPayload()

  if (props.mode === 'create') {
    createPost({ boardUrl: boardUrl.value, data: payload }, {
      onSuccess: (response) => {
        const newPostId = response.data.data
        router.push(`/board/${boardUrl.value}/post/${newPostId}`)
      },
      onError: (err) => {
        logger.error('Failed to create post:', err)
      }
    })
  } else {
    updatePost({ postId: postId.value, data: payload }, {
      onSuccess: () => {
        router.push(`/board/${boardUrl.value}/post/${postId.value}`)
      },
      onError: (err) => {
        logger.error('Failed to update post:', err)
      }
    })
  }
}

function handleCancel() {
  // 확인은 라우트 가드(onBeforeRouteLeave)에서만 한 번 수행 (중복 팝업 방지)
  router.back()
}

function handleKeyDown(event: KeyboardEvent) {
  const { key, ctrlKey, metaKey } = event
  if ((ctrlKey || metaKey) && key === 'Enter') {
    event.preventDefault()
    handleSubmit()
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
    event.preventDefault()
    handleCancel()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
  window.addEventListener('beforeunload', onBeforeUnload)
  if (props.mode === 'create') {
    nextTick(() => {
      if (!initialFormSnapshot.value) initialFormSnapshot.value = copyFormSnapshot(form.value)
    })
  }
})
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
  window.removeEventListener('beforeunload', onBeforeUnload)
})

defineExpose({
  hasUnsavedChanges: () => isFormDirty(),
  getLeaveConfirmMessage: () => leaveConfirmMessage.value
})

const pageTitle = computed(() =>
  props.mode === 'create' ? t('board.writePost.createTitle') : t('board.writePost.editTitle')
)
const submitLabel = computed(() =>
  isSubmitting.value
    ? (props.mode === 'create' ? t('board.writePost.submitting') : t('board.writePost.updating'))
    : (props.mode === 'create' ? t('common.submit') : t('board.writePost.update'))
)
const showNotice = computed(() => props.mode === 'create' && board.value?.isAdmin)
</script>

<template>
  <div class="w-full max-w-full overflow-x-hidden">
    <div class="md:flex md:items-center md:justify-between mb-4 sm:mb-6 ml-2 sm:ml-0">
      <div class="flex-1 min-w-0">
        <h2 class="text-xl font-bold leading-tight text-gray-900 dark:text-white sm:text-3xl sm:leading-8 sm:truncate">
          {{ pageTitle }}
        </h2>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-6 sm:py-10">
      <BaseSpinner size="lg" />
    </div>

    <form v-else @submit.prevent="handleSubmit"
      class="space-y-4 sm:space-y-6 bg-white dark:bg-gray-800 shadow px-3 py-4 sm:rounded-lg sm:px-6 sm:py-6 transition-colors duration-200">
      <div class="grid grid-cols-1 gap-y-4 gap-x-3 sm:grid-cols-6 sm:gap-y-6 sm:gap-x-4">

        <div v-if="filteredCategories && filteredCategories.length > 0" class="sm:col-span-3">
          <BaseSelect id="category" v-model="form.categoryId" :label="$t('common.category')"
            labelClass="text-[11px] sm:text-sm" inputClass="!text-xs !py-2 sm:!text-sm sm:!py-2">
            <option value="" disabled>{{ $t('board.writePost.selectCategory') }}</option>
            <option v-for="cat in filteredCategories" :key="cat.categoryId" :value="cat.categoryId">
              {{ cat.name }}
            </option>
          </BaseSelect>
        </div>

        <div class="sm:col-span-6">
          <BaseInput id="title" v-model="form.title" name="title" type="text" required
            :placeholder="$t('board.writePost.placeholder.title')" :label="$t('common.title')"
            labelClass="text-[11px] sm:text-sm" inputClass="!text-xs !py-2 sm:!text-sm sm:!py-2" />
        </div>

        <div class="sm:col-span-6">
          <label for="content" class="block text-[11px] font-medium text-gray-700 dark:text-gray-300 sm:text-sm mb-1">{{
            $t('common.content') }}</label>
          <div class="editor-area-container mt-1 h-80 min-h-[260px] sm:h-96 rounded border border-gray-200 dark:border-gray-600 overflow-hidden flex flex-col">
            <div class="editor-area-toggle-row">
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
            <div v-if="editorViewMode === 'visual'" ref="editorWrapperRef" class="tiptap-editor-wrapper flex-1 min-h-0 relative overflow-hidden border-t border-gray-200 dark:border-gray-600 flex flex-col">
              <PostEditorTipTap
                ref="tiptapEditorRef"
                v-model="form.content"
                @open-video="openVideoPopover"
                @open-emoticon="showEmoticonPicker = true"
              />
              <Teleport to="body">
                <div v-if="showVideoPopover" class="video-url-popover-mask" @click.self="closeVideoPopover">
                  <div
                    class="video-url-popover"
                    :style="{
                      top: videoPopoverStyle.top,
                      left: videoPopoverStyle.left
                    }"
                    role="dialog"
                    aria-label="동영상 URL 입력"
                  >
                    <span class="video-url-popover-label">동영상 URL:</span>
                    <input
                      v-model="videoUrl"
                      type="url"
                      class="video-url-popover-input"
                      :placeholder="'YouTube / Vimeo URL'"
                      @keydown.enter="insertVideoFromPopover"
                      @keydown.escape="closeVideoPopover"
                    />
                    <div class="video-url-popover-actions">
                      <BaseButton type="button" variant="secondary" size="sm" @click="closeVideoPopover">
                        {{ $t('common.cancel') }}
                      </BaseButton>
                      <BaseButton type="button" variant="primary" size="sm" @click="insertVideoFromPopover">
                        {{ $t('common.confirm') || '확인' }}
                      </BaseButton>
                    </div>
                  </div>
                </div>
              </Teleport>
              <EmoticonPicker :show="showEmoticonPicker" @select="handleEmoticonSelect"
                @close="showEmoticonPicker = false" />
            </div>
            <div v-if="editorViewMode === 'html'" class="html-source-editor-wrap flex-1 min-h-0 overflow-hidden border-t border-gray-200 dark:border-gray-600">
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

        <div class="sm:col-span-6 mt-4 pt-3 sm:mt-6 sm:pt-0 border-t border-gray-100 dark:border-gray-700 sm:border-0">
          <label for="tags" class="block text-[11px] font-medium text-gray-700 dark:text-gray-300 sm:text-sm">{{ $t('common.tags') }}</label>
          <div class="mt-1 sm:max-w-md">
            <PostTags v-model="form.tags" />
          </div>
        </div>

        <div class="sm:col-span-6">
          <div class="flex flex-row flex-wrap gap-4 sm:hidden items-center">
            <BaseCheckbox v-if="showNotice" id="isNotice-m" v-model="form.isNotice" :label="$t('common.notice')" />
            <BaseCheckbox v-if="board?.allowNsfw" id="nsfw-m" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')" />
            <BaseCheckbox id="spoiler-m" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')" />
          </div>
          <div class="hidden sm:block">
            <BaseCheckbox v-if="showNotice" id="isNotice" v-model="form.isNotice" :label="$t('common.notice')"
              :description="$t('board.writePost.noticeDesc')" class="mb-3 sm:mb-4" />
            <BaseCheckbox v-if="board?.allowNsfw" id="nsfw" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')"
              :description="$t('board.writePost.nsfwDesc')" />
            <BaseCheckbox id="spoiler" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')"
              :description="$t('board.writePost.spoilerDesc')" class="mt-3 sm:mt-4" />
          </div>
        </div>
      </div>

      <div class="flex justify-end gap-2 sm:gap-3 pt-1">
        <BaseButton type="button" variant="secondary" size="sm" class="!text-xs !px-3 !py-2 sm:!text-sm sm:!px-4 sm:!py-2"
          @click="handleCancel()">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton type="submit" variant="primary" size="sm" class="!text-xs !px-3 !py-2 sm:!text-sm sm:!px-4 sm:!py-2"
          :loading="isSubmitting">
          {{ submitLabel }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

<style scoped>
.tiptap-editor-wrapper {
  display: flex;
  flex-direction: column;
}
</style>

<style>
/* 비디오 URL 팝오버: 툴바 아래 고정 위치 */
.video-url-popover-mask {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: transparent;
}
.video-url-popover {
  position: fixed;
  transform: translateX(-50%);
  margin-top: 0;
  min-width: 320px;
  max-width: 90vw;
  padding: 12px 14px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);
  z-index: 10000;
}
.dark .video-url-popover {
  background: #1f2937;
  border-color: #4b5563;
  box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.3);
}
.video-url-popover-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: #374151;
  margin-bottom: 6px;
}
.dark .video-url-popover-label { color: #d1d5db; }
.video-url-popover-input {
  display: block;
  width: 100%;
  padding: 8px 10px;
  font-size: 14px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  margin-bottom: 10px;
  box-sizing: border-box;
}
.dark .video-url-popover-input {
  background: #374151;
  border-color: #4b5563;
  color: #f3f4f6;
}
.video-url-popover-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

/* 에디터 영역 컨테이너 (전환 탭 + 본문) */
.editor-area-container {
  background: #fff;
}
.dark .editor-area-container {
  background: #1f2937;
}
.editor-area-toggle-row {
  display: flex;
  flex-shrink: 0;
  gap: 2px;
  padding: 6px 8px 0;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
}
.dark .editor-area-toggle-row {
  border-color: #4b5563;
  background: #374151;
}
.editor-view-toggle-btn {
  padding: 6px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
  background: transparent;
  border: none;
  border-radius: 6px 6px 0 0;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.dark .editor-view-toggle-btn { color: #9ca3af; }
.editor-view-toggle-btn:hover { color: #111827; background: #f3f4f6; }
.dark .editor-view-toggle-btn:hover { color: #f3f4f6; background: #4b5563; }
.editor-view-toggle-btn.active {
  color: #fff;
  background: #4f46e5;
}
.dark .editor-view-toggle-btn.active { background: #6366f1; }

/* HTML 원본 텍스트 영역 */
.html-source-editor-wrap {
  background: #fff;
}
.dark .html-source-editor-wrap {
  background: #1f2937;
}
.html-source-textarea {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 240px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.5;
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
  color: #111827;
  background: transparent;
  border: none;
  outline: none;
  resize: none;
  white-space: pre-wrap;
  word-break: break-all;
  box-sizing: border-box;
}
.dark .html-source-textarea {
  color: #e5e7eb;
}
.html-source-textarea::placeholder {
  color: #9ca3af;
}
.dark .html-source-textarea::placeholder { color: #6b7280; }
</style>
