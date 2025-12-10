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
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()

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

const form = ref({
  title: '',
  content: '',
  categoryId: '',
  tags: '',
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

        let quill = null
        if (editor.value) {
          if (typeof editor.value.getQuill === 'function') {
            quill = editor.value.getQuill()
          } else {
            quill = editor.value
          }
        }

        if (quill) {
          const range = quill.getSelection(true)
          quill.insertEmbed(range.index, 'image', url)
          quill.setSelection(range.index + 1)
        }
      }
    } catch (err) {
      logger.error('Image upload failed:', err)
      alert(t('common.messages.uploadFailed'))
    }
  }
}

const onEditorReady = (quill) => {
  editor.value = quill
  quill.getModule('toolbar').addHandler('image', imageHandler)
}

watchEffect(() => {
  if (post.value) {
    form.value = {
      title: post.value.title,
      content: post.value.contents,
      categoryId: post.value.category?.categoryId || '',
      tags: post.value.tags ? post.value.tags.join(', ') : '',
      isNsfw: post.value.isNsfw,
      isSpoiler: post.value.isSpoiler
    }
  }
})

async function handleSubmit() {
  if (!form.value.title || !form.value.content) {
    alert(t('board.writePost.validation'))
    return
  }

  const payload = {
    ...form.value,
    tags: form.value.tags.split(',').map(tag => tag.trim()).filter(tag => tag),
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
      alert(t('board.writePost.failUpdate'))
    }
  })
}
</script>

<template>
  <div class="w-full">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          {{ $t('board.writePost.editTitle') }}
        </h2>
      </div>
    </div>

    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <form v-else @submit.prevent="handleSubmit" class="space-y-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div class="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">

        <!-- Category -->
        <div class="sm:col-span-3" v-if="categories.length > 0">
          <label for="category" class="block text-sm font-medium text-gray-700">{{ $t('common.category') }}</label>
          <div class="mt-1">
            <select id="category" v-model="form.categoryId"
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md">
              <option value="" disabled>{{ $t('board.writePost.selectCategory') }}</option>
              <option v-for="category in categories" :key="category.categoryId" :value="category.categoryId">
                {{ category.name }}
              </option>
            </select>
          </div>
        </div>

        <!-- Title -->
        <div class="sm:col-span-6">
          <BaseInput id="title" v-model="form.title" name="title" type="text" required :label="$t('common.title')" />
        </div>

        <!-- Content -->
        <div class="sm:col-span-6">
          <label for="content" class="block text-sm font-medium text-gray-700">{{ $t('common.content') }}</label>
          <div class="mt-1 h-96">
            <QuillEditor ref="editor" :toolbar="toolbarOptions" theme="snow" contentType="html"
              v-model:content="form.content" @ready="onEditorReady" />
          </div>
        </div>

        <!-- Tags -->
        <div class="sm:col-span-6 mt-12">
          <BaseInput id="tags" v-model="form.tags" name="tags" type="text"
            :placeholder="$t('board.writePost.placeholder.tags')" :label="$t('board.writePost.tags')" />
        </div>

        <!-- Options -->
        <div class="sm:col-span-6">
          <div v-if="board?.allowNsfw" class="flex items-start mb-4">
            <div class="flex items-center h-5">
              <input id="nsfw" name="nsfw" type="checkbox" v-model="form.isNsfw"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer" />
            </div>
            <div class="ml-3 text-sm">
              <label for="nsfw" class="font-medium text-gray-700 cursor-pointer">{{ $t('board.writePost.nsfw')
                }}</label>
            </div>
          </div>
          <div class="flex items-start">
            <div class="flex items-center h-5">
              <input id="spoiler" name="spoiler" type="checkbox" v-model="form.isSpoiler"
                class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded cursor-pointer" />
            </div>
            <div class="ml-3 text-sm">
              <label for="spoiler" class="font-medium text-gray-700 cursor-pointer">{{ $t('board.writePost.spoiler')
                }}</label>
            </div>
          </div>
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
