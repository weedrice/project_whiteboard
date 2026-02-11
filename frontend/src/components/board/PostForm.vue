<script setup lang="ts">
import { ref, computed, watchEffect, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import { useI18n } from 'vue-i18n'
import { QuillEditor, Quill } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import axios from '@/api'
import logger from '@/utils/logger'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import BaseSpinner from '@/components/common/ui/BaseSpinner.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import { registerEmoticonBlot } from '@/utils/emoticon-blot'
import type { EmoticonImage } from '@/types/emoticon'

registerEmoticonBlot(Quill)

const props = defineProps<{
  mode: 'create' | 'edit'
}>()

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()

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

const fileIds = ref<number[]>([])
const editor = ref<InstanceType<typeof QuillEditor> | null>(null)
const quillInstance = ref<any>(null)
const showEmoticonPicker = ref(false)
const showVideoPopover = ref(false)
const videoUrl = ref('')
const quillEditorWrapperRef = ref<HTMLElement | null>(null)
const videoPopoverStyle = ref<{ top: string; left: string }>({ top: '0', left: '0' })
/** PC에서 비디오 버튼 클릭 시, 클릭 좌표 저장 (팝오버 위치용) */
const lastVideoButtonClick = ref<{ x: number; y: number } | null>(null)

const form = ref({
  title: '',
  content: '',
  categoryId: '' as string | number,
  tags: [] as string[],
  isNsfw: false,
  isSpoiler: false,
  isNotice: false
})

const toolbarOptions = [
  ['bold', 'italic', 'underline', 'strike'],
  ['blockquote', 'code-block'],
  [{ 'header': 1 }, { 'header': 2 }],
  [{ 'list': 'ordered' }, { 'list': 'bullet' }],
  [{ 'script': 'sub' }, { 'script': 'super' }],
  [{ 'indent': '-1' }, { 'indent': '+1' }],
  [{ 'direction': 'rtl' }],
  [{ 'size': ['small', false, 'large', 'huge'] }],
  [{ 'header': [1, 2, 3, 4, 5, 6, false] }],
  [{ 'color': [] }, { 'background': [] }],
  [{ 'font': [] }],
  [{ 'align': [] }],
  ['clean'],
  ['link', 'image', 'video', 'emoticon']
]

const imageHandler = () => {
  const input = document.createElement('input')
  input.setAttribute('type', 'file')
  input.setAttribute('accept', 'image/*')
  input.click()
  input.onchange = async () => {
    if (!input.files) return
    const file = input.files[0]
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    try {
      const res = await axios.post('/files/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      if (res.data.success) {
        const { url, fileId } = res.data.data
        fileIds.value.push(fileId)
        if (quillInstance.value) {
          const range = quillInstance.value.getSelection(true)
          const index = range ? range.index : quillInstance.value.getLength()
          quillInstance.value.insertEmbed(index, 'image', url)
          quillInstance.value.setSelection(index + 1)
        }
      }
    } catch (err) {
      logger.error('Image upload failed:', err)
      toastStore.addToast(t('common.messages.uploadFailed'), 'error')
    }
  }
}

/** YouTube/Vimeo URL을 embed URL로 변환 (Quill 기본 동작과 동일) */
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
  const clickPos = lastVideoButtonClick.value
  lastVideoButtonClick.value = null

  if (typeof window === 'undefined') {
    videoPopoverStyle.value = { top: '300px', left: '400px' }
    videoUrl.value = ''
    showVideoPopover.value = true
    return
  }

  if (!isMobileView() && clickPos) {
    videoPopoverStyle.value = {
      top: `${clickPos.y + 8}px`,
      left: `${clickPos.x}px`
    }
  } else {
    if (!quillEditorWrapperRef.value) return
    const toolbar = quillEditorWrapperRef.value.querySelector('.ql-toolbar')
    if (toolbar) {
      const rect = toolbar.getBoundingClientRect()
      videoPopoverStyle.value = {
        top: `${rect.bottom + 8}px`,
        left: `${rect.left + rect.width / 2}px`
      }
    } else {
      const cx = window.innerWidth / 2
      const cy = window.innerHeight / 2
      videoPopoverStyle.value = { top: `${cy}px`, left: `${cx}px` }
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
    toastStore.addToast(t('board.writePost.videoUrlRequired') || '동영상 URL을 입력해 주세요.', 'error')
    return
  }
  if (quillInstance.value) {
    const range = quillInstance.value.getSelection(true)
    const index = range ? range.index : quillInstance.value.getLength()
    quillInstance.value.insertEmbed(index, 'video', embedUrl)
    quillInstance.value.setSelection(index + 1)
  }
  closeVideoPopover()
}

const videoHandler = () => {
  openVideoPopover()
}

const emoticonHandler = () => {
  showEmoticonPicker.value = !showEmoticonPicker.value
}

const handleEmoticonSelect = (image: EmoticonImage) => {
  if (quillInstance.value) {
    const range = quillInstance.value.getSelection(true)
    const index = range ? range.index : quillInstance.value.getLength()
    quillInstance.value.insertEmbed(index, 'emoticon', {
      src: image.imageUrl,
      alt: ':emoticon:'
    })
    quillInstance.value.setSelection(index + 1)
  }
  showEmoticonPicker.value = false
}

const onEditorReady = (quill: any) => {
  quillInstance.value = quill
  const toolbar = quill.getModule('toolbar')
  toolbar.addHandler('image', imageHandler)
  toolbar.addHandler('video', videoHandler)
  toolbar.addHandler('emoticon', emoticonHandler)
  const videoBtn = toolbar.container?.querySelector?.('button.ql-video')
  if (videoBtn) {
    videoBtn.addEventListener('mousedown', (e: MouseEvent) => {
      lastVideoButtonClick.value = { x: e.clientX, y: e.clientY }
    })
  }
}

// Edit: fill form from post
watchEffect(() => {
  if (props.mode === 'edit' && post.value) {
    form.value = {
      title: post.value.title,
      content: post.value.contents,
      categoryId: post.value.category?.categoryId ?? '',
      tags: post.value.tags?.map((t: { name?: string } | string) => typeof t === 'string' ? t : (t.name ?? '')) ?? [],
      isNsfw: post.value.isNsfw,
      isSpoiler: post.value.isSpoiler,
      isNotice: false
    }
  }
})

// Create: default category
watchEffect(() => {
  if (props.mode === 'create' && filteredCategories.value?.length && !form.value.categoryId) {
    form.value.categoryId = filteredCategories.value[0].categoryId
  }
})

function buildPayload() {
  return {
    title: form.value.title,
    categoryId: typeof form.value.categoryId === 'string' ? parseInt(form.value.categoryId) || 0 : form.value.categoryId,
    tags: form.value.tags,
    contents: form.value.content,
    isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false,
    isSpoiler: form.value.isSpoiler,
    ...(props.mode === 'create' && { isNotice: form.value.isNotice }),
    fileIds: fileIds.value
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

async function handleCancel() {
  const hasContent = form.value.title.trim() || form.value.content.trim()
  if (hasContent) {
    const message = props.mode === 'edit'
      ? t('board.writePost.discardEditConfirm')
      : t('board.writePost.discardCreateConfirm')
    const isConfirmed = await confirm(message)
    if (!isConfirmed) return
  }
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
    event.preventDefault()
    handleCancel()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
})
onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
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
          <label for="content" class="block text-[11px] font-medium text-gray-700 dark:text-gray-300 sm:text-sm">{{
            $t('common.content') }}</label>
          <div ref="quillEditorWrapperRef" class="quill-editor-wrapper mt-1 h-80 min-h-[260px] sm:h-96 relative overflow-hidden rounded border border-gray-200 dark:border-gray-600">
            <div class="quill-editor-inner quill-flex-container">
              <QuillEditor ref="editor" :toolbar="toolbarOptions" theme="snow" contentType="html"
                v-model:content="form.content" @ready="onEditorReady" />
            </div>
            <!-- 비디오 URL 입력 팝오버: 툴바 아래 고정 위치로 표시 -->
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
.quill-editor-wrapper {
  display: flex;
  flex-direction: column;
}
.quill-editor-inner {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
/* 툴바+컨테이너의 공통 부모(또는 Quill 루트)가 flex로 높이 배분 */
.quill-editor-inner.quill-flex-container {
  display: flex;
  flex-direction: column;
}
/* Quill이 단일 루트로 렌더할 때만 해당 루트에 flex 적용 (다중 루트 시 툴바가 flex:1 받는 것 방지) */
.quill-editor-inner.quill-flex-container > *:only-child {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
/* 툴바: float 기반 레이아웃 유지, 높이만 고정 */
.quill-editor-wrapper :deep(.ql-toolbar.ql-snow) {
  flex-shrink: 0;
  width: 100%;
  display: block;
  box-sizing: border-box;
}
.quill-editor-wrapper :deep(.ql-container.ql-snow) {
  flex: 1;
  min-height: 0;
  overflow: auto;
  scrollbar-width: thin;
  scrollbar-color: #d1d5db #e5e7eb;
}
.quill-editor-wrapper :deep(.ql-container.ql-snow::-webkit-scrollbar) {
  width: 10px;
}
.quill-editor-wrapper :deep(.ql-container.ql-snow::-webkit-scrollbar-track) {
  background: #e5e7eb;
}
.quill-editor-wrapper :deep(.ql-container.ql-snow::-webkit-scrollbar-thumb) {
  background: #9ca3af;
  border-radius: 5px;
}
.quill-editor-wrapper :deep(.ql-container.ql-snow::-webkit-scrollbar-thumb:hover) {
  background: #6b7280;
}
.quill-editor-wrapper :deep(.ql-editor) {
  min-height: 100%;
  box-sizing: border-box;
}
</style>

<style>
@media (max-width: 639px) {
  .ql-toolbar.ql-snow { padding: 6px 8px; }
  .ql-toolbar.ql-snow .ql-formats { margin-right: 8px; }
  .ql-toolbar.ql-snow button { width: 26px; padding: 2px 3px; }
  .ql-container.ql-snow { font-size: 16px; }
  .ql-editor { min-height: 240px; padding: 8px 12px; }
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-size),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-header),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-color),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-background),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-font),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-align),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-script),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-indent),
  .ql-toolbar.ql-snow .ql-formats:has(.ql-picker.ql-direction),
  .ql-toolbar.ql-snow .ql-formats:has(button.ql-blockquote),
  .ql-toolbar.ql-snow .ql-formats:has(button.ql-code-block) {
    display: none !important;
  }
}
.dark .ql-toolbar.ql-snow { border-color: #4b5563; background-color: #1f2937; }
.dark .ql-container.ql-snow { border-color: #4b5563; background-color: #1f2937; color: #f3f4f6; }
.dark .quill-editor-wrapper .ql-container.ql-snow {
  scrollbar-color: #6b7280 #374151;
}
.dark .quill-editor-wrapper .ql-container.ql-snow::-webkit-scrollbar-track { background: #374151; }
.dark .quill-editor-wrapper .ql-container.ql-snow::-webkit-scrollbar-thumb { background: #6b7280; }
.dark .quill-editor-wrapper .ql-container.ql-snow::-webkit-scrollbar-thumb:hover { background: #9ca3af; }
.dark .ql-snow .ql-stroke { stroke: #9ca3af; }
.dark .ql-snow .ql-fill { fill: #9ca3af; }
.dark .ql-snow .ql-picker { color: #9ca3af; }
.dark .ql-snow .ql-picker-options { background-color: #1f2937; border-color: #4b5563; }
.dark .ql-snow .ql-picker-item { color: #9ca3af; }
.dark .ql-snow .ql-picker-item:hover { color: #f3f4f6; }
.dark .ql-snow .ql-picker-item.ql-selected { color: #60a5fa; }
.dark .ql-snow.ql-toolbar button:hover .ql-stroke,
.dark .ql-snow.ql-toolbar button.ql-active .ql-stroke,
.dark .ql-snow .ql-picker-label:hover .ql-stroke,
.dark .ql-snow .ql-picker-label.ql-active .ql-stroke,
.dark .ql-snow .ql-picker-item:hover .ql-stroke,
.dark .ql-snow .ql-picker-item.ql-selected .ql-stroke,
.dark .ql-snow.ql-toolbar button:hover .ql-stroke-miter,
.dark .ql-snow.ql-toolbar button.ql-active .ql-stroke-miter,
.dark .ql-snow .ql-picker-label:hover .ql-stroke-miter,
.dark .ql-snow .ql-picker-label.ql-active .ql-stroke-miter,
.dark .ql-snow .ql-picker-item:hover .ql-stroke-miter,
.dark .ql-snow .ql-picker-item.ql-selected .ql-stroke-miter { stroke: #60a5fa; }
.dark .ql-snow.ql-toolbar button:hover .ql-fill,
.dark .ql-snow.ql-toolbar button.ql-active .ql-fill,
.dark .ql-snow .ql-picker-label:hover .ql-fill,
.dark .ql-snow .ql-picker-label.ql-active .ql-fill,
.dark .ql-snow .ql-picker-item:hover .ql-fill,
.dark .ql-snow .ql-picker-item.ql-selected .ql-fill,
.dark .ql-snow.ql-toolbar button:hover .ql-fill-miter,
.dark .ql-snow.ql-toolbar button.ql-active .ql-fill-miter,
.dark .ql-snow .ql-picker-label:hover .ql-fill-miter,
.dark .ql-snow .ql-picker-label.ql-active .ql-fill-miter,
.dark .ql-snow .ql-picker-item:hover .ql-fill-miter,
.dark .ql-snow .ql-picker-item.ql-selected .ql-fill-miter { fill: #60a5fa; }
button.ql-emoticon { width: 24px !important; height: 24px !important; }
img.ql-emoticon {
  width: 100px !important; height: 100px !important;
  vertical-align: baseline; display: inline-block; margin: 0 4px;
}
.ql-snow .ql-toolbar button.ql-emoticon::before,
.ql-snow.ql-toolbar button.ql-emoticon::before { content: '😊'; font-size: 16px; line-height: 1; }
.ql-snow .ql-toolbar button.ql-emoticon:hover { color: #06c; }
.dark .ql-snow .ql-toolbar button.ql-emoticon:hover { color: #60a5fa; }

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
</style>
