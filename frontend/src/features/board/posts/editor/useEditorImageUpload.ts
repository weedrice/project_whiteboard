import { ref, onBeforeUnmount, watch, type Ref } from 'vue'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { FILE_UPLOAD_TARGETS } from '@/api/fileUploadTargets'
import { unwrapApiData } from '@/api/response'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import { POST_EDITOR_IMAGE_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'
import { isCancellationError } from '@/utils/cancellationError'
import logger from '@/utils/logger'
import type { AxiosRequestConfig } from 'axios'
import { useAuthStore } from '@/stores/auth'
import { createAuthSessionScope } from '@/utils/authSessionScope'
import { throwIfAuthSessionIntentChanged } from '@/utils/authSessionIntent'
import { useAuthSessionReset } from '@/composables/useAuthSessionReset'

function isAbortUploadError(error: unknown): boolean {
    return isCancellationError(error, {
        names: ['AbortError', 'CanceledError'],
        codes: ['ERR_CANCELED'],
    })
}

export function useEditorImageUpload(
    maxImageSizeBytes = POST_EDITOR_IMAGE_UPLOAD_POLICY.maxSizeBytes,
    ownerIdentity?: Readonly<Ref<string | undefined>>,
) {
    const authStore = useAuthStore()
    const isUploadingImage = ref(false)
    let uploadAbortController: AbortController | null = null

    const validateImageFile = (file: File): 'type' | 'size' | null => {
        return validateGenericImageFile(file, {
            allowedMimeTypes: POST_EDITOR_IMAGE_UPLOAD_POLICY.allowedMimeTypes,
            allowedExtensions: POST_EDITOR_IMAGE_UPLOAD_POLICY.allowedExtensions,
            maxSizeBytes: maxImageSizeBytes,
        })
    }

    const uploadImage = async (file: File, config?: AxiosRequestConfig): Promise<{ url: string; fileId?: number } | null> => {
        if (isUploadingImage.value) return null

        isUploadingImage.value = true
        const controller = new AbortController()
        const scope = createAuthSessionScope(authStore)
        const startingIdentity = ownerIdentity?.value
        uploadAbortController = controller

        try {
            const requestConfig = scope.withRequestConfig(config)
            const expectedSession = config?.expectedSession ?? scope.intent
            throwIfAuthSessionIntentChanged(authStore, expectedSession, requestConfig.signal)
            const { data } = await fileApi.uploadFile(file, {
                ...requestConfig,
                expectedSession,
                signal: AbortSignal.any([controller.signal, requestConfig.signal]),
            }, FILE_UPLOAD_TARGETS.POST_CONTENT)
            if (!data.success) return null
            const uploadedFile = unwrapApiData(data)
            const imageUrl = resolveFileUploadUrl(uploadedFile)
            const isStale = !scope.isCurrent() || controller.signal.aborted || ownerIdentity?.value !== startingIdentity
            if (isStale || !imageUrl) {
                if (scope.isCurrent() && typeof uploadedFile.fileId === 'number') {
                    try {
                        await fileApi.discardUploads([uploadedFile.fileId], { expectedSession: scope.intent, skipGlobalErrorHandler: true })
                    } catch (error) {
                        logger.warn('Failed to discard stale post editor upload:', error)
                    }
                }
                return null
            }

            return {
                url: imageUrl,
                fileId: uploadedFile.fileId
            }
        } finally {
            scope.dispose()
            if (uploadAbortController === controller) {
                uploadAbortController = null
                isUploadingImage.value = false
            }
        }
    }

    const abortImageUpload = () => {
        if (uploadAbortController) {
            uploadAbortController.abort()
            uploadAbortController = null
        }
        isUploadingImage.value = false
    }

    useAuthSessionReset(abortImageUpload)

    const stopOwnerIdentityWatch = ownerIdentity
        ? watch(ownerIdentity, abortImageUpload, { flush: 'sync' })
        : null

    onBeforeUnmount(() => {
        stopOwnerIdentityWatch?.()
        abortImageUpload()
    })

    return {
        isUploadingImage,
        validateImageFile,
        uploadImage,
        abortImageUpload,
        isAbortUploadError
    }
}
