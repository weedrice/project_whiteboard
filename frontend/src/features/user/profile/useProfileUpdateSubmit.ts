import { getCurrentScope, onScopeDispose, reactive, ref, type Ref } from 'vue'
import type { AxiosError } from 'axios'
import { fileApi } from '@/api/file'
import { FILE_UPLOAD_TARGETS } from '@/api/fileUploadTargets'
import { unwrapAxiosApiData } from '@/api/response'
import type { UserUpdatePayload } from '@/api/user'
import {
  extractErrorMessage,
  extractValidationErrors,
  getFieldError,
  isConcurrentModificationError,
} from '@/utils/errorHandler'
import logger from '@/utils/logger'
import {
  captureAuthSessionIntent,
  throwIfAuthSessionIntentChanged,
  type AuthSessionIntentSource,
} from '@/utils/authSessionIntent'
import { subscribeAuthSessionBoundary } from '@/queryAuthScope'
import { isCancellationError } from '@/utils/cancellationError'

interface UseProfileUpdateSubmitOptions {
  selectedFile: Ref<File | null>
  removeProfileImage?: Ref<boolean>
  getDisplayName: () => string
  updateProfile: (payload: UserUpdatePayload) => Promise<unknown>
  refreshUser: () => Promise<unknown>
  addToast: (message: string, type: 'success' | 'error' | 'warning') => void
  t: (key: string, params?: Record<string, unknown>) => string
  confirm?: (message: string) => Promise<boolean>
  getProfileImageChangeCost?: () => number
  isProfileImageChangeFree?: () => boolean
  getCurrentPoints?: () => number
  onRefreshed: () => void
  onClose: () => void
  authSession: AuthSessionIntentSource
}

export function useProfileUpdateSubmit(options: UseProfileUpdateSubmitOptions) {
  const loading = ref(false)
  const errors = reactive<Record<string, string>>({})
  let operationRevision = 0
  let activeController: AbortController | null = null

  if (getCurrentScope()) {
    const stopSessionBoundary = subscribeAuthSessionBoundary(() => activeController?.abort())
    onScopeDispose(() => {
      activeController?.abort()
      stopSessionBoundary()
    })
  }

  const updateProfile = async () => {
    activeController?.abort()
    const controller = new AbortController()
    activeController = controller
    const operation = ++operationRevision
    const intent = captureAuthSessionIntent(options.authSession)
    const assertCurrent = () => throwIfAuthSessionIntentChanged(
      options.authSession,
      intent,
      controller.signal,
    )
    loading.value = true
    errors.displayName = ''
    errors.profileImage = ''

    let profileImageId: number | null = null
    let profileUpdated = false
    try {
      assertCurrent()

      if (options.selectedFile.value) {
        if (!await confirmPaidProfileImageChangeIfNeeded()) {
          return
        }
        assertCurrent()

        const uploadRes = await fileApi.uploadFile(
          options.selectedFile.value,
          { signal: controller.signal },
          FILE_UPLOAD_TARGETS.PROFILE_IMAGE,
        )
        const uploadedFile = unwrapAxiosApiData(uploadRes)
        if (uploadRes.data.success && uploadedFile?.fileId) {
          profileImageId = uploadedFile.fileId
        }
        assertCurrent()

        if (!uploadRes.data.success || !uploadedFile?.fileId) {
          options.addToast(options.t('common.messages.uploadFailed'), 'error')
          return
        }

      }

      const payload: UserUpdatePayload = {
        displayName: options.getDisplayName().trim(),
        profileImageId,
      }
      if (options.removeProfileImage?.value) {
        payload.removeProfileImage = true
      }

      const response = await options.updateProfile(payload)
      profileUpdated = true
      assertCurrent()
      await options.refreshUser()
      assertCurrent()
      const spentPoints = extractSpentPoints(response)
      if (spentPoints && spentPoints > 0) {
        options.addToast(options.t('user.profile.profileImageCostSpent', { points: spentPoints }), 'success')
      } else {
        options.addToast(options.t('common.messages.profileUpdated'), 'success')
      }
      options.onRefreshed()
      options.onClose()
    } catch (error) {
      if (profileImageId && !profileUpdated) {
        await discardUploadedProfileImage(profileImageId)
      }
      if (controller.signal.aborted || isCancellationError(error)) return
      if (isConcurrentModificationError(error)) {
        try {
          await options.refreshUser()
          assertCurrent()
        } catch (refreshError: unknown) {
          if (controller.signal.aborted || isCancellationError(refreshError)) return
          logger.error('Failed to refresh profile after concurrent modification:', refreshError)
          options.addToast(extractErrorMessage(refreshError), 'error')
          return
        }
        options.addToast(options.t('common.messages.concurrentModification'), 'warning')
        return
      }
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
          options.addToast(otherErrors[0], 'error')
        }
      } else {
        const errorMessage = extractErrorMessage(axiosError)
        errors.displayName = errorMessage
        options.addToast(errorMessage, 'error')
      }
    } finally {
      if (activeController === controller) activeController = null
      if (operationRevision === operation) loading.value = false
    }
  }

  const discardUploadedProfileImage = async (fileId: number) => {
    try {
      await fileApi.discardUploads([fileId], {
        skipAuthRefresh: true,
        skipGlobalErrorHandler: true,
      })
    } catch (error: unknown) {
      logger.warn('Failed to discard stale profile upload:', error)
    }
  }

  const confirmPaidProfileImageChangeIfNeeded = async () => {
    if (options.isProfileImageChangeFree?.()) {
      return true
    }

    const cost = options.getProfileImageChangeCost?.() ?? 0
    const currentPoints = options.getCurrentPoints?.() ?? 0
    if (cost > 0 && currentPoints < cost) {
      errors.profileImage = options.t('user.profile.profileImageInsufficientPoints', {
        current: currentPoints,
        cost,
      })
      options.addToast(errors.profileImage, 'error')
      return false
    }

    if (cost > 0 && options.confirm) {
      return options.confirm(options.t('user.profile.profileImageCostConfirm', { cost }))
    }

    return true
  }

  const extractSpentPoints = (response: unknown): number | null => {
    const apiData = response as { data?: { spentPoints?: unknown }, spentPoints?: unknown }
    const value = apiData?.data?.spentPoints ?? apiData?.spentPoints
    return typeof value === 'number' ? value : null
  }

  return {
    loading,
    errors,
    updateProfile,
  }
}
