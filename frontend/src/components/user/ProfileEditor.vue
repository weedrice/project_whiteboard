<template>
  <div class="bg-white shadow rounded-lg p-6">
    <h3 class="text-lg font-medium leading-6 text-gray-900 mb-4">{{ $t('user.profile.edit') }}</h3>
    <form @submit.prevent="updateProfile" class="space-y-4">
      <!-- Image Upload -->
      <div class="flex items-center space-x-6">
        <div class="shrink-0 border border-gray-200 rounded-full overflow-hidden h-16 w-16">
          <img class="h-full w-full object-contain bg-white" :src="previewImage || authStore.user?.profileImageUrl || 'https://via.placeholder.com/150'" alt="Current profile photo" />
        </div>
        <label class="block">
          <span class="sr-only">Choose profile photo</span>
          <input type="file" @change="handleFileChange" accept="image/*" class="block w-full text-sm text-slate-500
            file:mr-4 file:py-2 file:px-4
            file:rounded-full file:border-0
            file:text-sm file:font-semibold
            file:bg-violet-50 file:text-violet-700
            hover:file:bg-violet-100
          "/>
        </label>
      </div>

      <BaseInput
        :label="$t('user.profile.displayName')"
        v-model="form.displayName"
        :error="errors.displayName"
        placeholder="Enter your nickname"
      />
      
      <div class="flex justify-end">
        <BaseButton type="submit" :disabled="loading">
          {{ loading ? 'Saving...' : 'Save Changes' }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import { userApi } from '@/api/user'
import axios from '@/api' // Direct axios for file upload

const authStore = useAuthStore()
const loading = ref(false)
const errors = reactive({})
const selectedFile = ref(null)
const previewImage = ref(null)

const form = reactive({
  displayName: authStore.user?.displayName || ''
})

const handleFileChange = async (event) => {
  const file = event.target.files[0]
  if (file) {
    if (file.size > 10 * 1024 * 1024) {
      alert('File size exceeds 10MB limit.')
      return
    }

    try {
      const resizedImage = await resizeImage(file, 100, 100)
      selectedFile.value = resizedImage
      previewImage.value = URL.createObjectURL(resizedImage)
    } catch (e) {
      console.error('Image resize failed', e)
      alert('Failed to process image.')
    }
  }
}

const resizeImage = (file, maxWidth, maxHeight) => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.src = URL.createObjectURL(file)
    img.onload = () => {
      const canvas = document.createElement('canvas')
      let width = img.width
      let height = img.height

      if (width > height) {
        if (width > maxWidth) {
          height *= maxWidth / width
          width = maxWidth
        }
      } else {
        if (height > maxHeight) {
          width *= maxHeight / height
          height = maxHeight
        }
      }

      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')
      ctx.drawImage(img, 0, 0, width, height)

      canvas.toBlob((blob) => {
        resolve(new File([blob], file.name, { type: file.type }))
      }, file.type)
    }
    img.onerror = reject
  })
}

const updateProfile = async () => {
  loading.value = true
  errors.displayName = ''
  
  try {
    let profileImageUrl = authStore.user?.profileImageUrl
    let profileImageId = null

    if (selectedFile.value) {
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      
      const uploadRes = await axios.post('/files/upload', formData, {
        headers: {
          'Content-Type': 'multipart/form-data'
        }
      })
      if (uploadRes.data.success) {
        profileImageUrl = uploadRes.data.data.url
        profileImageId = uploadRes.data.data.fileId
      }
    }

    const { data } = await userApi.updateMyProfile({
      displayName: form.displayName,
      profileImageUrl: profileImageUrl,
      profileImageId: profileImageId
    })
    
    if (data.success) {
      await authStore.fetchUser()
      alert('Profile updated successfully')
    }
  } catch (error) {
    console.error('Failed to update profile:', error)
    if (error.response?.data?.message) {
      errors.displayName = error.response.data.message
    } else {
      alert('Failed to update profile')
    }
  } finally {
    loading.value = false
  }
}
</script>