import { ref, onBeforeUnmount, watch, type Ref } from 'vue'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import { POST_EDITOR_IMAGE_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'
import { isCancellationError } from '@/utils/cancellationError'
import logger from '@/utils/logger'

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
    const isUploadingImage = ref(false)
    let uploadAbortController: AbortController | null = null

    const validateImageFile = (file: File): 'type' | 'size' | null => {
        return validateGenericImageFile(file, {
            allowedMimeTypes: POST_EDITOR_IMAGE_UPLOAD_POLICY.allowedMimeTypes,
            allowedExtensions: POST_EDITOR_IMAGE_UPLOAD_POLICY.allowedExtensions,
            maxSizeBytes: maxImageSizeBytes,
        })
    }

    const uploadImage = async (file: File): Promise<{ url: string; fileId?: number } | null> => {
        if (isUploadingImage.value) return null

        isUploadingImage.value = true
        const controller = new AbortController()
        const startingIdentity = ownerIdentity?.value
        uploadAbortController = controller

        try {
            const { data } = await fileApi.uploadFile(file, { signal: controller.signal })
            if (!data.success) return null
            const uploadedFile = unwrapApiData(data)
            const imageUrl = resolveFileUploadUrl(uploadedFile)
            const isStale = controller.signal.aborted || ownerIdentity?.value !== startingIdentity
            if (isStale || !imageUrl) {
                if (typeof uploadedFile.fileId === 'number') {
                    try {
                        await fileApi.discardUploads([uploadedFile.fileId], { skipGlobalErrorHandler: true })
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
            if (uploadAbortController === controller) uploadAbortController = null
            isUploadingImage.value = false
        }
    }

    const abortImageUpload = () => {
        if (uploadAbortController) {
            uploadAbortController.abort()
            uploadAbortController = null
        }
    }

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
