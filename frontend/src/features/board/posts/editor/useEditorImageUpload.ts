import { ref, onBeforeUnmount } from 'vue'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import { POST_EDITOR_IMAGE_UPLOAD_POLICY } from '@/utils/imageUploadPolicy'
import { isCancellationError } from '@/utils/cancellationError'

function isAbortUploadError(error: unknown): boolean {
    return isCancellationError(error, {
        names: ['AbortError', 'CanceledError'],
        codes: ['ERR_CANCELED'],
    })
}

export function useEditorImageUpload(maxImageSizeBytes = POST_EDITOR_IMAGE_UPLOAD_POLICY.maxSizeBytes) {
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
        uploadAbortController = new AbortController()

        try {
            const { data } = await fileApi.uploadFile(file, { signal: uploadAbortController.signal })
            if (!data.success) return null
            const uploadedFile = unwrapApiData(data)
            const imageUrl = resolveFileUploadUrl(uploadedFile)
            if (!imageUrl) return null

            return {
                url: imageUrl,
                fileId: uploadedFile.fileId
            }
        } finally {
            uploadAbortController = null
            isUploadingImage.value = false
        }
    }

    const abortImageUpload = () => {
        if (uploadAbortController) {
            uploadAbortController.abort()
            uploadAbortController = null
        }
    }

    onBeforeUnmount(() => {
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
