<template>
  <div class="bg-white shadow rounded-lg p-6">
    <h3 class="text-lg font-medium leading-6 text-gray-900 mb-4">{{ $t('user.profile.edit') }}</h3>
    <form @submit.prevent="updateProfile" class="space-y-4">
      <!-- Image Upload -->
      <div class="flex items-center space-x-6">
        <div class="shrink-0 border border-gray-200 rounded-full overflow-hidden h-16 w-16">
          <img class="h-full w-full object-contain bg-white"
            :src="previewImage || authStore.user?.profileImageUrl || 'https://via.placeholder.com/150'"
            alt="Current profile photo" />
        </div>
        <div class="flex-1">
          <BaseFileInput :label="$t('user.profile.choosePhoto')" accept="image/*" @change="handleFileChange"
            :placeholder="$t('user.profile.choosePhotoPlaceholder')" />
        </div>
      </div>

      <BaseInput :label="$t('user.profile.displayName')" v-model="form.displayName" :error="errors.displayName"
        :placeholder="$t('user.profile.displayNamePlaceholder')" />

      <div class="flex justify-end">
        <BaseButton type="submit" :loading="loading">
          {{ loading ? $t('common.saving') : $t('common.save') }}
        </BaseButton>
      </div>
    </form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/BaseInput.vue'
import BaseButton from '@/components/common/BaseButton.vue'
import BaseFileInput from '@/components/common/BaseFileInput.vue'
import { useUser } from '@/composables/useUser'
import axios from '@/api' // Direct axios for file upload
import logger from '@/utils/logger'
import type { UserUpdatePayload } from '@/api/user'
import { useToastStore } from '@/stores/toast'

const authStore = useAuthStore()
const toastStore = useToastStore()
const { useUpdateMyProfile } = useUser()
const { mutate: updateProfileMutate, isPending: isUpdating } = useUpdateMyProfile()

const loading = ref(false) // Local loading state for image processing + mutation
const errors = reactive<Record<string, string>>({})
const selectedFile = ref<File | null>(null)
const previewImage = ref<string | null>(null)

const form = reactive({
  displayName: authStore.user?.displayName || ''
})

const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (file) {
    if (file.size > 10 * 1024 * 1024) {
      toastStore.addToast('File size exceeds 10MB limit.', 'warning')
      return
    }

    try {
      const resizedImage = await resizeImage(file, 100, 100)
      selectedFile.value = resizedImage
      previewImage.value = URL.createObjectURL(resizedImage)
    } catch (error) {
      logger.error('Image resize failed', error)
      toastStore.addToast('Failed to process image.', 'error')
    }
  }
}

const resizeImage = (file: File, maxWidth: number, maxHeight: number): Promise<File> => {
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
      if (ctx) {
        ctx.drawImage(img, 0, 0, width, height)
        canvas.toBlob((blob) => {
          if (blob) {
            resolve(new File([blob], file.name, { type: file.type }))
          } else {
            reject(new Error('Canvas to Blob failed'))
          }
        }, file.type)
      } else {
        reject(new Error('Could not get canvas context'))
      }
    }
    img.onerror = reject
  })
}

const updateProfile = async () => {
  loading.value = true
  errors.displayName = ''

  try {
    let profileImageUrl = authStore.user?.profileImageUrl
    let profileImageId: number | null = null

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

    const payload: UserUpdatePayload = {
      displayName: form.displayName,
      profileImageUrl: profileImageUrl,
      profileImageId: profileImageId
    }

    updateProfileMutate(payload, {
      onSuccess: async () => {
        await authStore.fetchUser()
        toastStore.addToast('Profile updated successfully', 'success')
        loading.value = false
      },
      onError: (error: Error) => {
        const axiosError = error as Error & { response?: { data?: { message?: string } } }
        logger.error('Failed to update profile:', error)
        if (axiosError.response?.data?.message) {
          errors.displayName = axiosError.response.data.message
        } else {
          toastStore.addToast('Failed to update profile', 'error')
        }
        loading.value = false
      }
    })

  } catch (error) {
    logger.error('Failed to process profile update:', error)
    loading.value = false
  }
}
</script>