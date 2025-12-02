<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import CategoryManager from '@/components/board/CategoryManager.vue'
import BaseInput from '@/components/common/BaseInput.vue'
import { useI18n } from 'vue-i18n'
import axios from '@/api'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const boardUrl = route.params.boardUrl

const form = ref({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  sortOrder: 0,
  allowNsfw: false
})

const isLoading = ref(true)
const isSubmitting = ref(false)

const fileInput = ref(null)
const selectedFile = ref(null)
const previewImage = ref(null)

function handleFileChange(event) {
  const file = event.target.files[0]
  if (file) {
    selectedFile.value = file
    previewImage.value = URL.createObjectURL(file)
  }
}

async function fetchBoard() {
  isLoading.value = true
  try {
    const { data } = await boardApi.getBoard(boardUrl)
    if (data.success) {
      const board = data.data
      form.value = {
        boardName: board.boardName,
        boardUrl: board.boardUrl,
        description: board.description,
        iconUrl: board.iconUrl || '',
        sortOrder: board.sortOrder || 0,
        allowNsfw: board.allowNsfw || false
      }
      if (form.value.iconUrl) {
        previewImage.value = form.value.iconUrl
      }
    }
  } catch (err) {
    console.error('Failed to load board:', err)
    alert(t('board.writePost.failLoad'))
    router.push(`/board/${boardUrl}`)
  } finally {
    isLoading.value = false
  }
}

async function handleSubmit() {
  if (!form.value.boardName) return

  isSubmitting.value = true
  try {
    let iconUrl = form.value.iconUrl

    if (selectedFile.value) {
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      
      const uploadRes = await axios.post('/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      if (uploadRes.data.success) {
        iconUrl = uploadRes.data.data.url
      }
    }

    const payload = { ...form.value, iconUrl }
    
    const { data } = await boardApi.updateBoard(boardUrl, payload)
    if (data.success) {
      alert(t('board.form.successUpdate'))
      router.push(`/board/${data.data.boardUrl}`)
    }
  } catch (err) {
    console.error('Failed to update board:', err)
    alert(t('board.form.failUpdate'))
  } finally {
    isSubmitting.value = false
  }
}

async function handleDelete() {
  if (!confirm(t('board.form.deleteConfirm'))) return

  try {
    const { data } = await boardApi.deleteBoard(boardUrl)
    if (data.success) {
      alert(t('board.form.successDelete'))
      router.push('/')
    }
  } catch (err) {
    console.error('Failed to delete board:', err)
    alert(t('board.form.failDelete'))
  }
}

onMounted(fetchBoard)
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div v-if="isLoading" class="text-center py-10">
      <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-600 mx-auto"></div>
    </div>

    <div v-else class="bg-white shadow sm:rounded-lg overflow-hidden">
      <!-- Header -->
      <div class="px-4 py-5 sm:px-6 border-b border-gray-200 flex justify-between items-center">
        <div>
          <h3 class="text-lg leading-6 font-medium text-gray-900">{{ $t('board.form.editTitle') }}</h3>
          <p class="mt-1 max-w-2xl text-sm text-gray-500">{{ $t('board.form.editDesc') }}</p>
        </div>
        <button
          type="button"
          @click="router.back()"
          class="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
        >
          {{ $t('common.back') }}
        </button>
      </div>

      <div class="px-4 py-5 sm:p-6 space-y-6">
          <!-- Board Form -->
          <form @submit.prevent="handleSubmit" class="space-y-6">
            
             <!-- Board Name + Icon Upload Row -->
            <div class="sm:col-span-6 flex items-end gap-4">
               <!-- Image Preview & Input -->
               <div class="shrink-0 relative group">
                  <label for="icon-upload" class="cursor-pointer">
                    <div class="h-16 w-16 rounded-md border border-gray-300 bg-gray-50 flex items-center justify-center overflow-hidden hover:bg-gray-100 relative">
                       <img v-if="previewImage" :src="previewImage" class="h-full w-full object-cover" />
                       <svg v-else class="h-8 w-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                       </svg>
                    </div>
                    <input id="icon-upload" type="file" @change="handleFileChange" accept="image/*" class="hidden" ref="fileInput" />
                  </label>
                  <span class="text-xs text-gray-500 text-center block mt-1">{{ $t('board.form.iconImage') }}</span>
               </div>

               <div class="flex-1">
                   <label for="boardName" class="block text-sm font-medium text-gray-700">{{ $t('board.form.name') }}</label>
                  <div class="mt-1">
                    <input
                      type="text"
                      name="boardName"
                      id="boardName"
                      v-model="form.boardName"
                      required
                      class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md px-3 py-2"
                    />
                  </div>
               </div>
            </div>

            <div>
              <label for="boardUrl" class="block text-sm font-medium text-gray-700">{{ $t('board.form.url') }}</label>
              <div class="mt-1">
                <input
                  type="text"
                  name="boardUrl"
                  id="boardUrl"
                  v-model="form.boardUrl"
                  disabled
                  class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border-gray-300 rounded-md bg-gray-100 text-gray-500 cursor-not-allowed px-3 py-2"
                />
              </div>
            </div>

            <div>
              <label for="description" class="block text-sm font-medium text-gray-700">{{ $t('board.form.description') }}</label>
              <div class="mt-1">
                <textarea
                  id="description"
                  name="description"
                  rows="3"
                  v-model="form.description"
                  class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border border-gray-300 rounded-md px-3 py-2"
                ></textarea>
              </div>
            </div>

            <!-- Sort Order Hidden -->
            <div class="hidden">
              <BaseInput
                :label="$t('board.form.sortOrder')"
                v-model.number="form.sortOrder"
                type="number"
                :placeholder="$t('board.form.placeholder.sortOrder')"
              />
            </div>

            <!-- NSFW Option Hidden -->
            <div class="flex items-start hidden">
              <div class="flex items-center h-5">
                <input
                  id="allowNsfw"
                  name="allowNsfw"
                  type="checkbox"
                  v-model="form.allowNsfw"
                  class="focus:ring-indigo-500 h-4 w-4 text-indigo-600 border-gray-300 rounded"
                />
              </div>
              <div class="ml-3 text-sm">
                <label for="allowNsfw" class="font-medium text-gray-700">{{ $t('board.form.allowNsfw') }}</label>
              </div>
            </div>

            <div class="flex justify-end">
              <button
                type="submit"
                :disabled="isSubmitting"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
              >
                {{ isSubmitting ? $t('board.writePost.updating') : $t('board.form.save') }}
              </button>
            </div>
          </form>

          <hr class="border-gray-200" />

          <!-- Category Manager -->
          <div class="py-6">
            <CategoryManager :boardUrl="boardUrl" />
          </div>

          <hr class="border-gray-200" />

          <!-- Delete Board (Moved to bottom right) -->
          <div class="flex justify-end">
              <button
                type="button"
                @click="handleDelete"
                class="inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500"
              >
                {{ $t('board.form.delete') }}
              </button>
          </div>
      </div>
    </div>
  </div>
</template>