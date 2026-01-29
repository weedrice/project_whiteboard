<script setup lang="ts">
import { ref, computed, watchEffect, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import PostTags from '@/components/tag/PostTags.vue'
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
import { useToastStore } from '@/stores/toast'
import { useConfirm } from '@/composables/useConfirm'
import EmoticonPicker from '@/components/common/widgets/EmoticonPicker.vue'
import { registerEmoticonBlot } from '@/utils/emoticon-blot'
import type { EmoticonImage } from '@/types/emoticon'

// 컴포넌트 마운트 전에 Blot 등록 (툴바 초기화 전에 완료되어야 함)
registerEmoticonBlot(Quill)

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const { confirm } = useConfirm()
const boardUrl = computed(() => route.params.boardUrl as string)

const { useBoardDetail, useBoardCategories } = useBoard()
const { useCreatePost } = usePost()

const { data: board, isLoading: isBoardLoading } = useBoardDetail(boardUrl)
const { data: categories, isLoading: isCategoriesLoading } = useBoardCategories(boardUrl)
const { mutate: createPost, isPending: isSubmitting } = useCreatePost()

const filteredCategories = computed(() => {
  if (!categories.value) return []
  const userRole = authStore.user?.role || 'USER'
  const isBoardAdmin = board.value?.isAdmin || false

  return categories.value.filter(cat => {
    const minRole = cat.minWriteRole || 'USER'
    if (minRole === 'SUPER_ADMIN') return userRole === 'SUPER_ADMIN'
    if (minRole === 'BOARD_ADMIN') return userRole === 'SUPER_ADMIN' || isBoardAdmin
    return true // USER role
  })
})

const isLoading = computed(() => isBoardLoading.value || isCategoriesLoading.value)
const fileIds = ref<number[]>([])
const editor = ref<InstanceType<typeof QuillEditor> | null>(null)
const quillInstance = ref<any>(null)
const showEmoticonPicker = ref(false)

const form = ref({
  categoryId: '' as string | number,
  title: '',
  contents: '',
  tags: [] as string[], // Array of strings
  isNsfw: false,
  isSpoiler: false,
  isNotice: false
})

const toolbarOptions = [
  ['bold', 'italic', 'underline', 'strike'],        // toggled buttons
  ['blockquote', 'code-block'],

  [{ 'header': 1 }, { 'header': 2 }],               // custom button values
  [{ 'list': 'ordered' }, { 'list': 'bullet' }],
  [{ 'script': 'sub' }, { 'script': 'super' }],      // superscript/subscript
  [{ 'indent': '-1' }, { 'indent': '+1' }],          // outdent/indent
  [{ 'direction': 'rtl' }],                         // text direction

  [{ 'size': ['small', false, 'large', 'huge'] }],  // custom dropdown
  [{ 'header': [1, 2, 3, 4, 5, 6, false] }],

  [{ 'color': [] }, { 'background': [] }],          // dropdown with defaults from theme
  [{ 'font': [] }],
  [{ 'align': [] }],

  ['clean'],                                         // remove formatting
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
        headers: {
          'Content-Type': 'multipart/form-data'
        }
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

  quill.getModule('toolbar').addHandler('image', imageHandler)
  quill.getModule('toolbar').addHandler('emoticon', emoticonHandler)
}

// Set default category when loaded
watchEffect(() => {
  if (filteredCategories.value && filteredCategories.value.length > 0 && !form.value.categoryId) {
    form.value.categoryId = filteredCategories.value[0].categoryId
  }
})

async function handleSubmit() {
  if (!form.value.title || !form.value.categoryId) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }

  const payload = {
    categoryId: typeof form.value.categoryId === 'string' ? parseInt(form.value.categoryId) || 0 : form.value.categoryId,
    title: form.value.title,
    contents: form.value.contents,
    tags: form.value.tags,
    isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false,
    isSpoiler: form.value.isSpoiler,
    isNotice: form.value.isNotice,
    fileIds: fileIds.value // Send collected fileIds
  }

  createPost({ boardUrl: boardUrl.value, data: payload }, {
    onSuccess: (response) => {
      const postId = response.data.data
      router.push(`/board/${boardUrl.value}/post/${postId}`)
    },
    onError: (err: any) => {
      logger.error('Failed to create post:', err)
      // 에러 메시지는 API 인터셉터에서 토스트로 처리됨
    }
  })
}

// 작성 취소 (confirm 후)
async function handleCancel() {
  const hasContent = form.value.title.trim() || form.value.contents.trim()
  if (hasContent) {
    const isConfirmed = await confirm(t('common.messages.confirmDelete'))
    if (!isConfirmed) return
  }
  router.back()
}

// 키보드 단축키 핸들러
const handleKeyDown = async (event: KeyboardEvent) => {
  const { key, ctrlKey, metaKey } = event

  // Ctrl+Enter: 제출
  if ((ctrlKey || metaKey) && key === 'Enter') {
    event.preventDefault()
    handleSubmit()
    return
  }

  // Esc: 취소 (confirm 후)
  if (key === 'Escape') {
    event.preventDefault()
    await handleCancel()
    return
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<template>
  <div class="w-full max-w-full overflow-x-hidden">
    <div class="md:flex md:items-center md:justify-between mb-4 sm:mb-6 ml-2 sm:ml-0">
      <div class="flex-1 min-w-0">
        <h2 class="text-xl font-bold leading-tight text-gray-900 dark:text-white sm:text-3xl sm:leading-8 sm:truncate">
          {{ $t('board.writePost.createTitle') }}
        </h2>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-6 sm:py-10">
      <div class="animate-spin rounded-full h-8 w-8 sm:h-10 sm:w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit"
      class="space-y-4 sm:space-y-6 bg-white dark:bg-gray-800 shadow px-3 py-4 sm:rounded-lg sm:px-6 sm:py-6 transition-colors duration-200">
      <div class="grid grid-cols-1 gap-y-4 gap-x-3 sm:grid-cols-6 sm:gap-y-6 sm:gap-x-4">
        <div class="sm:col-span-3">
          <BaseSelect id="category" v-model="form.categoryId" :label="$t('common.category')"
            labelClass="text-[11px] sm:text-sm" inputClass="!text-xs !py-2 sm:!text-sm sm:!py-2">
            <option v-for="category in filteredCategories" :key="category.categoryId" :value="category.categoryId">
              {{ category.name }}
            </option>
          </BaseSelect>
        </div>

        <div class="sm:col-span-6">
          <BaseInput id="title" v-model="form.title" name="title" type="text" required
            :placeholder="$t('board.writePost.placeholder.title')" :label="$t('common.title')"
            labelClass="text-[11px] sm:text-sm" inputClass="!text-xs !py-2 sm:!text-sm sm:!py-2" />
        </div>

        <div class="sm:col-span-6">
          <label for="contents" class="block text-[11px] font-medium text-gray-700 dark:text-gray-300 sm:text-sm">{{
            $t('common.content') }}</label>
          <div class="mt-1 h-80 min-h-[260px] sm:h-96 relative overflow-hidden rounded border border-gray-200 dark:border-gray-600">
            <QuillEditor ref="editor" :toolbar="toolbarOptions" theme="snow" contentType="html"
              v-model:content="form.contents" @ready="onEditorReady" />
            <EmoticonPicker :show="showEmoticonPicker" @select="handleEmoticonSelect"
              @close="showEmoticonPicker = false" />
          </div>
        </div>

        <div class="sm:col-span-6 mt-4 pt-3 sm:mt-6 sm:pt-0 border-t border-gray-100 dark:border-gray-700 sm:border-0">
          <label for="tags" class="block text-[11px] font-medium text-gray-700 dark:text-gray-300 sm:text-sm">{{ $t('common.tags')
          }}</label>
          <div class="mt-1 sm:max-w-md">
            <PostTags v-model="form.tags" />
          </div>
        </div>

        <div class="sm:col-span-6">
          <!-- 모바일: 공지/NSFW/스포일러 가로 배치, 설명 숨김 -->
          <div class="flex flex-row flex-wrap gap-4 sm:hidden items-center">
            <label v-if="board?.isAdmin" class="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" v-model="form.isNotice"
                class="h-4 w-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600" />
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('common.notice') }}</span>
            </label>
            <label v-if="board?.allowNsfw" class="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" v-model="form.isNsfw"
                class="h-4 w-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600" />
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('board.writePost.nsfw') }}</span>
            </label>
            <label class="inline-flex items-center gap-2 cursor-pointer">
              <input type="checkbox" v-model="form.isSpoiler"
                class="h-4 w-4 text-indigo-600 border-gray-300 rounded focus:ring-indigo-500 dark:bg-gray-700 dark:border-gray-600" />
              <span class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ $t('board.writePost.spoiler') }}</span>
            </label>
          </div>
          <!-- 데스크톱: 기존 체크박스(설명 포함) -->
          <div class="hidden sm:block">
            <BaseCheckbox v-if="board?.isAdmin" id="isNotice" v-model="form.isNotice" :label="$t('common.notice')"
              :description="$t('board.writePost.noticeDesc')" class="mb-3 sm:mb-4" />

            <BaseCheckbox v-if="board?.allowNsfw" id="isNsfw" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')"
              :description="$t('board.writePost.nsfwDesc')" />

            <BaseCheckbox id="isSpoiler" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')"
              :description="$t('board.writePost.spoilerDesc')" class="mt-3 sm:mt-4" />
          </div>
        </div>
      </div>

      <div class="flex justify-end gap-2 sm:gap-3 pt-1">
        <BaseButton type="button" variant="secondary" size="sm" class="!text-xs !px-3 !py-2 sm:!text-sm sm:!px-4 sm:!py-2"
          @click="router.back()">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton type="submit" variant="primary" size="sm" class="!text-xs !px-3 !py-2 sm:!text-sm sm:!px-4 sm:!py-2"
          :loading="isSubmitting">
          {{ isSubmitting ? $t('board.writePost.submitting') : $t('common.submit') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

<style>
/* Quill mobile sizing */
@media (max-width: 639px) {
  .ql-toolbar.ql-snow {
    padding: 6px 8px;
  }
  .ql-toolbar.ql-snow .ql-formats {
    margin-right: 8px;
  }
  .ql-toolbar.ql-snow button {
    width: 26px;
    padding: 2px 3px;
  }
  .ql-container.ql-snow {
    font-size: 16px; /* avoid iOS zoom on focus */
  }
  .ql-editor {
    min-height: 240px;
    padding: 8px 12px;
  }
  /* 모바일에서만 툴바 일부 요소 제거 (글꼴·크기·색·정렬·스크립트·들여쓰기·방향·인용/코드블록) */
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

/* Quill Dark Mode Overrides */
.dark .ql-toolbar.ql-snow {
  border-color: #4b5563;
  /* gray-600 */
  background-color: #1f2937;
  /* gray-800 */
}

.dark .ql-container.ql-snow {
  border-color: #4b5563;
  /* gray-600 */
  background-color: #1f2937;
  /* gray-800 */
  color: #f3f4f6;
  /* gray-100 */
}

.dark .ql-snow .ql-stroke {
  stroke: #9ca3af;
  /* gray-400 */
}

.dark .ql-snow .ql-fill {
  fill: #9ca3af;
  /* gray-400 */
}

.dark .ql-snow .ql-picker {
  color: #9ca3af;
  /* gray-400 */
}

.dark .ql-snow .ql-picker-options {
  background-color: #1f2937;
  /* gray-800 */
  border-color: #4b5563;
  /* gray-600 */
}

.dark .ql-snow .ql-picker-item {
  color: #9ca3af;
  /* gray-400 */
}

.dark .ql-snow .ql-picker-item:hover {
  color: #f3f4f6;
  /* gray-100 */
}

.dark .ql-snow .ql-picker-item.ql-selected {
  color: #60a5fa;
  /* blue-400 */
}

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
.dark .ql-snow .ql-picker-item.ql-selected .ql-stroke-miter {
  stroke: #60a5fa;
  /* blue-400 */
}

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
.dark .ql-snow .ql-picker-item.ql-selected .ql-fill-miter {
  fill: #60a5fa;
  /* blue-400 */
}

/* Emoticon button icon */
button.ql-emoticon {
  width: 24px !important;
  height: 24px !important;
}

/* Emoticon image in content - baseline에 맞춰 텍스트와 같은 밑줄에 정렬 */
img.ql-emoticon {
  width: 100px !important;
  height: 100px !important;
  vertical-align: baseline;
  display: inline-block;
  margin: 0 4px;
}

.ql-snow .ql-toolbar button.ql-emoticon::before,
.ql-snow.ql-toolbar button.ql-emoticon::before {
  content: '😊';
  font-size: 16px;
  line-height: 1;
}

.ql-snow .ql-toolbar button.ql-emoticon:hover,
.ql-snow.ql-toolbar button.ql-emoticon:hover {
  color: #06c;
}

.dark .ql-snow .ql-toolbar button.ql-emoticon:hover,
.dark .ql-snow.ql-toolbar button.ql-emoticon:hover {
  color: #60a5fa;
}
</style>
