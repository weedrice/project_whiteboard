import { ref, onBeforeUnmount } from 'vue'
import { fileApi } from '@/api/file'
import { unwrapApiData } from '@/api/response'
import { validateImageFile as validateGenericImageFile } from '@/utils/imageFile'
import { isCancellationError } from '@/utils/cancellationError'

const DEFAULT_MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
const ALLOWED_IMAGE_MIME_TYPES = new Set([
    'image/jpeg',
    'image/jpg',
    'image/png',
    'image/gif',
    'image/webp',
])
const ALLOWED_IMAGE_EXTENSIONS = new Set(['.jpg', '.jpeg', '.png', '.gif', '.webp'])

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
            allowedMimeTypes: ALLOWED_IMAGE_MIME_TYPES,
            allowedExtensions: ALLOWED_IMAGE_EXTENSIONS,
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

            return {
                url: uploadedFile.url,
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
