export const ALLOWED_UPLOAD_IMAGE_MIME_TYPES = [
  'image/jpeg',
  'image/jpg',
  'image/png',
  'image/gif',
  'image/webp',
] as const

export const ALLOWED_UPLOAD_IMAGE_EXTENSIONS = [
  '.jpg',
  '.jpeg',
  '.png',
  '.gif',
  '.webp',
] as const

export const ALLOWED_UPLOAD_IMAGE_MIME_TYPE_SET = new Set<string>(ALLOWED_UPLOAD_IMAGE_MIME_TYPES)
export const ALLOWED_UPLOAD_IMAGE_EXTENSION_SET = new Set<string>(ALLOWED_UPLOAD_IMAGE_EXTENSIONS)

export const IMAGE_UPLOAD_ACCEPT = [
  ...ALLOWED_UPLOAD_IMAGE_EXTENSIONS,
  ...ALLOWED_UPLOAD_IMAGE_MIME_TYPES,
].join(',')

export interface ImageUploadPolicy {
  allowedMimeTypes: ReadonlySet<string>
  allowedExtensions: ReadonlySet<string>
  maxSizeBytes: number
  accept: string
  maxWidth?: number
  maxHeight?: number
}

const TEN_MIB = 10 * 1024 * 1024

export const POST_EDITOR_IMAGE_UPLOAD_POLICY: ImageUploadPolicy = {
  allowedMimeTypes: ALLOWED_UPLOAD_IMAGE_MIME_TYPE_SET,
  allowedExtensions: ALLOWED_UPLOAD_IMAGE_EXTENSION_SET,
  maxSizeBytes: TEN_MIB,
  accept: IMAGE_UPLOAD_ACCEPT,
}

export const BOARD_ICON_UPLOAD_POLICY: ImageUploadPolicy = {
  allowedMimeTypes: ALLOWED_UPLOAD_IMAGE_MIME_TYPE_SET,
  allowedExtensions: ALLOWED_UPLOAD_IMAGE_EXTENSION_SET,
  maxSizeBytes: 2 * 1024 * 1024,
  accept: IMAGE_UPLOAD_ACCEPT,
}

export const PROFILE_IMAGE_UPLOAD_POLICY: ImageUploadPolicy = {
  allowedMimeTypes: ALLOWED_UPLOAD_IMAGE_MIME_TYPE_SET,
  allowedExtensions: ALLOWED_UPLOAD_IMAGE_EXTENSION_SET,
  maxSizeBytes: TEN_MIB,
  accept: IMAGE_UPLOAD_ACCEPT,
  maxWidth: 100,
  maxHeight: 100,
}
