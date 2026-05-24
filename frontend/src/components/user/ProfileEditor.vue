<template>
  <div class="bg-transparent dark:bg-transparent shadow-none rounded-lg p-0 sm:p-6 sm:bg-white sm:dark:bg-gray-800 sm:shadow transition-colors duration-200">
    <form @submit.prevent="updateProfile" class="space-y-3 sm:space-y-4">
      <div class="flex flex-col sm:flex-row sm:items-stretch gap-3 sm:gap-6">
        <div class="flex flex-col items-center shrink-0 sm:min-h-[88px]">
          <button
            type="button"
            class="shrink-0 border-2 border-gray-200 dark:border-gray-700 rounded-full overflow-hidden h-16 w-16 cursor-pointer focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2"
            :aria-label="$t('user.profile.choosePhoto')"
            @click="fileInputRef?.click()"
          >
            <img
              v-if="profileImageDisplayUrl"
              class="h-full w-full object-contain bg-white dark:bg-gray-700"
              :src="profileImageDisplayUrl"
              alt="Current profile photo"
              @error="profileImageError = true"
            />
            <div
              v-else
              class="h-full w-full rounded-full bg-indigo-100 dark:bg-indigo-900/50 flex items-center justify-center text-indigo-600 dark:text-indigo-400 font-bold text-2xl"
            >
              {{ (form.displayName || authStore.user?.displayName)?.[0] || 'U' }}
            </div>
          </button>
          <button
            type="button"
            class="mt-1.5 sm:mt-auto text-xs text-gray-500 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400"
            @click="fileInputRef?.click()"
          >
            {{ $t('user.profile.choosePhoto') }}
          </button>
          <input
            id="profile-photo-input"
            ref="fileInputRef"
            type="file"
            name="profileImage"
            class="sr-only"
            accept="image/*"
            :aria-label="$t('user.profile.choosePhoto')"
            @change="handleFileChange"
          />
        </div>
        <div class="flex-1 w-full min-w-0">
          <BaseInput
            v-model="form.displayName"
            :label="$t('user.profile.displayName')"
            :error="errors.displayName"
            :placeholder="$t('user.profile.displayNamePlaceholder')"
          />
        </div>
      </div>

    </form>

    <hr class="border-gray-200 dark:border-gray-700 my-4 sm:my-6" />

    <section class="space-y-4">
      <div>
        <h3 class="text-sm font-semibold text-gray-900 dark:text-gray-100">{{ $t('user.profile.agentTitle') }}</h3>
        <p class="mt-1 text-xs text-gray-500 dark:text-gray-400">
          {{ $t('user.profile.agentDescription') }}
        </p>
      </div>

      <p
        v-if="!isEmailVerified && !activeAgent"
        class="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700 dark:border-amber-900/60 dark:bg-amber-950/40 dark:text-amber-300"
      >
        {{ $t('user.profile.agentEmailVerificationRequired') }}
      </p>

      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div class="flex-1">
          <BaseInput
            id="agent-token"
            v-model="agentToken"
            name="agentToken"
            autocomplete="off"
            :label="$t('user.profile.agentPlaceholder')"
            :placeholder="$t('user.profile.agentPlaceholder')"
            :error="agentError"
            :disabled="isClaiming || isAgentListPending || !isEmailVerified || !!activeAgent"
            hide-label
            input-class="h-11 min-h-[44px]"
          />
        </div>
        <BaseButton
          v-if="!activeAgent"
          type="button"
          class="w-full sm:w-auto h-11 min-h-[44px]"
          :loading="isClaiming"
          :disabled="isAgentListPending || !isEmailVerified || !agentToken.trim()"
          @click="handleClaimAgent"
        >
          {{ $t('user.profile.agentRegister') }}
        </BaseButton>
        <BaseButton
          v-else
          type="button"
          variant="secondary"
          class="w-full sm:w-auto h-11 min-h-[44px]"
          :loading="processingAgentId === activeAgent.agentId && processingAction === 'suspend'"
          @click="handleSuspendAgent(activeAgent.agentId)"
        >
          {{ $t('user.profile.agentSuspend') }}
        </BaseButton>
      </div>
    </section>

    <hr class="border-gray-200 dark:border-gray-700 my-4 sm:my-6" />

    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <button
        type="button"
        class="text-left text-xs text-gray-500 underline underline-offset-2 hover:text-red-600 dark:text-gray-400 dark:hover:text-red-400"
        @click="showDeleteModal = true"
      >
        {{ $t('user.settings.deleteAccount') }}
      </button>
      <BaseButton type="button" :loading="loading" class="w-full sm:w-auto h-11 min-h-[44px]" @click="updateProfile">
        {{ loading ? $t('common.saving') : $t('common.save') }}
      </BaseButton>
    </div>
  </div>

  <BaseModal :isOpen="showDeleteModal" :title="$t('user.settings.deleteAccount')" @close="showDeleteModal = false">
    <div class="space-y-4">
      <p class="text-sm text-gray-500">
        {{ $t('user.settings.deleteAccountConfirmation') }}
      </p>

      <div class="bg-red-50 p-4 rounded-md">
        <div class="flex items-center">
          <div class="shrink-0 flex items-center justify-center">
            <svg class="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
              <path
                fill-rule="evenodd"
                d="M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                clip-rule="evenodd"
              />
            </svg>
          </div>
          <div class="ml-3 min-w-0 flex-1">
            <p class="text-[13px] text-red-700 text-left">{{ $t('user.settings.deleteAccountWarning') }}</p>
          </div>
        </div>
      </div>

      <BaseInput
        v-model="deletePassword"
        type="password"
        :label="$t('common.password')"
        :placeholder="$t('auth.placeholders.password')"
        :error="deleteError"
      />
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
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { AxiosError } from 'axios'
import { fileApi } from '@/api/file'
import BaseButton from '@/components/common/ui/BaseButton.vue'
import BaseInput from '@/components/common/ui/BaseInput.vue'
import BaseModal from '@/components/common/ui/BaseModal.vue'
import { useConfirm } from '@/composables/useConfirm'
import { useUser } from '@/composables/useUser'
import { useAuthStore } from '@/stores/auth'
import { useToastStore } from '@/stores/toast'
import type { UserUpdatePayload } from '@/api/user'
import { extractErrorMessage, extractValidationErrors, getFieldError } from '@/utils/errorHandler'
import { getOptimizedProfileImageUrl } from '@/utils/image'
import logger from '@/utils/logger'

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'refreshed'): void
}>()

const authStore = useAuthStore()
const toastStore = useToastStore()
const router = useRouter()
const { t } = useI18n()
const { confirm } = useConfirm()
const { useDeleteAccount, useMyAgents, useClaimAgent, useSuspendMyAgent, useUpdateMyProfile } = useUser()
const { mutateAsync: updateProfileMutateAsync } = useUpdateMyProfile()
const { mutateAsync: deleteAccount, isPending: isDeleting } = useDeleteAccount()
const { data: agentsData, isLoading: isAgentsLoading, isFetching: isAgentsFetching } = useMyAgents()
const { mutateAsync: claimAgent, isPending: isClaiming } = useClaimAgent()
const { mutateAsync: suspendMyAgent } = useSuspendMyAgent()

const loading = ref(false)
const errors = reactive<Record<string, string>>({})
const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const previewImage = ref<string | null>(null)
const profileImageError = ref(false)
const agentToken = ref('')
const agentError = ref('')
const processingAgentId = ref<number | null>(null)
const processingAction = ref<'suspend' | null>(null)

const agents = computed(() => agentsData.value?.agents ?? [])
const activeAgent = computed(() => agents.value.find((agent) => agent.status === 'ACTIVE') ?? null)
const isAgentListPending = computed(() => isAgentsLoading.value || isAgentsFetching.value)
const isEmailVerified = computed(() => {
  const value: unknown = authStore.user?.isEmailVerified
  return value === true || value === 'Y' || value === 'true' || value === 1
})

const profileImageDisplayUrl = computed(() => {
  if (previewImage.value) return previewImage.value
  if (profileImageError.value || !authStore.user?.profileImageUrl) return ''
  return getOptimizedProfileImageUrl(authStore.user.profileImageUrl)
})

const form = reactive({
  displayName: authStore.user?.displayName || '',
})

watch(
  () => authStore.user?.profileImageUrl,
  () => {
    profileImageError.value = false
  }
)

const revokeObjectUrlIfNeeded = (url: string | null | undefined) => {
  if (url && url.startsWith('blob:')) {
    URL.revokeObjectURL(url)
  }
}

watch(previewImage, (_newUrl, oldUrl) => {
  revokeObjectUrlIfNeeded(oldUrl)
})

onUnmounted(() => {
  revokeObjectUrlIfNeeded(previewImage.value)
})

const handleFileChange = async (event: Event) => {
  const target = event.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  if (file.size > 10 * 1024 * 1024) {
    toastStore.addToast(t('common.messages.fileSizeExceeded'), 'warning')
    return
  }

  try {
    const resizedImage = await resizeImage(file, 100, 100)
    selectedFile.value = resizedImage
    previewImage.value = URL.createObjectURL(resizedImage)
  } catch (error) {
    logger.error('Image resize failed', error)
    toastStore.addToast(t('common.messages.processImageFailed'), 'error')
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
      } else if (height > maxHeight) {
        width *= maxHeight / height
        height = maxHeight
      }

      canvas.width = width
      canvas.height = height
      const ctx = canvas.getContext('2d')

      if (!ctx) {
        URL.revokeObjectURL(objectUrl)
        reject(new Error('Could not get canvas context'))
        return
      }

      ctx.drawImage(img, 0, 0, width, height)
      canvas.toBlob((blob) => {
        URL.revokeObjectURL(objectUrl)
        if (blob) {
          resolve(new File([blob], file.name, { type: file.type }))
          return
        }
        reject(new Error('Canvas to Blob failed'))
      }, file.type)
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
    let profileImageId: number | null = null

    if (selectedFile.value) {
      const uploadRes = await fileApi.uploadFile(selectedFile.value)

      if (!uploadRes.data.success || !uploadRes.data.data?.fileId) {
        toastStore.addToast(t('common.messages.uploadFailed'), 'error')
        return
      }

      profileImageId = uploadRes.data.data.fileId
    }

    const payload: UserUpdatePayload = {
      displayName: form.displayName,
      profileImageId,
    }

    await updateProfileMutateAsync(payload)
    await authStore.fetchUser()
    toastStore.addToast(t('common.messages.profileUpdated'), 'success')
    emit('refreshed')
    emit('close')
  } catch (error) {
    const axiosError = error as AxiosError
    logger.error('Failed to update profile:', error)

    const validationErrors = extractValidationErrors(axiosError)
    if (validationErrors) {
      const displayNameError = getFieldError(validationErrors, 'displayName')
      if (displayNameError) {
        errors.displayName = displayNameError
      }

      const otherErrors = Object.entries(validationErrors)
        .filter(([key]) => key !== 'displayName')
        .flatMap(([, messages]) => messages)

      if (otherErrors.length > 0) {
        toastStore.addToast(otherErrors[0], 'error')
      }
    } else {
      const errorMessage = extractErrorMessage(axiosError)
      errors.displayName = errorMessage
      toastStore.addToast(errorMessage, 'error')
    }
  } finally {
    loading.value = false
  }
}

const handleClaimAgent = async () => {
  agentError.value = ''
  if (!isEmailVerified.value) {
    const message = t('user.profile.agentEmailVerificationRequired')
    agentError.value = message
    toastStore.addToast(message, 'warning')
    return
  }

  const token = agentToken.value.trim()
  if (!token) {
    agentError.value = t('user.profile.agentCodeRequired')
    return
  }

  try {
    await claimAgent(token)
    agentToken.value = ''
    toastStore.addToast(t('user.profile.agentClaimSuccess'), 'success')
    emit('refreshed')
  } catch (error: unknown) {
    agentError.value = extractErrorMessage(error as AxiosError) || t('user.profile.agentClaimFailed')
  }
}

const handleSuspendAgent = async (agentId: number) => {
  const isConfirmed = await confirm(
    t('user.profile.agentSuspendConfirmMessage'),
    t('user.profile.agentSuspendConfirmTitle'),
    t('user.profile.agentSuspend'),
    t('common.cancel')
  )

  if (!isConfirmed) {
    return
  }

  processingAgentId.value = agentId
  processingAction.value = 'suspend'
  try {
    await suspendMyAgent(agentId)
    toastStore.addToast(t('user.profile.agentSuspendSuccess'), 'success')
    emit('refreshed')
  } catch (error: unknown) {
    toastStore.addToast(extractErrorMessage(error as AxiosError) || t('user.profile.agentSuspendFailed'), 'error')
  } finally {
    processingAgentId.value = null
    processingAction.value = null
  }
}

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
    const validationErrors = extractValidationErrors(axiosError)

    if (validationErrors) {
      const passwordError = getFieldError(validationErrors, 'password')
      if (passwordError) {
        deleteError.value = passwordError
        return
      }
    }

    const errorMessage = extractErrorMessage(axiosError)
    deleteError.value = errorMessage || t('common.errorOccurred')
  }
}
</script>
