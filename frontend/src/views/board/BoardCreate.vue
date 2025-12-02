<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { boardApi } from '@/api/board'
import BaseInput from '@/components/common/BaseInput.vue'
import { useI18n } from 'vue-i18n'
import axios from '@/api'

const { t } = useI18n()

const router = useRouter()

const form = ref({
  boardName: '',
  boardUrl: '',
  description: '',
  iconUrl: '',
  sortOrder: 0,
  allowNsfw: false
})

const isSubmitting = ref(false)
const error = ref('')
const selectedFile = ref(null)
const previewImage = ref(null)

const handleFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    selectedFile.value = file
    previewImage.value = URL.createObjectURL(file)
  }
}

async function handleSubmit() {
  if (!form.value.boardName) {
    alert(t('board.form.validation'))
    return
  }
  if (!form.value.boardUrl) {
    alert(t('board.form.validation'))
    return
  }

  isSubmitting.value = true
  error.value = ''

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
    
    const { data } = await boardApi.createBoard(payload)
    if (data.success) {
      router.push(`/board/${data.data.boardUrl}`)
    }
  } catch (err) {
    console.error('Failed to create board:', err)
    error.value = err.response?.data?.error?.message || t('board.form.failCreate')
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto">
    <div class="md:flex md:items-center md:justify-between mb-6">
      <div class="flex-1 min-w-0">
        <h2 class="text-2xl font-bold leading-7 text-gray-900 sm:text-3xl sm:truncate">
          {{ $t('board.form.createTitle') }}
        </h2>
      </div>
    </div>

    <form @submit.prevent="handleSubmit" class="space-y-6 bg-white shadow px-4 py-5 sm:rounded-lg sm:p-6">
      <div v-if="error" class="rounded-md bg-red-50 p-4">
        <div class="flex">
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ error }}</h3>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 gap-y-6 gap-x-4 sm:grid-cols-6">
        
        <!-- Board Name + Icon Upload Row -->
        <div class="sm:col-span-6 flex items-end gap-4">
           <!-- Image Preview & Input -->
           <div class="shrink-0 relative group">
              <label for="icon-upload" class="cursor-pointer">
                <div class="h-16 w-16 rounded-md border border-gray-300 bg-gray-50 flex items-center justify-center overflow-hidden hover:bg-gray-100">
                   <img v-if="previewImage" :src="previewImage" class="h-full w-full object-cover" />
                   <svg v-else class="h-8 w-8 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                   </svg>
                </div>
                <input id="icon-upload" type="file" @change="handleFileChange" accept="image/*" class="hidden" />
              </label>
              <span class="text-xs text-gray-500 text-center block mt-1">{{ $t('board.form.iconImage') }}</span>
           </div>

           <div class="flex-1">
              <BaseInput
                :label="$t('board.form.name')"
                v-model="form.boardName"
                type="text"
                required
                :placeholder="$t('board.form.placeholder.name')"
              />
           </div>
        </div>

        <div class="sm:col-span-6">
          <BaseInput
            :label="$t('board.form.url')"
            v-model="form.boardUrl"
            type="text"
            required
            :placeholder="$t('board.form.placeholder.url')"
          />
        </div>

        <div class="sm:col-span-6">
          <label for="description" class="block text-sm font-medium text-gray-700">{{ $t('board.form.description') }}</label>
          <div class="mt-1">
            <textarea
              id="description"
              name="description"
              rows="3"
              v-model="form.description"
              class="shadow-sm focus:ring-indigo-500 focus:border-indigo-500 block w-full sm:text-sm border border-gray-300 rounded-md p-2"
              :placeholder="$t('board.form.placeholder.desc')"
            ></textarea>
          </div>
        </div>
      </div>

      <div class="flex justify-end">
        <button
          type="button"
          @click="router.back()"
          class="bg-white py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
        >
          {{ $t('common.cancel') }}
        </button>
        <button
          type="submit"
          :disabled="isSubmitting"
          class="ml-3 inline-flex justify-center py-2 px-4 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-50"
        >
          {{ isSubmitting ? $t('common.loading') : $t('board.form.create') }}
        </button>
      </div>
    </form>
  </div>
</template>