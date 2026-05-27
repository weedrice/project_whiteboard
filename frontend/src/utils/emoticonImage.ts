import { cloneBlobWithType, resizeImageToMaxDimensionBlob, revokeBlobUrlIfNeeded } from '@/utils/imageFile'

export const MAX_EMOTICON_GIF_SIZE_BYTES = 1024 * 1024
export const MAX_EMOTICON_IMAGE_DIMENSION = 500
export const EMOTICON_UPLOAD_MAX_DIMENSION = 100
export const SUPPORTED_EMOTICON_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
export const SUPPORTED_EMOTICON_IMAGE_ACCEPT = 'image/jpeg,image/png,image/gif,image/webp'

export interface EmoticonImagePreview {
  clientId: string
  file: File
  preview: string
  width: number
  height: number
}

export type EmoticonImageValidationError = 'imageOnly' | 'notImage' | 'gifSizeExceeded'
export type EmoticonImagePreviewResult =
  | { ok: true; item: EmoticonImagePreview }
  | { ok: false; reason: 'sizeExceeded'; width: number; height: number }
  | { ok: false; reason: 'loadFailed' }

export function isSupportedEmoticonImageType(file: File): boolean {
  return SUPPORTED_EMOTICON_IMAGE_TYPES.has(file.type)
}

export function validateEmoticonImageFile(
  file: File,
  options: { nonImageReason?: Extract<EmoticonImageValidationError, 'imageOnly' | 'notImage'> } = {}
): EmoticonImageValidationError | null {
  if (!file.type.startsWith('image/')) {
    return options.nonImageReason ?? 'notImage'
  }

  if (!isSupportedEmoticonImageType(file)) {
    return 'notImage'
  }

  if (file.type === 'image/gif' && file.size > MAX_EMOTICON_GIF_SIZE_BYTES) {
    return 'gifSizeExceeded'
  }

  return null
}

export function revokeEmoticonPreviewUrl(url: string | null | undefined): void {
  revokeBlobUrlIfNeeded(url)
}

let emoticonImagePreviewSequence = 0

function createEmoticonImagePreviewId(): string {
  emoticonImagePreviewSequence += 1
  return `emoticon-preview-${emoticonImagePreviewSequence}`
}

export function createEmoticonImagePreview(file: File): Promise<EmoticonImagePreviewResult> {
  return new Promise((resolve) => {
    const img = new Image()
    const preview = URL.createObjectURL(file)

    img.onload = () => {
      if (img.width > MAX_EMOTICON_IMAGE_DIMENSION || img.height > MAX_EMOTICON_IMAGE_DIMENSION) {
        revokeEmoticonPreviewUrl(preview)
        resolve({ ok: false, reason: 'sizeExceeded', width: img.width, height: img.height })
        return
      }

      resolve({
        ok: true,
        item: {
          clientId: createEmoticonImagePreviewId(),
          file,
          preview,
          width: img.width,
          height: img.height
        }
      })
    }

    img.onerror = () => {
      revokeEmoticonPreviewUrl(preview)
      resolve({ ok: false, reason: 'loadFailed' })
    }

    img.src = preview
  })
}

export function resizeEmoticonImage(file: File, maxSize: number = EMOTICON_UPLOAD_MAX_DIMENSION): Promise<Blob> {
  if (file.type === 'image/gif') {
    return cloneBlobWithType(file, 'image/gif')
  }
  return resizeImageToMaxDimensionBlob(file, maxSize)
}

export async function createUploadableEmoticonImageFile(
  item: EmoticonImagePreview,
  maxSize: number = EMOTICON_UPLOAD_MAX_DIMENSION
): Promise<File> {
  const needsResize = item.file.type !== 'image/gif' && (item.width > maxSize || item.height > maxSize)
  const fileToUpload: File | Blob = needsResize
    ? await resizeEmoticonImage(item.file, maxSize)
    : item.file

  if (fileToUpload instanceof File) {
    return fileToUpload
  }

  return new File([fileToUpload], item.file.name, {
    type: fileToUpload.type || item.file.type || 'image/png'
  })
}

export async function uploadEmoticonImagePreviews<T>(
  items: EmoticonImagePreview[],
  uploadFile: (file: File, item: EmoticonImagePreview, index: number) => Promise<T>,
  onProgress?: (completed: number, total: number) => void
): Promise<T[]> {
  let completed = 0
  const total = items.length

  return Promise.all(items.map(async (item, index) => {
    const file = await createUploadableEmoticonImageFile(item)
    const result = await uploadFile(file, item, index)
    completed += 1
    onProgress?.(completed, total)
    return result
  }))
}

export function resolveEmoticonTagAddition(
  rawTag: string,
  currentTags: string[],
  maxTags: number = 10
): { tag: string | null; error: 'maxTags' | null } {
  const tag = rawTag.trim().replace(/^#/, '')

  if (!tag || currentTags.includes(tag)) {
    return { tag: null, error: null }
  }

  if (currentTags.length >= maxTags) {
    return { tag: null, error: 'maxTags' }
  }

  return { tag, error: null }
}
