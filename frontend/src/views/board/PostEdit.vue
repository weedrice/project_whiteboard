<script setup>
import { ref, onMounted, computed, watchEffect } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useBoard } from '@/composables/useBoard'
import { usePost } from '@/composables/usePost'
import { useI18n } from 'vue-i18n'
import { QuillEditor } from '@vueup/vue-quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'
import axios from '@/api'
import logger from '@/utils/logger'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseSelect from '@/components/common/ui/BaseSelect.vue'
import BaseCheckbox from '@/components/common/ui/BaseCheckbox.vue'
import PostTags from '@/components/tag/PostTags.vue'
import { useToastStore } from '@/stores/toast'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const toastStore = useToastStore()

const boardUrl = computed(() => route.params.boardUrl)
const postId = computed(() => route.params.postId)

const { useBoardDetail, useBoardCategories } = useBoard()
const { usePostDetail, useUpdatePost } = usePost()

const { data: board, isLoading: isBoardLoading } = useBoardDetail(boardUrl)
const { data: categories, isLoading: isCategoriesLoading } = useBoardCategories(boardUrl)
const { data: post, isLoading: isPostLoading } = usePostDetail(postId)
const { mutate: updatePost, isLoading: isSubmitting } = useUpdatePost()

const isLoading = computed(() => isBoardLoading.value || isCategoriesLoading.value || isPostLoading.value)
const fileIds = ref([])
const editor = ref(null)
const quillInstance = ref(null)

const form = ref({
  title: '',
  content: '',
  categoryId: '',
  tags: [],
  isNsfw: false,
  isSpoiler: false
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
  ['link', 'image', 'video']
]

const imageHandler = () => {
  const input = document.createElement('input')
  input.setAttribute('type', 'file')
  input.setAttribute('accept', 'image/*')
  input.click()

  input.onchange = async () => {
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

const onEditorReady = (quill) => {
  quillInstance.value = quill
  quill.getModule('toolbar').addHandler('image', imageHandler)
}

watchEffect(() => {
  if (post.value) {
    form.value = {
      title: post.value.title,
      content: post.value.contents,
      categoryId: post.value.category?.categoryId || '',
      tags: post.value.tags || [],
      isNsfw: post.value.isNsfw,
      isSpoiler: post.value.isSpoiler
    }
  }
})

async function handleSubmit() {
  if (!form.value.title || !form.value.content) {
    toastStore.addToast(t('board.writePost.validation'), 'error')
    return
  }

  const payload = {
    ...form.value,
    tags: form.value.tags,
    contents: form.value.content, // API expects 'contents'
    isNsfw: board.value?.allowNsfw ? form.value.isNsfw : false,
    isSpoiler: form.value.isSpoiler, // Explicitly include isSpoiler
    fileIds: fileIds.value // Send collected fileIds
  }

  // Remove 'content' key as we mapped it to 'contents'
  delete payload.content

  updatePost({ postId: postId.value, data: payload }, {
    onSuccess: () => {
      router.push(`/board/${boardUrl.value}/post/${postId.value}`)
    },
    onError: (err) => {
      logger.error('Failed to update post:', err)
      toastStore.addToast(t('board.writePost.failUpdate'), 'error')
    }
  })
}
</script>

<template>
  <div class="w-full">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 dark:text-white sm:text-3xl sm:truncate">
          {{ $t('board.writePost.editTitle') }}
        </h2>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit"
      class="space-y-6 bg-white dark:bg-gray-800 shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div class="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">

        <!-- Category -->
        <div class="sm:col-span-3" v-if="categories.length > 0">
          <BaseSelect id="category" v-model="form.categoryId" :label="$t('common.category')">
            <option value="" disabled>{{ $t('board.writePost.selectCategory') }}</option>
            <option v-for="category in categories" :key="category.categoryId" :value="category.categoryId">
              {{ category.name }}
            </option>
          </BaseSelect>
        </div>

        <!-- Title -->
        <div class="sm:col-span-6">
          <BaseInput id="title" v-model="form.title" name="title" type="text" required :label="$t('common.title')" />
        </div>

        <!-- Content -->
        <div class="sm:col-span-6">
          <label for="content" class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{
            $t('common.content') }}</label>
          <div class="mt-1 h-96">
            <QuillEditor ref="editor" :toolbar="toolbarOptions" theme="snow" contentType="html"
              v-model:content="form.content" @ready="onEditorReady" />
          </div>
        </div>

        <!-- Tags -->
        <div class="sm:col-span-6 mt-12">
          <label for="tags" class="block text-sm font-medium text-gray-700 dark:text-gray-300">{{
            $t('board.writePost.tags') }}</label>
          <div class="mt-1">
            <PostTags v-model="form.tags" />
          </div>
        </div>

        <!-- Options -->
        <div class="sm:col-span-6">
          <BaseCheckbox v-if="board?.allowNsfw" id="nsfw" v-model="form.isNsfw" :label="$t('board.writePost.nsfw')"
            class="mb-4" />
          <BaseCheckbox id="spoiler" v-model="form.isSpoiler" :label="$t('board.writePost.spoiler')" />
        </div>

      </div>

      <div class="flex justify-end space-x-3">
        <BaseButton type="button" variant="secondary" @click="router.back()">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton type="submit" variant="primary" :loading="isSubmitting">
          {{ isSubmitting ? $t('board.writePost.updating') : $t('board.writePost.update') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>
