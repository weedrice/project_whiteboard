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
