import { ref, onBeforeUnmount } from 'vue'
import { fileApi } from '@/api/file'

const DEFAULT_MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024
const IMAGE_MIME_PREFIX = 'image/'

function isAbortUploadError(error: unknown): boolean {
    const maybeError = error as { name?: string; code?: string }
    return (
        maybeError?.name === 'AbortError'
        || maybeError?.name === 'CanceledError'
        || maybeError?.code === 'ERR_CANCELED'
    )
}

export function useEditorImageUpload(maxImageSizeBytes = DEFAULT_MAX_IMAGE_SIZE_BYTES) {
    const isUploadingImage = ref(false)
    let uploadAbortController: AbortController | null = null

    const validateImageFile = (file: File): 'type' | 'size' | null => {
        if (!file.type.startsWith(IMAGE_MIME_PREFIX)) return 'type'
        if (file.size > maxImageSizeBytes) return 'size'
        return null
    }

    const uploadImage = async (file: File): Promise<{ url: string; fileId?: number } | null> => {
        if (isUploadingImage.value) return null

        isUploadingImage.value = true
        uploadAbortController = new AbortController()

        try {
            const { data } = await fileApi.uploadFile(file, { signal: uploadAbortController.signal })
            if (!data.success) return null

            return {
                url: data.data.url,
                fileId: data.data.fileId
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
