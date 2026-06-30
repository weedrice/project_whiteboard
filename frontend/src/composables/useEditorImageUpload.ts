import { ref, onBeforeUnmount } from 'vue'
import { fileApi, resolveFileUploadUrl } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import {
    ALLOWED_UPLOAD_IMAGE_EXTENSION_SET,
    ALLOWED_UPLOAD_IMAGE_MIME_TYPE_SET,
} from '@/utils/imageUploadPolicy'
import { isCancellationError } from '@/utils/cancellationError'

const DEFAULT_MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024

function isAbortUploadError(error: unknown): boolean {
    return isCancellationError(error, {
        names: ['AbortError', 'CanceledError'],
        codes: ['ERR_CANCELED'],
    })
}

export function useEditorImageUpload(maxImageSizeBytes = DEFAULT_MAX_IMAGE_SIZE_BYTES) {
    const isUploadingImage = ref(false)
    let uploadAbortController: AbortController | null = null

    const validateImageFile = (file: File): 'type' | 'size' | null => {
        return validateGenericImageFile(file, {
            allowedMimeTypes: ALLOWED_UPLOAD_IMAGE_MIME_TYPE_SET,
            allowedExtensions: ALLOWED_UPLOAD_IMAGE_EXTENSION_SET,
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
