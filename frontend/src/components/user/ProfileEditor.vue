<template>
  <div class="bg-white dark:bg-gray-800 shadow rounded-lg p-6 transition-colors duration-200">
    <h3 class="text-lg font-medium leading-6 text-gray-900 dark:text-white mb-4">{{ $t('user.profile.edit') }}</h3>
    <form @submit.prevent="updateProfile" class="space-y-4">
      <!-- Image Upload -->
      <div class="flex items-center space-x-6">
        <div class="shrink-0 border border-gray-200 dark:border-gray-700 rounded-full overflow-hidden h-16 w-16">
          <img class="h-full w-full object-contain bg-white dark:bg-gray-700"
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
    <hr class="border-gray-200 dark:border-gray-700 my-6" />

    <!-- Danger Zone -->
    <div class="flex justify-end">
      <BaseButton variant="danger" size="sm" class="text-xs" @click="showDeleteModal = true">
        {{ $t('user.settings.deleteAccount') }}
      </BaseButton>
    </div>
  </div>

  <!-- Delete Account Modal -->
  <BaseModal :isOpen="showDeleteModal" :title="$t('user.settings.deleteAccount')" @close="showDeleteModal = false">
    <div class="space-y-4">
      <p class="text-sm text-gray-500">
        {{ $t('user.settings.deleteAccountConfirmation') }}
      </p>

      <div class="bg-red-50 p-4 rounded-md">
        <div class="flex">
          <div class="shrink-0">
            <svg class="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd"
                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                clip-rule="evenodd" />
            </svg>
          </div>
          <div class="ml-3">
            <h3 class="text-sm font-medium text-red-800">{{ $t('common.warning') }}</h3>
            <div class="mt-2 text-sm text-red-700">
              <p>{{ $t('user.settings.deleteAccountWarning') }}</p>
            </div>
          </div>
        </div>
      </div>

      <BaseInput v-model="deletePassword" type="password" :label="$t('common.password')"
        :placeholder="$t('auth.placeholders.password')" :error="deleteError" />
    </div>
    <template #footer>
      <div class="flex justify-end space-x-3">
        <BaseButton variant="secondary" @click="showDeleteModal = false">
          {{ $t('common.cancel') }}
        </BaseButton>
        <BaseButton variant="danger" :loading="isDeleting" @click="handleDeleteAccount">
          {{ $t('user.settings.deleteAccount') }}
        </BaseButton>
      </div>
    </template>
  </BaseModal>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseFileInput from '@/components/common/ui/BaseFileInput.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { useUser } from '@/composables/useUser'
import axios from '@/api' // Direct axios for file upload
import logger from '@/utils/logger'
import type { UserUpdatePayload } from '@/api/user'
import { useToastStore } from '@/stores/toast'
import { extractValidationErrors, extractErrorMessage, getFieldError } from '@/utils/errorHandler'
import type { AxiosError } from 'axios'

const authStore = useAuthStore()
const toastStore = useToastStore()
const router = useRouter()
const { t } = useI18n()
const { useUpdateMyProfile, useDeleteAccount } = useUser()
const { mutate: updateProfileMutate, isPending: isUpdating } = useUpdateMyProfile()
const { mutateAsync: deleteAccount, isPending: isDeleting } = useDeleteAccount()

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
      // 이전 preview URL 정리
      if (previewImage.value && previewImage.value.startsWith('blob:')) {
        URL.revokeObjectURL(previewImage.value)
      }
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
    const objectUrl = URL.createObjectURL(file)
    img.src = objectUrl
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
          // Cleanup object URL
          URL.revokeObjectURL(objectUrl)
          if (blob) {
            resolve(new File([blob], file.name, { type: file.type }))
          } else {
            reject(new Error('Canvas to Blob failed'))
          }
        }, file.type)
      } else {
        URL.revokeObjectURL(objectUrl)
        reject(new Error('Could not get canvas context'))
      }
    }
    img.onerror = () => {
      URL.revokeObjectURL(objectUrl)
      reject(new Error('Image load failed'))
    }
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
        const axiosError = error as AxiosError
        logger.error('Failed to update profile:', error)
        
        // Validation 에러 처리
        const validationErrors = extractValidationErrors(axiosError)
        if (validationErrors) {
          // 필드별 에러 설정
          const displayNameError = getFieldError(validationErrors, 'displayName')
          if (displayNameError) {
            errors.displayName = displayNameError
          }
          
          // 다른 필드 에러가 있으면 토스트로 표시
          const otherErrors = Object.entries(validationErrors)
            .filter(([key]) => key !== 'displayName')
            .flatMap(([, messages]) => messages)
          if (otherErrors.length > 0) {
            toastStore.addToast(otherErrors[0], 'error')
          }
        } else {
          // 일반 에러 처리
          const errorMessage = extractErrorMessage(axiosError)
          errors.displayName = errorMessage
          toastStore.addToast(errorMessage, 'error')
        }
        
        loading.value = false
      }
    })

  } catch (error) {
    logger.error('Failed to process profile update:', error)
    loading.value = false
  }
}

// Delete Account Logic
const showDeleteModal = ref(false)
const deletePassword = ref('')
const deleteError = ref('')

const handleDeleteAccount = async () => {
  deleteError.value = ''
  if (!deletePassword.value) {
    deleteError.value = t('auth.passwordRequired')
    return
  }

  try {
    await deleteAccount(deletePassword.value)
    showDeleteModal.value = false
    await authStore.logout()
    router.push('/')
  } catch (error: unknown) {
    logger.error('Failed to delete account:', error)
    const axiosError = error as AxiosError
    
    // Validation 에러 처리 (비밀번호 필드)
    const validationErrors = extractValidationErrors(axiosError)
    if (validationErrors) {
      const passwordError = getFieldError(validationErrors, 'password')
      if (passwordError) {
        deleteError.value = passwordError
        return
      }
    }
    
    // 일반 에러 처리
    const errorMessage = extractErrorMessage(axiosError)

// Cleanup preview image URL
watch(previewImage, (newUrl, oldUrl) => {
  // 이전 URL 정리
  if (oldUrl && oldUrl.startsWith('blob:')) {
    URL.revokeObjectURL(oldUrl)
  }
})

onUnmounted(() => {
  // 컴포넌트 unmount 시 preview URL 정리
  if (previewImage.value && previewImage.value.startsWith('blob:')) {
    URL.revokeObjectURL(previewImage.value)
  }
})
    deleteError.value = errorMessage || t('common.errorOccurred')
  }
}
</script>
